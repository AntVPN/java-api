package io.antivpn.api.socket;

import io.antivpn.api.AntiVPN;
import io.antivpn.api.socket.handler.SocketDataHandler;
import io.antivpn.api.util.IDGenerator;
import lombok.Getter;
import lombok.Setter;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class SocketManager {
    private final AntiVPN antiVPN;
    @Getter
    private SocketClient socket;
    @Getter
    private final SocketDataHandler socketDataHandler;
    private final Object socketLock = new Object();
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);
    private final AtomicBoolean runtimeMarkerLogged = new AtomicBoolean(false);
    private final AtomicLong socketGeneration = new AtomicLong(0);
    private Timer timeoutTimer;

    @Getter
    @Setter
    private String responseKick;

    @Getter
    @Setter
    private String shieldKick;

    private volatile long lastPongTimestamp = System.currentTimeMillis();
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
        synchronized (this.socketLock) {
            if (this.socket.isConnected() || this.socket.isConnecting()) return;
            if (this.runtimeMarkerLogged.compareAndSet(false, true)) {
                this.antiVPN.getLog().error("AntiVPN API runtime marker: %s", SocketClient.apiRuntimeMarker());
            }
            this.socket.connect();
            startTimeoutTask();
        }
    }

    /**
     * Closing the socket.
     */
    public void close() {
        synchronized (this.socketLock) {
            stopTimeoutTask();
            if (!this.isConnected()) return;
            this.socket.close(1000, "Closing");
        }
    }

    private SocketClient initialize() {
        var connection_url = URI.create(this.antiVPN.getAntiVPNConfig().getEndpoint());
        Map<String, String> httpHeaders = getHeaders();
        return new SocketClient(this, this.antiVPN, connection_url, httpHeaders, this.socketGeneration.incrementAndGet());
    }

    private void startTimeoutTask() {
        if (this.timeoutTimer != null) return;
        this.timeoutTimer = new Timer(antiVPN.getPluginName() + " - Socket Timeout Checker", true);
        this.timeoutTimer.scheduleAtFixedRate(new SocketTimeoutTask(this), 8000, 8000);
    }

    private void stopTimeoutTask() {
        if (this.timeoutTimer == null) return;
        this.timeoutTimer.cancel();
        this.timeoutTimer = null;
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
        this.lastPongTimestamp = now;
        this.reconnecting.set(false);
    }

    public boolean isCurrent(SocketClient socketClient, long generation) {
        return this.socket == socketClient && this.socketGeneration.get() == generation;
    }


    public void reconnect() {
        reconnect(false);
    }

    public void reconnect(boolean force) {
        synchronized (this.socketLock) {
            if (!force && (this.socket.isConnecting() || this.isConnected())) return;
            if (!this.reconnecting.compareAndSet(false, true)) return;

            SocketClient oldSocket = this.socket;
            this.antiVPN.getLog().log("Closing the AntiVPN Server connection...");
            oldSocket.close();

            this.socket = initialize();
            markConnected();

            this.antiVPN.getLog().error("Reconnecting to the AntiVPN Server...");
            this.socket.connect();
            startTimeoutTask();
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