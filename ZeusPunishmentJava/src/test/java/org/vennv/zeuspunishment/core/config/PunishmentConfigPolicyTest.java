package org.vennv.zeuspunishment.core.config;

import org.junit.jupiter.api.Test;
import org.vennv.zeuspunishment.core.model.PolicyDecision;
import org.vennv.zeuspunishment.core.model.Severity;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class PunishmentConfigPolicyTest {
    @Test
    public void defaultsAreDryRunObserveAndHighImpactDisabled() {
        PunishmentConfig config = new PunishmentConfig();

        assertTrue(config.isDryRun());
        assertFalse(config.isEnforcementEnabled());
        assertFalse(config.isImmediateBanEnabled());
        assertFalse(config.isBroadcastsEnabled());
        assertFalse(config.isEffectsEnabled());
        assertTrue(config.isManualBanwaveApprovalRequired());
        assertEquals("observe", config.getPolicyPreset());
        assertEquals("logs/zeus-punishment-audit.jsonl", config.getAuditPath());
    }

    @Test
    public void presetsAreResolvedAndReviewQueuesManually() {
        assertEquals(Set.of("observe", "warn", "kick", "review", "enforce"), PunishmentConfig.allowedPolicyPresets());

        PunishmentConfig config = new PunishmentConfig();
        config.setPolicyPreset("review");
        PolicyDecision decision = config.resolvePolicyDecision("profile-a", Severity.BAN, "player:violation");

        assertEquals("review", decision.policyTier());
        assertEquals(PolicyDecision.PolicyAction.BANWAVE, decision.action());
        assertTrue(decision.dryRun());
        assertFalse(decision.highImpactAllowed());
        assertTrue(config.isManualBanwaveApprovalRequired());
    }

    @Test
    public void policyActionsIncludeRequiredSemantics() {
        assertTrue(Set.of(PolicyDecision.PolicyAction.values()).containsAll(Set.of(
                PolicyDecision.PolicyAction.LOG,
                PolicyDecision.PolicyAction.WARN,
                PolicyDecision.PolicyAction.KICK,
                PolicyDecision.PolicyAction.BANWAVE,
                PolicyDecision.PolicyAction.BAN,
                PolicyDecision.PolicyAction.EFFECT
        )));
    }

    @Test
    public void invalidPresetAndActionsFailSafe() {
        PunishmentConfig config = new PunishmentConfig();
        assertFalse(config.setPolicyPreset("unknown"));
        assertEquals("observe", config.getPolicyPreset());

        assertEquals(ActionType.NONE, ActionType.fromString("unknown"));
        config.setPolicyOverride("profile-a", Severity.KICK, "invalid");
        PolicyDecision decision = config.resolvePolicyDecision("profile-a", Severity.KICK, "stable-key");
        assertEquals("observe", decision.policyTier());
        assertEquals(PolicyDecision.PolicyAction.LOG, decision.action());
        assertTrue(decision.dryRun());
    }
}
