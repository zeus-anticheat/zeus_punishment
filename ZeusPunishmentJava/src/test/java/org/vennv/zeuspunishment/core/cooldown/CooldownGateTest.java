package org.vennv.zeuspunishment.core.cooldown;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class CooldownGateTest {
    @Test
    public void suppressesRepeatWithinWindow() {
        AtomicLong now = new AtomicLong(1_000L);
        CooldownGate gate = new CooldownGate(now::get);

        CooldownGate.Decision first = gate.check("player", "violation", CooldownGate.Category.PUNISHMENT, Duration.ofMillis(500));
        CooldownGate.Decision second = gate.check("player", "violation", CooldownGate.Category.PUNISHMENT, Duration.ofMillis(500));

        assertTrue(first.allowed());
        assertFalse(second.allowed());
        assertEquals(1, second.suppressionCount());
        assertEquals(500, second.windowMillis());
    }

    @Test
    public void distinctKeysAndCategoriesDoNotSuppressEachOther() {
        AtomicLong now = new AtomicLong(1_000L);
        CooldownGate gate = new CooldownGate(now::get);
        assertTrue(gate.check("player-a", "violation", CooldownGate.Category.BROADCAST, Duration.ofSeconds(1)).allowed());
        assertTrue(gate.check("player-b", "violation", CooldownGate.Category.BROADCAST, Duration.ofSeconds(1)).allowed());
        assertTrue(gate.check("player-a", "other", CooldownGate.Category.BROADCAST, Duration.ofSeconds(1)).allowed());
        assertTrue(gate.check("player-a", "violation", CooldownGate.Category.EFFECT, Duration.ofSeconds(1)).allowed());
    }

    @Test
    public void suppressionDecisionExposesAuditSafeMetadata() {
        AtomicLong now = new AtomicLong(1_000L);
        CooldownGate gate = new CooldownGate(now::get);
        gate.check("player", "violation", CooldownGate.Category.EFFECT, Duration.ofMillis(250));
        CooldownGate.Decision suppressed = gate.check("player", "violation", CooldownGate.Category.EFFECT, Duration.ofMillis(250));

        assertEquals("player:violation:EFFECT", suppressed.stableKey());
        assertEquals(CooldownGate.Category.EFFECT, suppressed.category());
        assertEquals(250, suppressed.windowMillis());
        assertEquals(1, suppressed.suppressionCount());
        assertTrue(suppressed.remainingMillis() > 0);
    }

    @Test
    public void statusAndGuiRefreshCategoriesExist() {
        assertNotNull(CooldownGate.Category.STATUS_REFRESH);
        assertNotNull(CooldownGate.Category.GUI_REFRESH);
        PunishmentConfigBackedWindows windows = new PunishmentConfigBackedWindows();
        assertEquals(5_000L, windows.statusRefreshWindowMillis());
        assertEquals(5_000L, windows.guiRefreshWindowMillis());
    }

    private static class PunishmentConfigBackedWindows extends org.vennv.zeuspunishment.core.config.PunishmentConfig { }
}
