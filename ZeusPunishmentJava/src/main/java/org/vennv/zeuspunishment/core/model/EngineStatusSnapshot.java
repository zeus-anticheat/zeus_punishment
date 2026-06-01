package org.vennv.zeuspunishment.core.model;

import org.vennv.zeuspunishment.core.network.ApiStatusSnapshot;
import org.vennv.zeuspunishment.core.scheduler.BanwaveManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class EngineStatusSnapshot {
    private final boolean lifecycleRunning;
    private final boolean enforcementActive;
    private final boolean dryRun;
    private final String policyPreset;
    private final boolean highImpactEnabled;
    private final ApiStatusSnapshot apiStatus;
    private final BanwaveManager.QueueState queueState;
    private final List<String> recentOutcomes;

    public EngineStatusSnapshot(boolean lifecycleRunning, boolean enforcementActive, boolean dryRun, String policyPreset, boolean highImpactEnabled, ApiStatusSnapshot apiStatus, BanwaveManager.QueueState queueState, List<String> recentOutcomes) {
        this.lifecycleRunning = lifecycleRunning;
        this.enforcementActive = enforcementActive;
        this.dryRun = dryRun;
        this.policyPreset = policyPreset == null ? "observe" : policyPreset;
        this.highImpactEnabled = highImpactEnabled;
        this.apiStatus = apiStatus;
        this.queueState = queueState;
        this.recentOutcomes = Collections.unmodifiableList(new ArrayList<>(recentOutcomes == null ? Collections.emptyList() : recentOutcomes));
    }

    public boolean isLifecycleRunning() { return lifecycleRunning; }
    public boolean isEnforcementActive() { return enforcementActive; }
    public boolean isDryRun() { return dryRun; }
    public String getPolicyPreset() { return policyPreset; }
    public boolean isHighImpactEnabled() { return highImpactEnabled; }
    public ApiStatusSnapshot getApiStatus() { return apiStatus; }
    public BanwaveManager.QueueState getQueueState() { return queueState; }
    public List<String> getRecentOutcomes() { return recentOutcomes; }
}
