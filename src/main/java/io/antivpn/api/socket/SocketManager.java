package io.antivpn.api.socket;

import io.antivpn.api.AntiVPN;
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

public class SocketManager  {
    private final AntiVPN antiVPN;
    @Getter
    private final SocketClient socket;
    @Getter
    private final SocketDataHandler socketDataHandler;

    @Getter
    @Setter
    private String responseKick;

    public SocketManager(AntiVPN antiVPN, Duration cacheDuration) {
        this.antiVPN = antiVPN;
        this.socket = initialize();
        this.socketDataHandler = new SocketDataHandler(this, cacheDuration);
        this.responseKick = "§cVPN Detected!\n" +
                "§cPlease disable your VPN and rejoin.\n" +
                "§cIf you believe this is a mistake, please contact an administrator.";
    }

    public void connect() {
        if (this.isConnected()) return;
        this.socket.connect();
        Timer timer = new Timer(antiVPN.getPluginName() + " - Socket Timeout Checker");
        timer.scheduleAtFixedRate(new SocketTimeoutTask(), 0, 8000);
    }

    /**
     * Closing the socket.
     */
    public void close() {
        if (this.isConnected()) return;
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
                this.antiVPN.getConsole().error("Failed to authenticate with the server, please check your secret in the config.json file.");
            } else if (statusCode >= 500 && statusCode <= 505) {
                this.antiVPN.getConsole().error("Our server is restarting or something related... If this still happening after 10 minutes please report it on discord.snake.rip. Useful data: (HttpStatus: %s)", statusCode);
            } else {
                this.antiVPN.getConsole().error("Report this to the developer: %s", throwable.getClass().getSimpleName());
                throwable.printStackTrace();
            }

            return null;
        }
    }

    public boolean isConnected() {
        if (this.socket == null) return false;
        return this.socket.isConnected();
    }

    public void sendPing() {
        if (!this.isConnected()) return;
        this.socket.sendPing();
    }

    public void reconnect() {
        this.antiVPN.getConsole().log("Closing the AntiVPN Server connection...");
        this.socket.close();

        if (this.socket.isConnecting() || this.isConnected()) return;
        this.socket.clearHeaders();

        getHeaders().forEach(this.socket::addHeader);

        this.antiVPN.getConsole().error("Reconnecting to the AntiVPN Server...");
        this.socket.reconnect();
    }

    public Map<String, String> getHeaders() {
        Map<String, String> httpHeaders = new HashMap<>();

        httpHeaders.put("User-Agent", this.antiVPN.getPluginName());
        httpHeaders.put("Authorization", "Bearer " + this.antiVPN.getAntiVPNConfig().getApiKey());

        return httpHeaders;
    }
}