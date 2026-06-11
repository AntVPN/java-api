package io.antivpn.api.socket;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.antivpn.api.AntiVPN;
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
    private final AntiVPN antiVPN;

    public SocketClient(SocketManager socketManager, AntiVPN antiVPN, URI serverUri, Map<String, String> httpHeaders) {
        super(serverUri, httpHeaders);
        this.socketManager = socketManager;
        this.antiVPN = antiVPN;
        this.setTcpNoDelay(true);
        this.setConnectionLostTimeout(30);
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
                    // Ignore PONG messages, they are used for keepalive
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
        this.antiVPN.getLog().fine("Connected to the AntiVPN Server.");
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        String readableReason = (reason == null || reason.isEmpty()) ? getReadableCloseReason(code) : reason;
        String initiator = remote ? "Server" : "Client";

        this.antiVPN.getLog().error("Disconnected from AntiVPN Server [%s]. (Code: %d, Reason: %s)", initiator, code, readableReason);
        // this.close() was removed because it is redundant to call it inside onClose
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
