package com.spud.rpic.io.serializer;

import com.spud.rpic.common.exception.SerializeException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class HessianSerializerWhitelistTest {

    static class TestBean implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        private String name;

        public TestBean() {}

        public TestBean(String name) { this.name = name; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    @Test
    public void testDeserializeRejectedByWhitelist() throws Exception {
        HessianSerializer serializer = new HessianSerializer();
        TestBean bean = new TestBean("alice");
        byte[] data = serializer.serialize(bean);

        // Temporarily set whitelist to exclude test package
        System.setProperty("rpic.hessian.whitelist", "java.,javax.");

        try {
            Object result = serializer.deserialize(data, TestBean.class);
            // If deserialization did not throw, ensure the returned object is NOT an instance of TestBean
            // (i.e., Hessian fell back to a safe structure such as Map)
            assertFalse(result instanceof TestBean, "Deserialized into TestBean while whitelist excludes it");
        } catch (SerializeException e) {
            // acceptable: serializer refuses to deserialize unwhitelisted class
        } finally {
            // restore default
            System.clearProperty("rpic.hessian.whitelist");
        }
    }

    @Test
    public void testDeserializeAllowedWhenWhitelisted() throws Exception {
        HessianSerializer serializer = new HessianSerializer();
        TestBean bean = new TestBean("bob");
        byte[] data = serializer.serialize(bean);

        // Allow the test package explicitly
        System.setProperty("rpic.hessian.whitelist", "java.,javax.,com.spud.rpic.io.serializer.");

        try {
            TestBean result = serializer.deserialize(data, TestBean.class);
            assertNotNull(result);
            assertEquals("bob", result.getName());
        } finally {
            System.clearProperty("rpic.hessian.whitelist");
        }
    }
}
