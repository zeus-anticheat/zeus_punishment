# Graph Report - zeus_punishment  (2026-06-02)

## Corpus Check
- 46 files · ~10,990 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 654 nodes · 1617 edges · 33 communities (28 shown, 5 thin omitted)
- Extraction: 73% EXTRACTED · 27% INFERRED · 0% AMBIGUOUS · INFERRED: 444 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `295e41b5`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- [[_COMMUNITY_Community 0|Community 0]]
- [[_COMMUNITY_Community 1|Community 1]]
- [[_COMMUNITY_Community 2|Community 2]]
- [[_COMMUNITY_Community 3|Community 3]]
- [[_COMMUNITY_Community 4|Community 4]]
- [[_COMMUNITY_Community 5|Community 5]]
- [[_COMMUNITY_Community 6|Community 6]]
- [[_COMMUNITY_Community 7|Community 7]]
- [[_COMMUNITY_Community 8|Community 8]]
- [[_COMMUNITY_Community 9|Community 9]]
- [[_COMMUNITY_Community 10|Community 10]]
- [[_COMMUNITY_Community 11|Community 11]]
- [[_COMMUNITY_Community 12|Community 12]]
- [[_COMMUNITY_Community 13|Community 13]]
- [[_COMMUNITY_Community 14|Community 14]]
- [[_COMMUNITY_Community 15|Community 15]]
- [[_COMMUNITY_Community 16|Community 16]]
- [[_COMMUNITY_Community 17|Community 17]]
- [[_COMMUNITY_Community 18|Community 18]]
- [[_COMMUNITY_Community 23|Community 23]]
- [[_COMMUNITY_Community 24|Community 24]]
- [[_COMMUNITY_Community 26|Community 26]]
- [[_COMMUNITY_Community 27|Community 27]]
- [[_COMMUNITY_Community 28|Community 28]]
- [[_COMMUNITY_Community 29|Community 29]]
- [[_COMMUNITY_Community 30|Community 30]]
- [[_COMMUNITY_Community 31|Community 31]]
- [[_COMMUNITY_Community 32|Community 32]]

## God Nodes (most connected - your core abstractions)
1. `PunishmentConfig` - 68 edges
2. `ZeusPunishmentEngine` - 32 edges
3. `String` - 21 edges
4. `builder()` - 19 edges
5. `ZeusApiClient` - 18 edges
6. `String` - 15 edges
7. `ZeusPunishmentPlugin` - 14 edges
8. `ViolationRecord` - 13 edges
9. `ViolationKey` - 12 edges
10. `QueueState` - 12 edges

## Surprising Connections (you probably didn't know these)
- `toSummaryString()` --references--> `String`  [EXTRACTED]
  ZeusPunishmentJava/src/main/java/org/vennv/zeuspunishment/core/audit/AuditEvent.java → ZeusPunishmentJava/src/main/java/org/vennv/zeuspunishment/core/audit/AuditEvent.java  _Bridges community 2 → community 27_
- `RecordingDispatcher` --implements--> `PunishmentDispatcher`  [EXTRACTED]
  ZeusPunishmentJava/src/test/java/org/vennv/zeuspunishment/core/ZeusPunishmentEngineAckTest.java →   _Bridges community 6 → community 7_
- `RecordingDispatcher` --implements--> `PunishmentDispatcher`  [EXTRACTED]
  ZeusPunishmentJava/src/test/java/org/vennv/zeuspunishment/core/ZeusPunishmentEnginePolicyTest.java →   _Bridges community 7 → community 4_
- `TestDispatcher` --implements--> `PunishmentDispatcher`  [EXTRACTED]
  ZeusPunishmentJava/src/test/java/org/vennv/zeuspunishment/core/ZeusPunishmentEngineStatusTest.java →   _Bridges community 7 → community 11_
- `RecordingDispatcher` --implements--> `PunishmentDispatcher`  [EXTRACTED]
  ZeusPunishmentJava/src/test/java/org/vennv/zeuspunishment/core/ZeusPunishmentEngineWorkflowTest.java →   _Bridges community 7 → community 5_

## Communities (33 total, 5 thin omitted)

### Community 1 - "Community 1"
Cohesion: 0.08
Nodes (18): ViolationKey, QueueState, BanwaveManager, QueueState, BanwaveManagerTest, Override, Severity, String (+10 more)

### Community 2 - "Community 2"
Cohesion: 0.10
Nodes (17): builder(), escape(), safe(), toJsonLine(), Boolean, PunishmentConfigPolicyTest, ZeusPunishmentEnginePolicyTest, Fixture (+9 more)

### Community 3 - "Community 3"
Cohesion: 0.07
Nodes (27): CooldownGate, DedupEntry, ZeusPunishmentEngine, DedupEntry, JSONArray, Map, ViolationRecord, ViolationLog (+19 more)

### Community 4 - "Community 4"
Cohesion: 0.28
Nodes (8): RecordingAudit, RecordingDispatcher, AuditEvent, DispatcherOutcome, Override, Severity, String, ViolationRecord

