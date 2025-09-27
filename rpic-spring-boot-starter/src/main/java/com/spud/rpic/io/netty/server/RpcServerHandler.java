package com.spud.rpic.io.netty.server;

import com.spud.rpic.common.constants.RpcConstants;
import com.spud.rpic.common.domain.RpcRequest;
import com.spud.rpic.common.domain.RpcResponse;
import com.spud.rpic.io.common.ProtocolMsg;
import com.spud.rpic.io.netty.server.invocation.DefaultServerInvocation;
import com.spud.rpic.io.serializer.Serializer;
import com.spud.rpic.io.serializer.SerializerFactory;
import com.spud.rpic.metrics.RpcMetricsRecorder;

import io.micrometer.core.instrument.Timer;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Spud
 * @date 2025/2/9
 */
@Slf4j
public class RpcServerHandler extends SimpleChannelInboundHandler<ProtocolMsg> {

	private final Serializer serializer;
	private final SerializerFactory serializerFactory;
	private final DefaultServerInvocation defaultServerInvocation;
	private final RpcMetricsRecorder metricsRecorder;

	/**
	 * 创建主Handler实例（由Spring管理的单例）
	 */
	public RpcServerHandler(Serializer serializer, SerializerFactory serializerFactory,
	                        DefaultServerInvocation defaultServerInvocation, RpcMetricsRecorder metricsRecorder) {
		this.serializer = serializer;
		this.serializerFactory = serializerFactory;
		this.defaultServerInvocation = defaultServerInvocation;
		this.metricsRecorder = metricsRecorder;
		log.debug("Created master RpcServerHandler with serializer: {}", serializer.getType());
	}

