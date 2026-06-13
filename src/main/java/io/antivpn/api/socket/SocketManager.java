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

    /**
     * Timestamp (ms) of the last JSON PONG received from the server. Updated on every PONG and on
     * a fresh connection open. Used by {@link SocketTimeoutTask} to detect half-open connections
     * (TCP alive but server unreachable, common behind Cloudflare) that {@code isOpen()} cannot see.
     */
    private volatile long lastPongTimestamp = System.currentTimeMillis();

    /**
     * Max time (ms) without a JSON PONG before the connection is considered dead and force-reconnected.
     * Keepalive is sent every ~56s, so this allows ~2 missed PONGs before acting.
     */
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

    /**
     * Records that a JSON PONG (or a fresh connection) was just received, resetting the dead-connection clock.
     */
    public void markPongReceived() {
        this.lastPongTimestamp = System.currentTimeMillis();
    }

    /**
     * @return true if no JSON PONG has been received within {@link #PONG_TIMEOUT_MS}, i.e. the
     * connection is most likely half-open/dead even though {@code isOpen()} still reports true.
     */
    public boolean isPongStale() {
        return (System.currentTimeMillis() - this.lastPongTimestamp) > PONG_TIMEOUT_MS;
    }


    public void reconnect() {
        reconnect(false);
    }

    public void reconnect(boolean force) {
        this.antiVPN.getLog().log("Closing the AntiVPN Server connection...");
        this.socket.close();

        if (!force && (this.socket.isConnecting() || this.isConnected())) return;

        // Create a fresh SocketClient so no stale Java-WebSocket timer state survives.
        this.socket = initialize();
        if (this.socket == null) {
            this.antiVPN.getLog().error("Failed to initialize socket during reconnect.");
            return;
        }

        // Reset the keepalive clock for the new connection so the stale-detection in
        // SocketTimeoutTask doesn't immediately fire again while the handshake is in progress.
        markPongReceived();

        this.antiVPN.getLog().error("Reconnecting to the AntiVPN Server...");
        this.socket.connect();
    }

    public Map<String, String> getHeaders() {
        Map<String, String> httpHeaders = new HashMap<>();

        String userAgent = this.antiVPN.getAntiVPNConfig().getUserAgent();
        httpHeaders.put("User-Agent", userAgent != null ? userAgent : this.antiVPN.getPluginName());
        httpHeaders.put("Authorization", "Bearer " + this.antiVPN.getAntiVPNConfig().getApiKey());

        return httpHeaders;
    }
}