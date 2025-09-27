package com.spud.rpic.io.netty.client;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import com.spud.rpic.common.domain.RpcRequest;
import com.spud.rpic.common.domain.RpcResponse;
import com.spud.rpic.common.exception.RpcException;
import com.spud.rpic.common.exception.TimeoutException;
import com.spud.rpic.io.common.ProtocolMsg;
import com.spud.rpic.io.netty.NetClient;
import com.spud.rpic.metrics.RpcMetricsRecorder;
import com.spud.rpic.model.ServiceURL;

import io.micrometer.core.instrument.Timer;
import io.netty.channel.Channel;
import io.netty.util.concurrent.Promise;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Spud
 * @date 2025/2/9
 */
@Slf4j
public class NettyNetClient implements NetClient, InitializingBean, DisposableBean {

	private final ConnectionPool connectionPool;
	private final RpcClientHandler clientHandler;
	private final RpcMetricsRecorder metricsRecorder;
	private final ConcurrentMap<String, PendingClientMetric> pendingMetrics = new ConcurrentHashMap<>();

	public NettyNetClient(ConnectionPool connectionPool, RpcClientHandler clientHandler,
	                      RpcMetricsRecorder metricsRecorder) {
		this.connectionPool = connectionPool;
		this.clientHandler = clientHandler;
		this.metricsRecorder = metricsRecorder;
	}

	@Override
	public RpcResponse send(ServiceURL serviceURL, RpcRequest request, int timeout) throws Exception {
		log.debug("Sending request to {}, request: {}", serviceURL, request.getRequestId());

		if (serviceURL == null || serviceURL.getHost() == null || serviceURL.getHost().isEmpty()) {
			throw new RpcException("Invalid service URL: " + serviceURL);
		}

		final Timer.Sample sample = metricsRecorder.startClientSample();
		final String requestId = request.getRequestId();
		final String endpoint = endpointOf(serviceURL);
		final String serviceKey = request.getServiceKey();
		final String methodName = request.getMethodName();

		Channel channel = null;
		Promise<RpcResponse> promise = null;

		try {
			channel = connectionPool.acquireChannel(serviceURL);
			if (!channel.isActive()) {
				connectionPool.releaseChannel(serviceURL, channel);
				RpcException error = new RpcException("Channel is not active");
				metricsRecorder.recordClient(sample, serviceKey, methodName, endpoint, false, error, 0, -1);
				throw error;
			}

			promise = channel.eventLoop().newPromise();
			final Promise<RpcResponse> requestPromise = promise;
			clientHandler.addPromise(requestId, requestPromise, channel.eventLoop(), timeout);

			byte[] requestBytes = clientHandler.getSerializer().serialize(request);
			byte serializerCode = clientHandler.getSerializer().getCode();
			PendingClientMetric metric = new PendingClientMetric(sample, serviceKey, methodName, endpoint,
				requestBytes.length);
			pendingMetrics.put(requestId, metric);

			requestPromise.addListener(promiseFuture -> {
				PendingClientMetric removed = pendingMetrics.remove(requestId);
				try {
					if (removed != null) {
						RpcResponse response = promiseFuture.isSuccess() ? (RpcResponse) promiseFuture.getNow() : null;
						Throwable cause = promiseFuture.isSuccess() ? null : promiseFuture.cause();
						boolean success = promiseFuture.isSuccess() && response != null
							&& !Boolean.TRUE.equals(response.getError());
						if (promiseFuture.isSuccess() && response != null && Boolean.TRUE.equals(response.getError())) {
							cause = new RpcException(response.getErrorMsg());
							success = false;
						}
						int responseBytes = clientHandler.pollResponseSize(requestId);
						metricsRecorder.recordClient(removed.sample, removed.serviceKey, removed.methodName,
							removed.endpoint, success, cause, removed.requestBytes, responseBytes);
					}
				} finally {
					clientHandler.removePromise(requestId);
				}
			});

			ProtocolMsg protocolMsg = ProtocolMsg.fromBytes(requestBytes, serializerCode);

			channel.writeAndFlush(protocolMsg).addListener(writeFuture -> {
				if (!writeFuture.isSuccess()) {
					log.error("Failed to send request: {}", requestId, writeFuture.cause());
					requestPromise.tryFailure(writeFuture.cause());
				} else {
					log.debug("Request sent successfully: {}", requestId);
				}
			});

			return requestPromise.get(timeout, TimeUnit.MILLISECONDS);
		} catch (java.util.concurrent.TimeoutException e) {
			PendingClientMetric removed = pendingMetrics.remove(requestId);
			if (removed != null) {
				metricsRecorder.recordClient(removed.sample, removed.serviceKey, removed.methodName,
					removed.endpoint, false, e, removed.requestBytes, -1);
			} else {
				metricsRecorder.recordClient(sample, serviceKey, methodName, endpoint, false, e, 0, -1);
			}
			throw new TimeoutException("Request timeout after " + timeout + "ms", e);
		} finally {
			if (promise != null && !promise.isDone()) {
				clientHandler.removePromise(requestId);
			}
			if (channel != null) {
				connectionPool.releaseChannel(serviceURL, channel);
			}
		}
	}

