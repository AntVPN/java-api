package io.antivpn.api.socket;

import com.google.gson.JsonObject;
import io.antivpn.api.AntiVPN;
import io.antivpn.api.data.socket.response.ResponseType;
import io.antivpn.api.data.socket.response.impl.CheckResponse;
import io.antivpn.api.data.socket.response.impl.SettingsResponse;
import io.antivpn.api.utils.GsonParser;
import lombok.Getter;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.Map;

public class SocketClient extends WebSocketClient {
    private final SocketManager socketManager;
    private final AntiVPN antiVPN;
    @Getter
    private boolean connecting = true;

    public SocketClient(SocketManager socketManager, AntiVPN antiVPN, URI serverUri, Map<String, String> httpHeaders) {
        super(serverUri, httpHeaders);
        this.socketManager = socketManager;
        this.antiVPN = antiVPN;
        this.setTcpNoDelay(true);
    }

    @Override
    public void connect() {
        this.connecting = true;
        super.connect();
    }

    @Override
    public void reconnect() {
        this.connecting = true;
        super.reconnect();
    }

    @Override
    public void onMessage(String message) {
        this.antiVPN.getConsole().debug("Received message from the AntiVPN Server. (Message: %s)", message);
        try {
            JsonObject object = GsonParser.parse(message);

            if (!object.has("type")) {
                this.antiVPN.getConsole().error("Received invalid message from the AntiVPN Server. (Message: %s)", message);
                return;
            }

            if (object.get("type").getAsString().equalsIgnoreCase(ResponseType.SETTINGS.name())) {
                JsonObject settingsObject = object.get("settings").getAsJsonObject();
                SettingsResponse response = GsonParser.fromJson(settingsObject, SettingsResponse.class);

                this.socketManager.setResponseKick(response.getKickMessage());
                this.antiVPN.getConsole().fine("Received settings from the AntiVPN Server.");
            } else if (object.get("type").getAsString().equalsIgnoreCase(ResponseType.VERIFY.name())) {
                this.socketManager.getSocketDataHandler().handle(
                        GsonParser.fromJson(message, CheckResponse.class)
                );
            } else {
                this.antiVPN.getConsole().error("Received invalid message from the AntiVPN Server. (Message: %s)", message);
            }
        } catch (Exception e) {
            this.antiVPN.getConsole().error("An error occurred while parsing the message from the AntiVPN Server. (Message: %s)", message);
            this.antiVPN.getConsole().error("Error: %s", e.getMessage());
        }
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        this.connecting = false;
        this.antiVPN.getConsole().fine("Connected to the AntiVPN Server.");
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        this.connecting = false;
        if (reason == null || reason.isEmpty()) reason = "Unknown";

        this.antiVPN.getConsole().error("Disconnected from the AntiVPN Server. (Code: %s, Reason: %s)", code, reason);
        this.close();
    }

    @Override
    public void onError(Exception e) {
        this.connecting = false;
        this.antiVPN.getConsole().error("An error occurred, please report this to the developer. (Error: %s)", e.getMessage());
        e.printStackTrace();
    }

    public boolean isConnected() {
        if (this.isClosed()) return false;
        return this.isOpen() && !this.isClosing();
    }
}
