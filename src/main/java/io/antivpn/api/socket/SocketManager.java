package io.antivpn.api.socket;

import io.antivpn.api.AntiVPN;
import io.antivpn.api.socket.handler.SocketDataHandler;
import io.antivpn.api.util.IDGenerator;
import lombok.Getter;
import lombok.Setter;
import org.java_websocket.framing.CloseFrame;

import java.net.URI;
import java.net.http.WebSocketHandshakeException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class SocketManager {
    private final AntiVPN antiVPN;
    @Getter
    private SocketClient socket;
    @Getter
    private final SocketDataHandler socketDataHandler;

    @Getter
    @Setter
    private String responseKick;

    @Getter
    @Setter
    private String shieldKick;

    private volatile long lastPongTimestamp = System.currentTimeMillis();
    private volatile long connectedAt = System.currentTimeMillis();
    private volatile long maxConnectionAgeMs = randomMaxConnectionAge();
    private static final long PONG_TIMEOUT_MS = 150_000L;

    public SocketManager(AntiVPN antiVPN, Duration cacheDuration) {
        this.antiVPN = antiVPN;
        this.socket = initialize();
        this.socketDataHandler = new SocketDataHandler(this, cacheDuration);
        this.responseKick = "§cVPN Detected!\n" +
                "§cPlease disable your VPN and rejoin.\n" +
                "§cIf you believe this is a mistake, please contact an administrator.";
        this.shieldKick = "§cShield is enabled!\n" +
                "§cPlease wait a couple minutes before joining.\n" +
                "§cIf you believe this is a mistake, please contact an administrator.";
    }

    public void connect() {
        if (this.isConnected()) return;
        this.socket.connect();
        Timer timer = new Timer(antiVPN.getPluginName() + " - Socket Timeout Checker");
        timer.scheduleAtFixedRate(new SocketTimeoutTask(this), 0, 8000);
    }

    /**
     * Closing the socket.
     */
    public void close() {
        if (!this.isConnected()) return;
        this.socket.close(CloseFrame.NORMAL, "Closing");
    }

    private SocketClient initialize() {
        try {
            var connection_url = URI.create(this.antiVPN.getAntiVPNConfig().getEndpoint());

            Map<String, String> httpHeaders = getHeaders();
            return new SocketClient(this, this.antiVPN, connection_url, httpHeaders);
        } catch (CompletionException ex) {
            if (!(ex.getCause() instanceof WebSocketHandshakeException)) return null;

            WebSocketHandshakeException throwable = (WebSocketHandshakeException) ex.getCause();
            int statusCode = throwable.getResponse().statusCode();

            if (statusCode == 401) {
                this.antiVPN.getLog().error("Failed to authenticate with the server, please check your secret in the config.json file.");
            } else if (statusCode >= 500 && statusCode <= 505) {
                this.antiVPN.getLog().error("Our server is restarting or something related... If this still happening after 10 minutes please report it on discord.snake.rip. Useful data: (HttpStatus: %s)", statusCode);
            } else {
                this.antiVPN.getLog().error("Report this to the developer: %s", throwable.getClass().getSimpleName());
                throwable.printStackTrace();
            }

            return null;
        }
    }

    public boolean isConnected() {
        if (this.socket == null) return false;
        return this.socket.isConnected();
    }

    public void sendKeepAlive() {
        if (!this.isConnected()) return;
        String nonce = IDGenerator.generateUniqueID();
        this.antiVPN.getLog().debug("Sending JSON PING keepalive [nonce=%s]", nonce);
        this.socket.send("{\"type\":\"PING\",\"nonce\":\"" + nonce + "\"}");
    }

    public void markPongReceived() {
        this.lastPongTimestamp = System.currentTimeMillis();
    }

    public boolean isPongStale() {
        return (System.currentTimeMillis() - this.lastPongTimestamp) > PONG_TIMEOUT_MS;
    }

    public void markConnected() {
        long now = System.currentTimeMillis();
        this.connectedAt = now;
        this.lastPongTimestamp = now;
        this.maxConnectionAgeMs = randomMaxConnectionAge();
    }

    public boolean shouldRefreshConnection() {
        return (System.currentTimeMillis() - this.connectedAt) >= this.maxConnectionAgeMs;
    }

    private static long randomMaxConnectionAge() {
        return ThreadLocalRandom.current().nextLong(165_000L, 196_000L);
    }


    public void reconnect() {
        reconnect(false);
    }

    public void reconnect(boolean force) {
        this.antiVPN.getLog().log("Closing the AntiVPN Server connection...");
        this.socket.close();

        if (!force && (this.socket.isConnecting() || this.isConnected())) return;

        this.socket = initialize();
        if (this.socket == null) {
            this.antiVPN.getLog().error("Failed to initialize socket during reconnect.");
            return;
        }

        markConnected();

        this.antiVPN.getLog().error("Reconnecting to the AntiVPN Server...");
        this.socket.connect();
    }

    public void refreshConnection() {
        SocketClient oldSocket = this.socket;
        SocketClient newSocket = initialize();
        if (newSocket == null) {
            this.antiVPN.getLog().error("Failed to initialize socket during connection refresh.");
            return;
        }

        try {
            this.antiVPN.getLog().debug("Refreshing AntiVPN Server connection...");
            if (!newSocket.connectBlocking(10, TimeUnit.SECONDS)) {
                this.antiVPN.getLog().error("Timed out refreshing AntiVPN Server connection.");
                newSocket.close();
                return;
            }

            this.socket = newSocket;
            markConnected();

            if (oldSocket != null && oldSocket.isOpen()) {
                oldSocket.close(CloseFrame.NORMAL, "Refreshing");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            newSocket.close();
            this.antiVPN.getLog().error("Interrupted while refreshing AntiVPN Server connection.");
        } catch (Exception e) {
            newSocket.close();
            this.antiVPN.getLog().error("Failed to refresh AntiVPN Server connection: %s", e.getMessage());
        }
    }

    public Map<String, String> getHeaders() {
        Map<String, String> httpHeaders = new HashMap<>();

        String userAgent = this.antiVPN.getAntiVPNConfig().getUserAgent();
        httpHeaders.put("User-Agent", userAgent != null ? userAgent : this.antiVPN.getPluginName());
        httpHeaders.put("Authorization", "Bearer " + this.antiVPN.getAntiVPNConfig().getApiKey());

        return httpHeaders;
    }
}