package com.asuka.backend.common.properties;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiPropertiesTest {

    @Test
    void fakeProviderDoesNotAdvertiseTheConfiguredRealModel() {
        AiProperties properties = new AiProperties();
        properties.getDeepseek().setModel("deepseek/deepseek-v4.1-flash");
        properties.setProvider("fake");

        assertEquals("fake", properties.getActiveModelName());
        properties.setProvider("FAKE");
        assertEquals("fake", properties.getActiveModelName());
    }

    @Test
    void realProviderUsesItsConfiguredModel() {
        AiProperties properties = new AiProperties();
        properties.getDeepseek().setModel("deepseek/deepseek-v4-pro");

        assertEquals("deepseek/deepseek-v4-pro", properties.getActiveModelName());
    }
}
