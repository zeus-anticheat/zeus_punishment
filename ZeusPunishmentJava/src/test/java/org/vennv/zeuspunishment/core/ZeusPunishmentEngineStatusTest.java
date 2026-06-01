package org.vennv.zeuspunishment.core;

import org.junit.jupiter.api.Test;
import org.vennv.zeuspunishment.core.config.PunishmentConfig;
import org.vennv.zeuspunishment.core.model.EngineStatusSnapshot;
import org.vennv.zeuspunishment.core.network.ZeusApiClient;
import org.vennv.zeuspunishment.core.scheduler.BanwaveManager;

import static org.junit.jupiter.api.Assertions.*;

class ZeusPunishmentEngineStatusTest {
    @Test
    void snapshotIncludesPolicyApiAndQueueState() {
        PunishmentConfig config = new PunishmentConfig();
        config.setPolicyPreset("review");
        config.setBanwaveEnabled(true);
        ZeusApiClient api = new ZeusApiClient("http://127.0.0.1:1");
        PunishmentDispatcher dispatcher = new TestDispatcher();
        BanwaveManager banwave = new BanwaveManager(config, dispatcher);
        ZeusPunishmentEngine engine = new ZeusPunishmentEngine(config, dispatcher, api, banwave);

        EngineStatusSnapshot snapshot = engine.getStatusSnapshot();

        assertEquals("review", snapshot.getPolicyPreset());
        assertTrue(snapshot.isDryRun());
        assertTrue(snapshot.isHighImpactEnabled());
        assertNotNull(snapshot.getApiStatus());
        assertEquals(0, snapshot.getQueueState().getQueuedCount());
        assertTrue(snapshot.getRecentOutcomes().isEmpty());
    }

    static final class TestDispatcher implements PunishmentDispatcher {}
}
