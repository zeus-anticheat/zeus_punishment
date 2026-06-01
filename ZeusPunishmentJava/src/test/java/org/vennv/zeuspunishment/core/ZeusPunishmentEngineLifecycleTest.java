package org.vennv.zeuspunishment.core;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ZeusPunishmentEngineLifecycleTest {
    @Test
    void exposesHealthGatedLifecycleState() throws Exception {
        Field running = ZeusPunishmentEngine.class.getDeclaredField("lifecycleRunning");
        Field generation = ZeusPunishmentEngine.class.getDeclaredField("lifecycleGeneration");
        Field enforcement = ZeusPunishmentEngine.class.getDeclaredField("enforcementEnabled");
        Field probeThread = ZeusPunishmentEngine.class.getDeclaredField("probeThread");

        assertNotNull(running);
        assertNotNull(generation);
        assertNotNull(enforcement);
        assertNotNull(probeThread);
    }
}
