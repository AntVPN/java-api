package io.antivpn.api.socket;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.antivpn.api.AntiVPN;
import lombok.Getter;
import io.antivpn.api.model.response.CheckResponse;
import io.antivpn.api.model.response.SettingsResponse;
import io.antivpn.api.util.GsonParser;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.enums.ReadyState;
import org.java_websocket.handshake.ServerHandshake;

import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.channels.UnresolvedAddressException;
import java.util.Map;

public class SocketClient extends WebSocketClient {
    private final SocketManager socketManager;
    @Getter
    private final AntiVPN antiVPN;

    public SocketClient(SocketManager socketManager, AntiVPN antiVPN, URI serverUri, Map<String, String> httpHeaders) {
        super(serverUri, httpHeaders);
        this.socketManager = socketManager;
        this.antiVPN = antiVPN;
        this.setTcpNoDelay(true);
        // Disable library-level ping/pong; we use our own JSON keepalive in SocketTimeoutTask.
        // The server replies to JSON PINGs but not to WebSocket control-frame PINGs, which
        // caused Code: 1006 "did not respond with a pong in time" disconnects.
        this.setConnectionLostTimeout(0);
    }

    /**
     * Hard-disable Java-WebSocket's native lost-connection detection.
     *
     * Java-WebSocket's {@code WebSocketClient.onWebsocketOpen()} unconditionally calls
     * {@code startConnectionLostTimer()} BEFORE our {@link #onOpen} runs and re-arms it on every
     * reconnect, which is why {@code setConnectionLostTimeout(0)} alone was unreliable and we kept
     * getting Code: 1006 "did not respond with a pong in time" disconnects. The server (behind
     * Cloudflare) only speaks our JSON PING/PONG, never WebSocket control-frame PONGs, so the
     * native watchdog always falsely flags the link as dead.
     *
     * Overriding this to a no-op guarantees the native timer can never start. We manage keepalive
     * and dead-connection detection ourselves in {@code SocketTimeoutTask} / {@link SocketManager}.
     */
    @Override
    protected void startConnectionLostTimer() {
        // Intentionally empty: keepalive is fully self-managed via JSON PING/PONG.
        // Proof-of-patch marker: if you see this line in the logs, the native watchdog is disabled
        // and any subsequent 1006 "did not respond with a pong in time" can only come from OLD code.
        this.antiVPN.getLog().debug("Native lost-connection watchdog suppressed; using self-managed JSON keepalive.");
    }

    @Override
    public void onWebsocketPong(org.java_websocket.WebSocket conn, org.java_websocket.framing.Framedata f) {
        // Native control-frame PONGs are not used; ignore them entirely.
    }

    @Override
    public void onMessage(String message) {
        this.antiVPN.getLog().debug("Received message from the AntiVPN Server: %s", message);
        try {
            JsonObject object = GsonParser.parse(message);
            JsonElement typeElement = object.get("type");

            if (typeElement == null) {
                this.antiVPN.getLog().error("Received invalid message (missing 'type'): %s", message);
                return;
            }

            String type = typeElement.getAsString().toUpperCase();

            switch (type) {
                case "SETTINGS":
                    JsonObject settingsObject = object.getAsJsonObject("settings");
                    // Use the already parsed JsonObject instead of a String
                    SettingsResponse response = GsonParser.fromJson(settingsObject, SettingsResponse.class);

                    this.socketManager.setResponseKick(response.getKickMessage().replace("\r", ""));
                    this.socketManager.setShieldKick(response.getShieldMode().replace("\r", ""));
                    this.antiVPN.getLog().fine("Received settings from the AntiVPN Server.");
                    break;

                case "VERIFY":
                    // Avoid parsing the full string a second time, pass the JsonObject directly
                    CheckResponse checkResponse = GsonParser.fromJson(object, CheckResponse.class);
                    this.socketManager.getSocketDataHandler().handle(checkResponse);
                    break;

                case "PONG":
                    this.socketManager.markPongReceived();
                    this.antiVPN.getLog().debug("Received JSON PONG keepalive from server.");
                    break;

                default:
                    this.antiVPN.getLog().error("Received unknown message type '%s': %s", type, message);
                    break;
            }
        } catch (Exception e) {
            this.antiVPN.getLog().error("Failed to parse message from AntiVPN Server: %s | Error: %s", message, e.getMessage());
        }
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        // Defensive: Java-WebSocket may start the lost-connection timer during handshake.
        // Calling this here cancels any timer that was started with a stale/default timeout.
        this.setConnectionLostTimeout(0);
        // Reset our self-managed keepalive clock so a fresh connection isn't immediately
        // flagged as stale before the first JSON PONG arrives.
        this.socketManager.markPongReceived();
        this.antiVPN.getLog().fine("Connected to the AntiVPN Server.");
        this.antiVPN.getLog().debug("WebSocket handshake complete. Status: %d | Url: %s", handshake.getHttpStatus(), this.uri);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        // Halt any leftover connection-lost timer so it cannot fire on a reused/recreated socket.
        this.setConnectionLostTimeout(0);

        String readableReason = (reason == null || reason.isEmpty()) ? getReadableCloseReason(code) : reason;
        String initiator = remote ? "Server" : "Client";

        this.antiVPN.getLog().debug("onClose triggered [initiator=%s, code=%d, rawReason=%s]", initiator, code, reason);
        this.antiVPN.getLog().error("Disconnected from AntiVPN Server [%s]. (Code: %d, Reason: %s)", initiator, code, readableReason);
    }

    @Override
    public void onError(Exception e) {
        String errorMsg;

        // Specific handling of common network errors for better console clarity
        if (e instanceof ConnectException) {
            errorMsg = "Connection refused. The AntiVPN socket server might be offline or blocked by a firewall.";
        } else if (e instanceof UnknownHostException || e instanceof UnresolvedAddressException) {
            errorMsg = "Unknown host. Please check your server URI or DNS settings.";
        } else {
            errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            // Only print the stacktrace if it's an unexpected error
            e.printStackTrace();
        }

        this.antiVPN.getLog().error("Socket connection error: %s", errorMsg);
    }

    /**
     * Returns true if the socket is currently in the process of connecting.
     */
    public boolean isConnecting() {
        return this.getReadyState() == ReadyState.NOT_YET_CONNECTED;
    }

    /**
     * Returns true if the socket is fully connected and open.
     */
    public boolean isConnected() {
        return this.isOpen(); // isOpen() safely validates the state
    }

    /**
     * Translates standard WebSocket close codes to human-readable text.
     */
    private String getReadableCloseReason(int code) {
        switch (code) {
            case 1000: return "Normal Closure";
            case 1001: return "Going Away (Server Restarting)";
            case 1005: return "No Status Received";
            case 1006: return "Abnormal Closure (Network Drop / Connection Refused)";
            case 1011: return "Internal Server Error";
            case 1015: return "TLS Handshake Failure";
            default: return "Unknown (" + code + ")";
        }
    }
}