	@Override
	public CompletableFuture<RpcResponse> sendAsync(ServiceURL serviceUrl, RpcRequest request, int timeout) {
		CompletableFuture<RpcResponse> future = new CompletableFuture<>();
		final Timer.Sample sample = metricsRecorder.startClientSample();

		final String requestId = request.getRequestId();
		final String endpoint = endpointOf(serviceUrl);
		final String serviceKey = request.getServiceKey();
		final String methodName = request.getMethodName();

		try {
			if (serviceUrl == null || serviceUrl.getHost() == null || serviceUrl.getHost().isEmpty()) {
				throw new RpcException("Invalid service URL: " + serviceUrl);
			}

			connectionPool.acquireChannelAsync(serviceUrl).thenAccept(channel -> {
				if (!channel.isActive()) {
					RpcException error = new RpcException("Channel is not active");
					metricsRecorder.recordClient(sample, serviceKey, methodName, endpoint, false, error, 0, -1);
					future.completeExceptionally(error);
					connectionPool.releaseChannel(serviceUrl, channel);
					return;
				}

				try {
					Promise<RpcResponse> promise = channel.eventLoop().newPromise();
					clientHandler.addPromise(requestId, promise, channel.eventLoop(), timeout);

					byte[] requestBytes = clientHandler.getSerializer().serialize(request);
					byte serializerCode = clientHandler.getSerializer().getCode();
					PendingClientMetric metric = new PendingClientMetric(sample, serviceKey, methodName, endpoint,
						requestBytes.length);
					pendingMetrics.put(requestId, metric);

					promise.addListener(promiseFuture -> {
						PendingClientMetric removed = pendingMetrics.remove(requestId);
						try {
							if (removed != null) {
								RpcResponse response = promiseFuture.isSuccess() ? (RpcResponse) promiseFuture.getNow() : null;
								Throwable cause = promiseFuture.isSuccess() ? null : promiseFuture.cause();
								boolean success = promiseFuture.isSuccess() && response != null
									&& !Boolean.TRUE.equals(response.getError());
								if (promiseFuture.isSuccess() && response != null && Boolean.TRUE.equals(response.getError())) {
									cause = new RpcException(response.getErrorMsg());
									success = false;
								}
								int responseBytes = clientHandler.pollResponseSize(requestId);
								metricsRecorder.recordClient(removed.sample, removed.serviceKey, removed.methodName,
									removed.endpoint, success, cause, removed.requestBytes, responseBytes);
							}

							if (promiseFuture.isSuccess()) {
								future.complete((RpcResponse) promiseFuture.getNow());
							} else {
								future.completeExceptionally(promiseFuture.cause());
							}
						} finally {
							clientHandler.removePromise(requestId);
							connectionPool.releaseChannel(serviceUrl, channel);
						}
					});

					ProtocolMsg protocolMsg = ProtocolMsg.fromBytes(requestBytes, serializerCode);

					channel.writeAndFlush(protocolMsg).addListener(writeFuture -> {
						if (!writeFuture.isSuccess()) {
							log.error("Failed to send async request: {}", requestId, writeFuture.cause());
							promise.tryFailure(writeFuture.cause());
						} else {
							log.debug("Async request sent successfully: {}", requestId);
						}
					});
				} catch (Exception e) {
					log.error("Error processing async request: {}", requestId, e);
					clientHandler.removePromise(requestId);
					PendingClientMetric removed = pendingMetrics.remove(requestId);
					if (removed != null) {
						metricsRecorder.recordClient(removed.sample, removed.serviceKey, removed.methodName,
							removed.endpoint, false, e, removed.requestBytes, -1);
					} else {
						metricsRecorder.recordClient(sample, serviceKey, methodName, endpoint, false, e, 0, -1);
					}
					future.completeExceptionally(e);
					connectionPool.releaseChannel(serviceUrl, channel);
				}
			}).exceptionally(e -> {
				log.error("Failed to acquire channel for async request: {}", requestId, e);
				RpcException error = new RpcException("Failed to acquire channel", e);
				metricsRecorder.recordClient(sample, serviceKey, methodName, endpoint, false, error, 0, -1);
				future.completeExceptionally(error);
				return null;
			});
		} catch (Exception e) {
			log.error("Failed to send async request: {}", requestId, e);
			metricsRecorder.recordClient(sample, serviceKey, methodName, endpoint, false, e, 0, -1);
			future.completeExceptionally(e);
		}

		return future;
	}

	@Override
	public void close() {
		connectionPool.close();
	}

	@Override
	public void destroy() {
		this.close();
		clientHandler.close();
	}

	@Override
	public void afterPropertiesSet() {
		// 初始化逻辑（如果需要）
	}

	private String endpointOf(ServiceURL serviceURL) {
		return serviceURL.getHost() + ":" + serviceURL.getPort();
	}

	private static final class PendingClientMetric {
		final Timer.Sample sample;
		final String serviceKey;
		final String methodName;
		final String endpoint;
		final long requestBytes;

		PendingClientMetric(Timer.Sample sample, String serviceKey, String methodName, String endpoint, long requestBytes) {
			this.sample = sample;
			this.serviceKey = serviceKey != null ? serviceKey : "unknown";
			this.methodName = methodName != null ? methodName : "unknown";
			this.endpoint = endpoint != null ? endpoint : "unknown";
			this.requestBytes = requestBytes;
		}
	}
}