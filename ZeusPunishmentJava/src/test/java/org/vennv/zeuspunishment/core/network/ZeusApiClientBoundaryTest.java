package org.vennv.zeuspunishment.core.network;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class ZeusApiClientBoundaryTest {
    @Test
    void exposesOnlyAllowedPublicRouteConstants() throws Exception {
        assertEquals("/api/public/violations", getConstant("PUBLIC_VIOLATIONS_PATH"));
        assertEquals("/api/public/violations/stream", getConstant("PUBLIC_VIOLATIONS_STREAM_PATH"));
        assertEquals("/api/public/list_models", getConstant("PUBLIC_LIST_MODELS_PATH"));
    }

    @Test
    void exposesNonDestructiveCompatibilityProbe() throws Exception {
        Method method = ZeusApiClient.class.getDeclaredMethod("probeCompatibility");
        assertEquals(boolean.class, method.getReturnType());
    }

    @Test
    void acknowledgementBoundaryFailsClosedWithoutPublicRoute() throws Exception {
        Method method = ZeusApiClient.class.getDeclaredMethod("acknowledgeViolations", java.util.List.class);
        assertEquals(boolean.class, method.getReturnType());
        ZeusApiClient client = new ZeusApiClient("http://127.0.0.1:9");
        assertTrue(client.acknowledgeViolations(java.util.Collections.emptyList()));
        assertFalse(client.acknowledgeViolations(java.util.Collections.singletonList("uid-1")));
    }

    private static String getConstant(String fieldName) throws Exception {
        Field field = ZeusApiClient.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (String) field.get(null);
    }
}
