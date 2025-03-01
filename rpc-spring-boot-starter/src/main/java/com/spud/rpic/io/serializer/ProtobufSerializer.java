package com.spud.rpic.io.serializer;

import com.alibaba.nacos.shaded.com.google.protobuf.MessageLite;
import com.alibaba.nacos.shaded.com.google.protobuf.Parser;
import com.spud.rpic.common.exception.SerializeException;
import org.springframework.stereotype.Component;

/**
 * @author Spud
 * @date 2025/2/9
 */
@Component
public class ProtobufSerializer implements Serializer {

    @Override
    public <T> byte[] serialize(T obj) throws SerializeException {
        try {
            if (!(obj instanceof MessageLite)) {
                throw new SerializeException("Object must be an instance of MessageLite");
            }
            return ((MessageLite) obj).toByteArray();
        } catch (Exception e) {
            throw new SerializeException("Error serializing object", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T deserialize(byte[] data, Class<T> clz) throws SerializeException {
        try {
            if (!MessageLite.class.isAssignableFrom(clz)) {
                throw new SerializeException("Class must be a subclass of MessageLite");
            }
            MessageLite.Builder builder = getBuilder(clz);
            Parser<?> parser = builder.getDefaultInstanceForType().getParserForType();
            return (T) parser.parseFrom(data);
        } catch (Exception e) {
            throw new SerializeException("Error deserializing object", e);
        }
    }

    private MessageLite.Builder getBuilder(Class<?> clz) throws Exception {
        return (MessageLite.Builder) clz.getMethod("newBuilder").invoke(null);
    }

    @Override
    public byte getType() {
        return SerializerType.PROTOBUF.getType();
    }
}