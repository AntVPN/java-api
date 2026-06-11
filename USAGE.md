# ServerAntiVPN Java Client Usage Guide

This guide covers how to integrate the `serverantivpn-api` Java client into your application to verify IP addresses in real-time via WebSocket.

---

## Installation

### Maven

Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>io.antivpn</groupId>
    <artifactId>serverantivpn-api</artifactId>
    <version>1.1.0-RELEASE</version>
</dependency>
```

### Requirements

- **Java 17** or higher
- Dependencies are **shaded** into the artifact — you do not need to add `gson` or `guava` manually.

---

## Quick Start

### 1. Create a Configuration

Use the builder-style methods on `AntiVPNConfig`:

```java
import io.antivpn.api.config.AntiVPNConfig;

AntiVPNConfig config = AntiVPNConfig.create()
    .withApiKey("your-api-key-here")
    .withEndpoint("wss://api.antivpn.io/connect")
    .withUserAgent("MyPlugin/1.0")
    .withDebug(false)
    .withLevel(java.util.logging.Level.INFO);
```

### 2. Implement the Logger

Provide a `VPNLogger` implementation. The interface has four methods:

```java
import io.antivpn.api.logging.VPNLogger;

public class MyLogger implements VPNLogger {
    @Override
    public void log(String message, Object... args) {
        System.out.printf("[INFO] " + message + "%n", args);
    }

    @Override
    public void fine(String message, Object... args) {
        System.out.printf("[FINE] " + message + "%n", args);
    }

    @Override
    public void error(String message, Object... args) {
        System.err.printf("[ERROR] " + message + "%n", args);
    }

    @Override
    public void debug(String message, Object... args) {
        System.out.printf("[DEBUG] " + message + "%n", args);
    }
}
```

### 3. Create and Start the Client

```java
import io.antivpn.api.AntiVPN;
import java.time.Duration;

AntiVPN antiVPN = AntiVPN.create(
    "MyPlugin",
    new MyLogger(),
    config,
    Duration.ofMinutes(5)
);

antiVPN.start(); // validates config and opens the WebSocket
```

---

## Core Usage

### IP Verification

Send a `CheckRequest` to verify whether an IP address is using a VPN or proxy. The API returns a `CompletableFuture<CheckResponse>` so you can handle the result asynchronously.

```java
import io.antivpn.api.model.request.CheckRequest;
import io.antivpn.api.model.response.CheckResponse;

CheckRequest request = new CheckRequest(
    "192.0.2.1",   // player IP
    "uuid-123",    // user ID
    "PlayerName"   // username
);

CompletableFuture<CheckResponse> future =
    antiVPN.getSocketManager().getSocketDataHandler().verify(request);

if (future != null) {
    future.thenAccept(response -> {
        if (!response.isValid()) {
            System.out.println("VPN detected! " + response.getKickMessage());
        }
        if (response.isAttack()) {
            System.out.println("Attack flagged for IP: " + response.getIp());
        }
    }).exceptionally(ex -> {
        System.err.println("Check failed: " + ex.getMessage());
        return null;
    });
}
```

### CheckResponse Fields

| Field | Type | Description |
|-------|------|-------------|
| `transactionalId` | `String` | Unique ID for this request |
| `sessionId` | `String` | Session identifier |
| `username` | `String` | Player username |
| `ip` | `String` | IP address checked |
| `country` | `String` | Detected country |
| `kickMessage` | `String` | Message to show if the check fails |
| `valid` | `boolean` | `true` if the IP is clean, `false` if VPN/proxy detected |
| `isAttack` | `boolean` | `true` if the request was flagged as an attack |

### Sending User Data Events

You can send player lifecycle events to the AntiVPN server for analytics and session tracking.

```java
import io.antivpn.api.model.request.UserData;
import io.antivpn.api.util.Event;

UserData data = UserData.builder()
    .sessionId("session-abc")
    .username("PlayerName")
    .userId("uuid-123")
    .version("1.20.1")
    .address("192.0.2.1")
    .hostname("lobby-1")
    .server("main")
    .event(Event.PLAYER_JOIN)
    .premium(true)
    .build();

antiVPN.getSocketManager().getSocketDataHandler().sendUserData(data);
```

**Supported events:** `PLAYER_JOIN`, `PLAYER_SWITCH`, `PLAYER_QUIT`

---

## Configuration Reference

### AntiVPNConfig Options

| Method | Default | Description |
|--------|---------|-------------|
| `withApiKey(String)` | `"your-api-key-here"` | Your AntiVPN API key |
| `withEndpoint(String)` | `"wss://api.antivpn.io/connect"` | WebSocket endpoint URI |
| `withUserAgent(String)` | `null` | Custom user-agent (falls back to plugin name) |
| `withDebug(boolean)` | `false` | Enable debug output |
| `withLevel(Level)` | `Level.FINE` | Minimum Java logging level |

### Validation

Calling `antiVPN.start()` (or `config.validate()` directly) will throw an `IllegalStateException` if:

- The API key is missing, blank, or still set to the placeholder `"your-api-key-here"`
- The endpoint is not a valid URI

---

## Advanced Topics

### Runtime Config Reloading

You can hot-swap the configuration without restarting your application:

```java
AntiVPNConfig newConfig = AntiVPNConfig.create()
    .withApiKey("new-api-key")
    .withEndpoint("wss://api.antivpn.io/connect");

