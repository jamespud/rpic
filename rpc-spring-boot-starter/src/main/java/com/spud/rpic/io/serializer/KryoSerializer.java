package com.spud.rpic.io.serializer;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.spud.rpic.common.exception.SerializeException;
import org.springframework.stereotype.Component;

/**
 * @author Spud
 * @date 2025/2/27
 */
@Component
public class KryoSerializer implements Serializer {

    private static final ThreadLocal<Kryo> kryoThreadLocal = ThreadLocal.withInitial(() -> {
        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(false);
        return kryo;
    });

    @Override
    public <T> byte[] serialize(T obj) throws SerializeException {
        try (Output output = new Output(1024, -1)) {
            Kryo kryo = kryoThreadLocal.get();
            kryo.writeObject(output, obj);
            return output.toBytes();
        } catch (Exception e) {
            throw new SerializeException("Error serializing object", e);
        }
    }

    @Override
    public <T> T deserialize(byte[] data, Class<T> clz) throws SerializeException {
        try (Input input = new Input(data)) {
            Kryo kryo = kryoThreadLocal.get();
            return kryo.readObject(input, clz);
        } catch (Exception e) {
            throw new SerializeException("Error deserializing object", e);
        }
    }

    @Override
    public byte getType() {
        return SerializerType.KRYO.getType();
    }
}