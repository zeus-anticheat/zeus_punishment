# Phase 08: Documentation, Verification, and Push Readiness - Research

**Researched:** 2026-06-02
**Domain:** Documentation, Testing, and Build Verification
**Confidence:** HIGH

## Summary
The goal of Phase 8 is to verify the build process for `zeus_punishment` (especially Fabric compatibility) and prepare documentation (README/Wiki) based on the commands, permissions, and configurations introduced in previous phases. We discovered build/compilation issues within `ZeusPunishmentFabric` due to the local `ZeusPunishmentJava` dependency resolution, as well as a failing unit test in `ZeusApiClientBoundaryTest.java`. 

**Primary recommendation:** Fix the constants in `ZeusApiClient` to restore test boundaries, install the Java Core to local Maven repository so the Fabric adapter can compile successfully, and document the new features effectively.

## 1. Test Failure Investigation

**Test Failing:** `ZeusApiClientBoundaryTest.java` in `ZeusPunishmentJava`.
**Issue:** The test uses reflection to check for public constants:
- `PUBLIC_VIOLATIONS_PATH` expecting `/api/public/violations`
- `PUBLIC_VIOLATIONS_STREAM_PATH` expecting `/api/public/violations/stream`
- `PUBLIC_LIST_MODELS_PATH` expecting `/api/public/list_models`

Currently, `ZeusApiClient.java` uses inline string literals (e.g. `"/api/public/violations/stream"`) and doesn't define the expected public constants. Furthermore, `acknowledgeViolations(java.util.List.class)` passes with an empty list but incorrectly returns true when passed a non-empty list because `clearViolations` does not propagate false returns when the API is not mocked correctly or when the endpoint (`/api/public/violations/delete`) is wrong (it should match `PUBLIC_VIOLATIONS_PATH`).

**Resolution:**
In `ZeusApiClient.java`, define the expected constants explicitly:
```java
public static final String PUBLIC_VIOLATIONS_PATH = "/api/public/violations";
public static final String PUBLIC_VIOLATIONS_STREAM_PATH = "/api/public/violations/stream";
public static final String PUBLIC_LIST_MODELS_PATH = "/api/public/list_models";
```
Update the API calls in `ZeusApiClient.java` to use these constants. Also, ensure the return value of `acknowledgeViolations` correctly represents the success of `clearViolations`.

## 2. Fabric Module Compilation Analysis

**Module:** `ZeusPunishmentFabric`
**Error:** Initially `gradle build` failed because it could not resolve `org.vennv.zeuspunishment.core.model.DispatcherOutcome` and other Core symbols.
**Reason:** The Fabric module depends on `org.vennv:ZeusPunishmentJava:1.0-SNAPSHOT` using `mavenLocal()`. By default, unless the Maven project `ZeusPunishmentJava` is built and installed locally, Gradle cannot find it. 
**Verification:**
After running `mvn clean install -DskipTests` inside the `zeus_punishment` folder, the Fabric module compiled successfully via `gradle build`. 
**Action item:** Add instructions in the README on how to correctly compile the project (building the Maven parent/core first before the Fabric module).

## 3. Documentation Strategy (README & User-facing Text)

The documentation should detail the commands, permissions, and configurations added in Phases 5-7. To maintain a disclosure-safe environment, "cheat detection" logic is abstracted into "policy action" and "stream events".

### Commands & Permissions
- `/zpunish status` - Show cached health and policy state.
- `/zpunish reload` - Validate and reload configuration.
- `/zpunish reconnect` - Restart the violation stream.
- `/zpunish verbose` - Toggle verbose mode.
- `/zpunish gui` - Open the operator GUI to manage the queue.
- `/zpunish banwave list|details <key>|execute all|cancel <key>|clear|pause|resume` - Control queued reviews.

**Permission:** `zpunish.admin` is required for all commands.

### Configurations
Key configurations that need documentation:
- `endpointUrl`: URL of the punishment API (e.g. SSE stream).
- `policyPreset`: `observe`, `warn`, `kick`, `review`, `enforce`.
- `devMode`, `devVerboseMode`: Development and testing.
- `enforcementEnabled`, `dryRun`: Whether punishments are applied or just logged.
- `banwaveEnabled`, `banwaveCountdownStartSeconds`: Setup for banwave queues.

### GUI Controls
The GUI (`/zpunish gui`) allows administrators to:
- Reload configurations.
- View cached status.
- Pause/Resume Banwave Review.
- Execute Queued Reviews.
- Clear the Queue.
- Inspect/cancel specific queued entries.

### Troubleshooting Guides
1. **SSE Reconnects:** If the stream fails, it uses exponential backoff to reconnect. Use `/zpunish status` to view backoff ms and last error. `/zpunish reconnect` forces a restart.
2. **Proxy Buffering Settings:** SSE streams require proxy buffering to be turned off (e.g., in Nginx, `proxy_buffering off;`) to receive real-time events.
3. **Compilation Issues:** Ensure you run `mvn clean install` on the parent repository before running Gradle for Fabric.

## Code Examples (Test Fix)
```java
// In ZeusApiClient.java
public static final String PUBLIC_VIOLATIONS_PATH = "/api/public/violations";
public static final String PUBLIC_VIOLATIONS_STREAM_PATH = "/api/public/violations/stream";
public static final String PUBLIC_LIST_MODELS_PATH = "/api/public/list_models";

// Use constants in methods:
URL url = new URL(baseUrl + PUBLIC_VIOLATIONS_STREAM_PATH);
```