package org.vennv.zeuspunishment.core.network;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZeusApiClientLifecycleTest {
    @Test
    void exposesGenerationAndActiveConnectionStateForInterruptibleStop() throws Exception {
        Field generation = ZeusApiClient.class.getDeclaredField("streamGeneration");
        Field connection = ZeusApiClient.class.getDeclaredField("activeConnection");
        Field reader = ZeusApiClient.class.getDeclaredField("activeReader");
        Method stopStream = ZeusApiClient.class.getDeclaredMethod("stopStream");

        assertNotNull(generation);
        assertNotNull(connection);
        assertNotNull(reader);
        assertNotNull(stopStream);
    }

    @Test
    void constructorAcceptsLifecycleTimingValues() throws Exception {
        ZeusApiClient client = new ZeusApiClient("http://127.0.0.1:8080", 1234, 2345, 3456, 4567);
        Field reconnectMax = ZeusApiClient.class.getDeclaredField("reconnectMaxMs");
        reconnectMax.setAccessible(true);

        assertTrue((Integer) reconnectMax.get(client) >= 3456);
    }
}
