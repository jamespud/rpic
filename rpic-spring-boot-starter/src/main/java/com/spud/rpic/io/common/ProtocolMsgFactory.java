package com.spud.rpic.io.common;

import com.spud.rpic.io.serializer.Serializer;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Spud
 * @date 2025/3/1
 */
public class ProtocolMsgFactory {

	private final Serializer serializer;
	private final AtomicLong messageIdGenerator = new AtomicLong(0);

	public ProtocolMsgFactory(Serializer serializer) {
		this.serializer = serializer;
	}

//    /**
//     * 创建请求消息
//     */
//    public ProtocolMsg createRequestMsg(RpcRequest request) {
//        byte[] content = serializer.serialize(request);
//        return ProtocolMsg.builder()
//                .type(RpcConstants.TYPE_REQUEST)
//                .serializeType(serializer.getType())
//                .id(messageIdGenerator.incrementAndGet())
//                .length(content.length)
//                .content(content)
//                .build();
//    }
//
//    /**
//     * 创建响应消息
//     */
//    public ProtocolMsg createResponseMsg(RpcResponse response) {
//        byte[] content = serializer.serialize(response);
//        return ProtocolMsg.builder()
//                .type(RpcConstants.TYPE_RESPONSE)
//                .serializeType(serializer.getType())
//                .id(messageIdGenerator.incrementAndGet())
//                .length(content.length)
//                .content(content)
//                .build();
//    }
}
