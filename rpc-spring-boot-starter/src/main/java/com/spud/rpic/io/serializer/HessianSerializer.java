package com.spud.rpic.io.serializer;

import com.caucho.hessian.io.Hessian2Input;
import com.caucho.hessian.io.Hessian2Output;
import com.caucho.hessian.io.HessianInput;
import com.caucho.hessian.io.HessianOutput;
import com.caucho.hessian.io.SerializerFactory;
import com.spud.rpic.common.exception.SerializeException;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author Spud
 * @date 2025/2/19
 */
@Component
public class HessianSerializer implements Serializer {

    @Override
    public <T> byte[] serialize(T obj) throws SerializeException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            HessianOutput hessianOutput = new HessianOutput(bos);
            hessianOutput.writeObject(obj);
            return bos.toByteArray();
        } catch (Exception e) {
            throw new SerializeException("Error serializing object", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T deserialize(byte[] data, Class<T> clz) throws SerializeException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(data)) {
            HessianInput hessianInput = new HessianInput(bis);
            return (T) hessianInput.readObject(clz);
        } catch (Exception e) {
            throw new SerializeException("Error deserializing object", e);
        }
    }

    @Override
    public byte getType() {
        return SerializerType.HESSIAN.getType();
    }
}