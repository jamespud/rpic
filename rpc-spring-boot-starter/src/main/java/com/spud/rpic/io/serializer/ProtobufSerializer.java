package com.spud.rpic.io.serializer;

import com.alibaba.nacos.shaded.com.google.protobuf.InvalidProtocolBufferException;
import com.spud.rpic.common.domain.OrcRpcRequest;
import com.spud.rpic.common.domain.OrcRpcResponse;

/**
 * @author Spud
 * @date 2025/2/9
 */
public class ProtobufSerializer implements Serializer {

    @Override
    public byte[] serialize(Object obj) {
        if (obj instanceof OrcRpcRequest) {
            OrcRpcRequest request = (OrcRpcRequest) obj;
            OrcRpcRequest.Builder builder = OrcRpcRequest.newBuilder();
            builder.setServiceName(request.getServiceName());
            builder.setMethodName(request.getMethodName());
            // 处理参数类型和参数
            for (Class<?> parameterType : request.getParameterTypes()) {
                builder.addParameterTypes(parameterType.getName());
            }
            for (Object parameter : request.getParameters()) {
                if (parameter instanceof String) {
                    builder.addParameters(OrcRpcProto.Parameter.newBuilder().setStringValue((String) parameter).build());
                } else if (parameter instanceof Integer) {
                    builder.addParameters(OrcRpcProto.Parameter.newBuilder().setIntValue((Integer) parameter).build());
                }
                // 处理其他类型参数
            }
            return builder.build().toByteArray();
        } else if (obj instanceof OrcRpcResponse) {
            OrcRpcResponse response = (OrcRpcResponse) obj;
            OrcRpcProto.OrcRpcResponse.Builder builder = OrcRpcProto.OrcRpcResponse.newBuilder();
            if (response.getResult() != null) {
                if (response.getResult() instanceof String) {
                    builder.setResult(OrcRpcProto.Result.newBuilder().setStringValue((String) response.getResult()).build());
                } else if (response.getResult() instanceof Integer) {
                    builder.setResult(OrcRpcProto.Result.newBuilder().setIntValue((Integer) response.getResult()).build());
                }
                // 处理其他类型结果
            }
            if (response.getException() != null) {
                builder.setException(response.getException().getMessage());
            }
            builder.setRequestId(response.getRequestId());
            return builder.build().toByteArray();
        }
        throw new IllegalArgumentException("Unsupported object type for serialization");
    }

    @Override
    public <T> T deserialize(byte[] data, Class<T> clazz) {
        if (clazz == OrcRpcRequest.class) {
            try {
                OrcRpcRequest protoRequest = OrcRpcRequest.parseFrom(data);
                OrcRpcRequest request = new OrcRpcRequest();
                request.setServiceName(protoRequest.getServiceName());
                request.setMethodName(protoRequest.getMethodName());
                // 解析参数类型和参数
                Class<?>[] parameterTypes = new Class[protoRequest.getParameterTypesCount()];
                for (int i = 0; i < protoRequest.getParameterTypesCount(); i++) {
                    try {
                        parameterTypes[i] = Class.forName(protoRequest.getParameterTypes(i));
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException("Class not found for parameter type", e);
                    }
                }
                request.setParameterTypes(parameterTypes);
                Object[] parameters = new Object[protoRequest.getParametersCount()];
                for (int i = 0; i < protoRequest.getParametersCount(); i++) {
                    OrcRpcProto.Parameter parameter = protoRequest.getParameters(i);
                    if (parameter.hasStringValue()) {
                        parameters[i] = parameter.getStringValue();
                    } else if (parameter.hasIntValue()) {
                        parameters[i] = parameter.getIntValue();
                    }
                    // 处理其他类型参数
                }
                request.setParameters(parameters);
                return (T) request;
            } catch (InvalidProtocolBufferException e) {
                throw new RuntimeException("Deserialization failed for OrcRpcRequest", e);
            }
        } else if (clazz == OrcRpcResponse.class) {
            try {
                OrcRpcProto.OrcRpcResponse protoResponse = OrcRpcProto.OrcRpcResponse.parseFrom(data);
                OrcRpcResponse response = new OrcRpcResponse();
                if (protoResponse.hasResult()) {
                    if (protoResponse.getResult().hasStringValue()) {
                        response.setResult(protoResponse.getResult().getStringValue());
                    } else if (protoResponse.getResult().hasIntValue()) {
                        response.setResult(protoResponse.getResult().getIntValue());
                    }
                    // 处理其他类型结果
                }
                if (protoResponse.hasException()) {
                    response.setException(new RuntimeException(protoResponse.getException()));
                }
                response.setRequestId(protoResponse.getRequestId());
                return (T) response;
            } catch (InvalidProtocolBufferException e) {
                throw new RuntimeException("Deserialization failed for OrcRpcResponse", e);
            }
        }
        throw new IllegalArgumentException("Unsupported class type for deserialization");
    }
}