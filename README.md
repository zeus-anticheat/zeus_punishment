# Zeus Punishment Platform

The Zeus Punishment Platform plugin is a lightweight, high-performance API-consuming enforcement client designed to securely interact with the broader Zeus ecosystem. It consumes Zeus Platform streaming telemetry via SSE (Server-Sent Events) and enforces dynamic policies—bridging the gap between automated detection mechanics and server administration workflows.

This documentation serves both **Server Owners & Administrators** (for setup, policy configuration, and operator commands) and **Developers** (for API boundaries and integration specifics).

---

## 🏗 Architecture Boundary & Disclosure Note

Zeus Punishment acts purely as a consumer of abstract "Zeus Engine" violation alerts. It does not implement detection mechanisms locally, nor does it log, display, or utilize any real AI/ML architecture names (which is strictly prohibited to prevent bypassing). Instead, it enforces actions based on functional tiers (e.g., *Observe, Warn, Kick, Enforce*).

The plugin maintains strict network thread isolation—ensuring that long-lived SSE connections and HTTP payload handling never block the main server thread.

---

## 🚀 Setup & Installation

### Build Requirements
- JDK 17 or higher
- Maven (for Core and Gateway)
- Gradle (for Fabric)

### Compilation

You should compile the core engine first, followed by your specific server platform gateway.

**1. Compile the Core (Java/Maven):**
```bash
cd zeus_punishment/ZeusPunishmentJava
mvn clean install
```

**2. Compile the Spigot/Paper Gateway (Maven):**
```bash
cd zeus_punishment/ZeusPunishmentGateway
mvn clean package
```
*The resulting JAR will be located in the `target/` directory.*

**3. Compile the Fabric Gateway (Gradle):**
```bash
cd zeus_punishment/ZeusPunishmentFabric
./gradlew build
```
*The resulting JAR will be located in the `build/libs/` directory.*

*Note: General modern Fabric support is intended, but you must verify compatibility with your exact Fabric API and Minecraft version.*

---

## ⚙️ Detailed Configuration Guide

The primary configuration resides in `config.yml`. The plugin allows you to seamlessly configure policies without reloading the entire server.

### Key Settings:

- **`enforcementEnabled`**: (Boolean) Master switch for taking action. If `false`, the plugin will consume events and log them but will not kick or ban players.
- **`devMode`** / **`dryRun`**: (Boolean) Developer tools. When enabled, actions are simulated and broadcast to admins with the `zpunish.admin` permission, rather than executed.
- **Policies**: Define functional tiers based on violation severity scores.
  - **Observe**: Merely logs the event.
  - **Warn**: Sends a warning message to the offending player.
  - **Kick**: Disconnects the player immediately.
  - **Enforce (Banwave)**: Flags the user for the next scheduled banwave.

*Example snippet:*
```yaml
enforcementEnabled: true
dryRun: false

policies:
  observe_threshold: 50
  warn_threshold: 75
  kick_threshold: 90
  enforce_threshold: 100
```

---

## 🛡️ Operator Commands & Permissions

The primary command is `/zpunish`. All administrative subcommands require the `zpunish.admin` permission.

| Command | Description | Permission |
| :--- | :--- | :--- |
| `/zpunish status` | Displays current SSE connection status, API health, and queued enforcement tasks. | `zpunish.admin` |
| `/zpunish reload` | Hot-reloads `config.yml` without dropping the SSE connection. | `zpunish.admin` |
| `/zpunish reconnect` | Forcibly drops and re-establishes the SSE stream to the Zeus Platform. | `zpunish.admin` |
| `/zpunish verbose` | Toggles verbose debugging in the console for incoming violation events. | `zpunish.admin` |
| `/zpunish gui` | Opens the interactive Operator GUI to manage the Banwave queue. | `zpunish.admin` |
| `/zpunish banwave [execute/cancel]` | Instantly executes or clears the current pending banwave queue. | `zpunish.admin` |

### Operator GUI & Banwave Control
The `/zpunish gui` command opens a visual inventory menu. From here, operators can view the number of players pending enforcement, review specific abstract violation scores, and manually authorize or cancel a banwave. The banwave queue intelligently batches disconnects to prevent server lag spikes.

---

## 🛠️ Troubleshooting & Network Configuration

Due to the persistent nature of SSE (Server-Sent Events) used to receive violations, specific network tuning may be required.

### 1. Proxy Buffering Requirements (Nginx)
If you place the Zeus Platform backend behind a reverse proxy, you **must disable proxy buffering**. Buffered proxies will hold SSE packets until the buffer is full, causing delayed or seemingly "dropped" violation events.

**Nginx Configuration Example:**
```nginx
location /api/public/violations/stream {
    proxy_pass http://zeus-backend;
    proxy_http_version 1.1;
    proxy_set_header Connection "";
    proxy_buffering off;
    proxy_cache off;
    proxy_read_timeout 86400s;
}
```

### 2. SSE Reconnection Behavior
The plugin features an exponential backoff reconnect strategy. If `/zpunish status` shows `DISCONNECTED`, check your proxy headers and API key validity. You can manually force a restart via `/zpunish reconnect`.

### 3. Thread Dumps for Blocking/Leaks
The SSE client operates on a dedicated async thread. If you notice main-thread lag or suspect the SSE client is leaking threads, capture a thread dump:
- **Paper/Spigot:** Run `/timings paste` (or `/spark profiler`) and review the thread tree.
- **Direct Java:** Run `jstack <pid> > threaddump.txt` from the command line.
Report the findings to the developers, noting specifically the `Zeus-SSE-Client` thread state.

### 4. API Error Code Mappings
- **401 Unauthorized:** Invalid or missing API key.
- **403 Forbidden:** The API key lacks permission to access the violation stream.
- **404 Not Found:** Ensure you are hitting the correct `/api/public/violations/stream` endpoint.
- **429 Too Many Requests:** The platform is rate-limiting reconnection attempts.

---

## 💻 Developer & API Integration Boundary

Developers can extend or intercept Zeus Punishment behaviors via established API boundaries.

### Event Hooks
The plugin fires platform-specific events (e.g., `ZeusViolationEvent` on Bukkit) when an alert is successfully parsed from the SSE stream and acknowledged. 

### Integration Boundary (SSE Stream & Payload)
The plugin expects a standardized JSON payload from the Zeus Platform. Real AI architecture models are abstracted out; the payload guarantees:
- `player_uuid`: UUID of the offender.
- `severity_score`: Functional integer determining the policy tier.
- `abstract_reason`: A sanitized string (e.g., "Zeus Engine: Movement Desync").

*Payload Example:*
```json
{
  "event_id": "req_987654",
  "player_uuid": "00000000-0000-0000-0000-000000000000",
  "severity_score": 85,
  "abstract_reason": "Zeus Engine Alert: Delta-V Anomaly"
}
```

*Ensure that any mock backends strictly adhere to these payload boundaries and omit proprietary detection signals.*