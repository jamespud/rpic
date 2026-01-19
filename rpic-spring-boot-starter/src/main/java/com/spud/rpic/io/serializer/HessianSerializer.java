package com.spud.rpic.io.serializer;

import com.caucho.hessian.io.HessianInput;
import com.caucho.hessian.io.HessianOutput;
import com.spud.rpic.common.exception.SerializeException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * @author Spud
 * @date 2025/2/19
 */
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
	public <T> T deserialize(byte[] data, Class<T> clz) throws SerializeException {
		try (ByteArrayInputStream bis = new ByteArrayInputStream(data)) {
			HessianInput hessianInput = new HessianInput(bis);
			Object obj = hessianInput.readObject();
			if (obj == null) {
				return null;
			}
			if (!clz.isInstance(obj)) {
				throw new SerializeException("Deserialized object type mismatch. Expected: " + clz.getName() + ", but got: " + obj.getClass().getName());
			}
			return clz.cast(obj);
		} catch (SerializeException e) {
			throw e; // rethrow our own exceptions
		} catch (Exception e) {
			throw new SerializeException("Error deserializing object", e);
		}
	}

	@Override
	public String getType() {
		return SerializerType.HESSIAN.getType();
	}
}