### Community 5 - "Community 5"
Cohesion: 0.15
Nodes (15): AcknowledgingApiClient, RecordingDispatcher, ZeusPunishmentEngineWorkflowTest, ZeusApiClient, ActionType, BanwaveManager, DispatcherOutcome, List (+7 more)

### Community 6 - "Community 6"
Cohesion: 0.16
Nodes (15): RecordingApiClient, RecordingDispatcher, ZeusPunishmentEngineAckTest, ActionType, BanwaveManager, DispatcherOutcome, List, Override (+7 more)

### Community 7 - "Community 7"
Cohesion: 0.10
Nodes (23): PunishmentDispatcher, FabricDispatcher, BukkitDispatcher, PunishmentDispatcher, RecordingDispatcher, DispatcherOutcome, MinecraftServer, Override (+15 more)

### Community 8 - "Community 8"
Cohesion: 0.15
Nodes (7): Consumer, ZeusApiClient, ApiStatusSnapshot, Consumer, List, String, ViolationRecord

### Community 9 - "Community 9"
Cohesion: 0.11
Nodes (18): authors, contact, homepage, depends, fabric-api, fabricloader, java, minecraft (+10 more)

### Community 10 - "Community 10"
Cohesion: 0.24
Nodes (3): ViolationLog, JSONObject, String

### Community 11 - "Community 11"
Cohesion: 0.07
Nodes (20): Command, CommandExecutor, ZPunishCommand, CommandSender, TestDispatcher, ZeusPunishmentEngineStatusTest, EngineStatusSnapshot, EngineStatusSnapshot (+12 more)

### Community 12 - "Community 12"
Cohesion: 0.14
Nodes (14): ClickType, EventHandler, MenuBuilder, MenuListener, InventoryClickEvent, ItemStack, Listener, Material (+6 more)

### Community 13 - "Community 13"
Cohesion: 0.39
Nodes (3): ZeusApiClientBoundaryTest, String, Test

### Community 15 - "Community 15"
Cohesion: 0.50
Nodes (3): fromString(), Severity(), String

### Community 16 - "Community 16"
Cohesion: 0.50
Nodes (3): fromString(), ActionType, String

### Community 23 - "Community 23"
Cohesion: 0.14
Nodes (11): Category, CooldownGate, Entry, CooldownGateTest, PunishmentConfigBackedWindows, Decision, Duration, LongSupplier (+3 more)

### Community 24 - "Community 24"
Cohesion: 0.15
Nodes (4): PunishmentConfigNetworkTest, Override, Override, Test

### Community 27 - "Community 27"
Cohesion: 0.09
Nodes (16): toSummaryString(), AuditSink, AuditSinkTest, CompositeAuditSink, FileAuditSink, AuditSink, AuditEvent, Consumer (+8 more)

### Community 28 - "Community 28"
Cohesion: 0.50
Nodes (3): record(), AuditEvent, Override

### Community 29 - "Community 29"
Cohesion: 0.20
Nodes (7): PolicyAction, PolicyDecision, Set, ActionType, PolicyAction, PolicyDecision, Severity

### Community 30 - "Community 30"
Cohesion: 0.29
Nodes (3): DispatcherOutcome, Status, String

### Community 31 - "Community 31"
Cohesion: 0.27
Nodes (5): ZeusPunishmentPlugin, JavaPlugin, BanwaveManager, PunishmentConfig, ZeusPunishmentEngine

### Community 32 - "Community 32"
Cohesion: 0.36
Nodes (4): DedicatedServerModInitializer, ZeusPunishmentMod, MinecraftServer, PunishmentConfig

## Knowledge Gaps
- **58 isolated node(s):** `build.sh script`, `PunishmentDispatcher`, `DedupEntry`, `PolicyAction`, `String` (+53 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **5 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `PunishmentConfig` connect `Community 0` to `Community 1`, `Community 2`, `Community 3`, `Community 7`, `Community 12`, `Community 23`, `Community 24`, `Community 26`, `Community 29`?**
  _High betweenness centrality (0.125) - this node is a cross-community bridge._
- **Why does `ZeusPunishmentEngine` connect `Community 3` to `Community 8`, `Community 24`, `Community 2`?**
  _High betweenness centrality (0.075) - this node is a cross-community bridge._
- **Why does `Map` connect `Community 3` to `Community 1`, `Community 2`, `Community 7`, `Community 23`, `Community 29`?**
  _High betweenness centrality (0.041) - this node is a cross-community bridge._
- **What connects `build.sh script`, `PunishmentDispatcher`, `DedupEntry` to the rest of the system?**
  _58 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Community 0` be split into smaller, more focused modules?**
  _Cohesion score 0.12698412698412698 - nodes in this community are weakly interconnected._
- **Should `Community 1` be split into smaller, more focused modules?**
  _Cohesion score 0.07764876632801161 - nodes in this community are weakly interconnected._
- **Should `Community 2` be split into smaller, more focused modules?**
  _Cohesion score 0.09643605870020965 - nodes in this community are weakly interconnected._