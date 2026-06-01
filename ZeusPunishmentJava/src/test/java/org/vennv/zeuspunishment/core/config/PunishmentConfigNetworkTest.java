package org.vennv.zeuspunishment.core.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PunishmentConfigNetworkTest {
    @Test
    void hasSafeNetworkLifecycleDefaults() {
        PunishmentConfig config = new PunishmentConfig();

        assertEquals(5000, config.getConnectTimeoutMs());
        assertEquals(5000, config.getReadTimeoutMs());
        assertEquals(1000, config.getReconnectInitialMs());
        assertEquals(30000, config.getReconnectMaxMs());
        assertEquals(3, config.getHealthRetries());
    }
}