antiVPN.reload(newConfig);
```

**Reconnect behavior:** If the `apiKey`, `endpoint`, or `userAgent` changed, the socket is safely closed and automatically reconnected. Otherwise, the config is swapped in-place with no interruption.

### Custom Kick Messages

The server provides default kick messages, but you can read or override them via the `SocketManager`:

```java
String vpnKick = antiVPN.getSocketManager().getResponseKick();
String shieldKick = antiVPN.getSocketManager().getShieldKick();

// You can also set your own defaults before the server sends its SETTINGS:
antiVPN.getSocketManager().setResponseKick("Custom VPN kick message");
antiVPN.getSocketManager().setShieldKick("Custom shield message");
```

### Cache Tuning

The `cacheDuration` passed to `AntiVPN.create(...)` controls how long successful check results are cached per IP address. A longer duration reduces API calls but delays detection of new VPNs on the same IP.

```java
// Cache results for 10 minutes
AntiVPN.create("MyPlugin", logger, config, Duration.ofMinutes(10));
```

Internally, a second cache (20 seconds) tracks in-flight requests so duplicate checks for the same player reuse the same `CompletableFuture`.

### Connection Lifecycle

```java
boolean connected = antiVPN.getSocketManager().isConnected();

// Manual reconnect (useful after network errors)
antiVPN.getSocketManager().reconnect();

// Graceful shutdown
antiVPN.getSocketManager().close();
```

### Error Handling

- **Timeouts:** If a request is not answered within ~20 seconds, the future completes exceptionally with `RequestTimeoutException`.
- **Null on disconnect:** `verify()` returns `null` if the socket is not connected. Always check before chaining:

```java
CompletableFuture<CheckResponse> future =
    antiVPN.getSocketManager().getSocketDataHandler().verify(request);

if (future == null) {
    System.err.println("AntiVPN socket is not connected.");
} else {
    future.thenAccept(...);
}
```

---

## Complete Example

```java
import io.antivpn.api.AntiVPN;
import io.antivpn.api.config.AntiVPNConfig;
import io.antivpn.api.logging.VPNLogger;
import io.antivpn.api.model.request.CheckRequest;
import io.antivpn.api.model.request.UserData;
import io.antivpn.api.model.response.CheckResponse;
import io.antivpn.api.util.Event;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class AntiVPNExample {

    public static void main(String[] args) {
        // 1. Configure
        AntiVPNConfig config = AntiVPNConfig.create()
            .withApiKey("your-api-key-here")
            .withEndpoint("wss://api.antivpn.io/connect")
            .withDebug(true);

        // 2. Create client
        AntiVPN antiVPN = AntiVPN.create(
            "MyApplication",
            new SimpleLogger(),
            config,
            Duration.ofMinutes(5)
        );

        // 3. Start
        antiVPN.start();

        // 4. Verify an IP
        CheckRequest check = new CheckRequest("192.0.2.1", "user-123", "Steve");
        CompletableFuture<CheckResponse> future =
            antiVPN.getSocketManager().getSocketDataHandler().verify(check);

        if (future != null) {
            future.thenAccept(response -> {
                System.out.println("Result: valid=" + response.isValid() +
                                   ", attack=" + response.isAttack());
            }).exceptionally(ex -> {
                System.err.println("Verification failed: " + ex.getMessage());
                return null;
            });
        }

        // 5. Send a player join event
        UserData userData = UserData.builder()
            .sessionId("session-1")
            .username("Steve")
            .userId("user-123")
            .version("1.20.1")
            .address("192.0.2.1")
            .hostname("lobby")
            .server("main")
            .event(Event.PLAYER_JOIN)
            .premium(false)
            .build();

        antiVPN.getSocketManager().getSocketDataHandler().sendUserData(userData);
    }

    static class SimpleLogger implements VPNLogger {
        @Override public void log(String m, Object... a) { System.out.printf(m + "%n", a); }
        @Override public void fine(String m, Object... a) { /* no-op */ }
        @Override public void error(String m, Object... a) { System.err.printf(m + "%n", a); }
        @Override public void debug(String m, Object... a) { System.out.printf("[DEBUG] " + m + "%n", a); }
    }
}
```

---

## Additional Resources

- Full code examples and additional languages are available in the [official wiki](https://docs.antivpn.io/developers/realtime-java-api).
- For bug reports or feature requests, see the repository's [Issues](https://github.com/your-org/java-api/issues) page.