	/**
	 * 为每个Channel创建独立的Handler实例，但共享序列化器和调用器
	 */
	@Override
	public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
		log.debug("Server handler added to channel: {}", ctx.channel());
		super.handlerAdded(ctx);
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		log.info("Server channelRead called with message of type: {}", msg.getClass().getName());
		super.channelRead(ctx, msg);
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		log.debug("Server channel active: {}", ctx.channel());
		super.channelActive(ctx);
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, ProtocolMsg msg) throws Exception {
		log.info("Server Channel[{}] channelRead0 received message: type={} (hex: 0x{}), contentLength={}, contentStart={}",
			ctx.channel().id().asShortText(), msg.getType(),
			Integer.toHexString(msg.getType() & 0xFF), msg.getContentLength(),
			msg.getContent() != null && msg.getContent().length > 0
				? bytesToHex(msg.getContent(), 0, Math.min(10, msg.getContent().length)) + "..."
				: "empty");

		if (msg.getType() == RpcConstants.TYPE_REQUEST) {
			log.info("Server Channel[{}] Received REQUEST message, time: {}",
				ctx.channel().id().asShortText(), System.currentTimeMillis());

			Timer.Sample sample = metricsRecorder.startServerSample();
			byte[] requestBytes = msg.getContent();
			int requestBytesLength = msg.getContentLength();
			String caller = remoteEndpoint(ctx);
			final RpcRequest[] requestHolder = new RpcRequest[1];
			Serializer activeSerializer = resolveSerializer(msg.getSerializerType());
			try {
				// 打印请求字节开头用于调试
				log.info("Server Request content start: {}",
					bytesToHex(requestBytes, 0, Math.min(20, requestBytes.length)));

				RpcRequest request = activeSerializer.deserialize(requestBytes, RpcRequest.class);
				requestHolder[0] = request;
				log.info("Server Channel[{}] Deserialized request: {}, method: {}",
					ctx.channel().id().asShortText(), request.getRequestId(), request.getMethodName());

				RpcResponse response = defaultServerInvocation.handleRequest(request);

				log.info("Server Channel[{}] Processed request: {}, created response {}",
					ctx.channel().id().asShortText(), request.getRequestId(), response);

				byte[] responseBytes = activeSerializer.serialize(response);
				log.info("Server Channel[{}] Serialized response for request: {}, bytes length: {}, start: {}",
					ctx.channel().id().asShortText(), request.getRequestId(), responseBytes.length,
					bytesToHex(responseBytes, 0, Math.min(20, responseBytes.length)));
				metricsRecorder.recordServer(sample, request.getServiceKey(), request.getMethodName(), caller,
					true, null, requestBytesLength, responseBytes.length);

				// 使用新的便捷方法创建响应消息
				ProtocolMsg responseMsg = ProtocolMsg.responseFromBytes(responseBytes, activeSerializer.getCode());
				log.info("Server Channel[{}] Created response message, type: {} (hex: 0x{}), contentLength: {}",
					ctx.channel().id().asShortText(), responseMsg.getType(),
					Integer.toHexString(responseMsg.getType() & 0xFF), responseMsg.getContentLength());

				log.info("Server Channel[{}] Sending response to client, request_id: {}, time: {}",
					ctx.channel().id().asShortText(), request.getRequestId(), System.currentTimeMillis());
				final String requestIdForLog = request.getRequestId();

				// 添加Listener来确认是否成功发送
				ctx.writeAndFlush(responseMsg).addListener(future -> {
					if (future.isSuccess()) {
						log.info("Server Channel[{}] Successfully sent response for request: {}, time: {}",
							ctx.channel().id().asShortText(), requestIdForLog, System.currentTimeMillis());
					} else {
						log.error("Server Channel[{}] Failed to send response for request: {}, error: {}",
							ctx.channel().id().asShortText(), requestIdForLog, future.cause().getMessage(), future.cause());
					}
				});
			} catch (Exception e) {
				log.error("Server Channel[{}] Error processing request: {}",
					ctx.channel().id().asShortText(), e.getMessage(), e);
				RpcRequest failedRequest = requestHolder[0];
				metricsRecorder.recordServer(sample,
					failedRequest != null ? failedRequest.getServiceKey() : null,
					failedRequest != null ? failedRequest.getMethodName() : null,
					caller, false, e, requestBytesLength, -1);
			}
		} else {
			log.error("Server Channel[{}] Unknown message type: {} (hex: 0x{})",
				ctx.channel().id().asShortText(), msg.getType(),
				Integer.toHexString(msg.getType() & 0xFF));
		}
	}

	/**
	 * 将字节数组转换为十六进制字符串
	 */
	private String bytesToHex(byte[] bytes, int offset, int length) {
		StringBuilder sb = new StringBuilder();
		for (int i = offset; i < offset + length && i < bytes.length; i++) {
			sb.append(String.format("%02X ", bytes[i] & 0xFF));
		}
		return sb.toString();
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		log.error("Server Channel[{}] RpcServerHandler exception", ctx.channel().id().asShortText(), cause);
		ctx.close();
	}

	public Serializer getSerializer() {
		return this.serializer;
	}

	public DefaultServerInvocation getDefaultServerInvocation() {
		return this.defaultServerInvocation;
	}

	public SerializerFactory getSerializerFactory() {
		return this.serializerFactory;
	}

	public RpcMetricsRecorder getMetricsRecorder() {
		return this.metricsRecorder;
	}

	private Serializer resolveSerializer(byte serializerType) {
		if (serializerFactory == null) {
			return serializer;
		}
		try {
			return serializerFactory.getSerializer(serializerType);
		} catch (IllegalArgumentException ignored) {
			log.warn("Unsupported serializer code: {}, fallback to default {}", serializerType, serializer.getType());
			return serializer;
		}
	}

	private String remoteEndpoint(ChannelHandlerContext ctx) {
		if (ctx == null || ctx.channel() == null) {
			return "unknown";
		}
		java.net.SocketAddress address = ctx.channel().remoteAddress();
		if (address instanceof java.net.InetSocketAddress) {
			java.net.InetSocketAddress inet = (java.net.InetSocketAddress) address;
			return inet.getHostString() + ":" + inet.getPort();
		}
		return address != null ? address.toString() : "unknown";
	}
}