package org.vennv.zeuspunishment.core.model;

public record PolicyDecision(
        String policyTier,
        PolicyAction action,
        boolean dryRun,
        boolean highImpactAllowed,
        String stableKey,
        String operationalReason
) {
    public enum PolicyAction {
        LOG,
        WARN,
        KICK,
        BANWAVE,
        BAN,
        EFFECT
    }

    public PolicyDecision {
        policyTier = policyTier == null || policyTier.isBlank() ? "observe" : policyTier;
        action = action == null ? PolicyAction.LOG : action;
        stableKey = stableKey == null ? "" : stableKey;
        operationalReason = operationalReason == null ? "policy decision" : operationalReason;
    }
}
