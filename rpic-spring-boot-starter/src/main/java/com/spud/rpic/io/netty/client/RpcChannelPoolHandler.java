package com.spud.rpic.io.netty.client;

import com.spud.rpic.io.netty.LoggingChannelHandler;
import com.spud.rpic.io.netty.ProtocolDecoder;
import com.spud.rpic.io.netty.ProtocolEncoder;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.pool.AbstractChannelPoolHandler;
import io.netty.util.AttributeKey;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Spud
 * @date 2025/2/17
 */

@Slf4j
public class RpcChannelPoolHandler extends AbstractChannelPoolHandler {

	private static final AttributeKey<Long> LAST_ACCESS_TIME = AttributeKey.valueOf("lastAccessTime");

	private static final int MAX_IDLE_MINUTES = 15;

	private final RpcClientHandler sharedHandler;
	private final boolean debugMode;
	private final AtomicInteger handlerCounter = new AtomicInteger(0);

	public RpcChannelPoolHandler(RpcClientHandler sharedHandler, boolean debugMode) {
		this.sharedHandler = sharedHandler;
		this.debugMode = debugMode;
	}

	@Override
	public void channelCreated(Channel ch) {
		// 获取远程地址信息
		InetSocketAddress remoteAddress = null;
		try {
			remoteAddress = (InetSocketAddress) ch.remoteAddress();
		} catch (Exception e) {
			// Channel可能尚未连接，无法获取远程地址
			log.debug("Channel not connected yet, cannot get remote address: {}", ch);
		}

		ChannelPipeline pipeline = ch.pipeline();

		// 添加调试日志处理器（如果启用）
		if (debugMode) {
			pipeline.addLast("logger", new LoggingChannelHandler("CLIENT", true));
		}

		// 添加协议编解码器
		pipeline.addLast("decoder", new ProtocolDecoder());
		pipeline.addLast("encoder", new ProtocolEncoder());

		// 为每个channel创建独立的handler实例，共享状态
		String handlerName = "handler-" + handlerCounter.incrementAndGet();
		RpcClientHandler channelHandler = new RpcClientHandler(sharedHandler.getSerializer(),
			sharedHandler.getSerializerFactory(), sharedHandler);
		pipeline.addLast(handlerName, channelHandler);

		log.debug("Channel created: {}, remote address: {}, handler: {}", ch, remoteAddress,
			handlerName);
		ch.attr(LAST_ACCESS_TIME).set(System.nanoTime());
	}

	@Override
	public void channelReleased(Channel ch) throws Exception {
		if (!ch.isActive()) {
			log.debug("Channel released but not active, closing: {}", ch);
			ch.close();
			return;
		}

		ch.attr(LAST_ACCESS_TIME).set(System.nanoTime());
		log.debug("Channel released and last access time updated: {}", ch);
		super.channelReleased(ch);
	}

	@Override
	public void channelAcquired(Channel ch) {
		long idleNanos = System.nanoTime() - ch.attr(LAST_ACCESS_TIME).get();
		long idleMinutes = TimeUnit.NANOSECONDS.toMinutes(idleNanos);
		log.debug("Channel acquired: {}, idle time: {} minutes", ch, idleMinutes);

		if (idleMinutes > MAX_IDLE_MINUTES && !ch.isActive()) {
			log.debug("Channel idle for too long and inactive, closing: {}", ch);
			ch.close();
		} else {
			ch.attr(LAST_ACCESS_TIME).set(System.nanoTime());
		}
	}
}
