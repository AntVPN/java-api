package io.antivpn.api.socket;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.antivpn.api.AntiVPN;
import io.antivpn.api.model.response.CheckResponse;
import io.antivpn.api.model.response.SettingsResponse;
import io.antivpn.api.util.GsonParser;
import lombok.Getter;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.channels.UnresolvedAddressException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class SocketClient extends WebSocketListener {
    public static final String API_VERSION = "1.1.7-RELEASE";
    public static final String API_MARKER = "ANTIVPN_API_OKHTTP_1_1_7_RELEASE";
    public static final String SOCKET_IMPLEMENTATION = "OkHttp";

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build();

    private final SocketManager socketManager;
    @Getter
    private final AntiVPN antiVPN;
    private final URI uri;
    private final Request request;
    private final long generation;
    private volatile WebSocket webSocket;
    private volatile CountDownLatch connectLatch = new CountDownLatch(1);
    private final AtomicBoolean open = new AtomicBoolean(false);
    private final AtomicBoolean connecting = new AtomicBoolean(false);

    public SocketClient(SocketManager socketManager, AntiVPN antiVPN, URI serverUri, Map<String, String> httpHeaders, long generation) {
        this.socketManager = socketManager;
        this.antiVPN = antiVPN;
        this.uri = serverUri;
        this.generation = generation;

        Request.Builder builder = new Request.Builder().url(serverUri.toString());
        for (Map.Entry<String, String> header : httpHeaders.entrySet()) {
            builder.header(header.getKey(), header.getValue());
        }
        this.request = builder.build();
    }

    public void connect() {
        if (this.open.get() || this.connecting.getAndSet(true)) return;
        this.connectLatch = new CountDownLatch(1);
        this.webSocket = CLIENT.newWebSocket(this.request, this);
    }

    public boolean connectBlocking(long timeout, TimeUnit timeUnit) throws InterruptedException {
        connect();
        return this.connectLatch.await(timeout, timeUnit) && this.isConnected();
    }

    public void send(String text) {
        WebSocket socket = this.webSocket;
        if (socket != null) {
            socket.send(text);
        }
    }

    public void send(byte[] bytes) {
        WebSocket socket = this.webSocket;
        if (socket != null) {
            socket.send(ByteString.of(bytes));
        }
    }

    public void close() {
        close(1000, "Closing");
    }

    public void close(int code, String reason) {
        WebSocket socket = this.webSocket;
        if (socket != null) {
            socket.close(code, reason);
        }
    }

    public boolean isOpen() {
        return this.open.get();
    }

    public boolean isConnected() {
        return this.open.get();
    }

    public boolean isConnecting() {
        return this.connecting.get() && !this.open.get();
    }

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        if (!this.socketManager.isCurrent(this, this.generation)) {
            webSocket.close(1000, "Replaced");
            return;
        }

        this.webSocket = webSocket;
        this.open.set(true);
        this.connecting.set(false);
        this.socketManager.markConnected();
        this.antiVPN.getLog().fine("Connected to the AntiVPN Server.");
        this.antiVPN.getLog().debug("OkHttp WebSocket handshake complete. Status: %d | Url: %s", response.code(), this.uri);
        this.connectLatch.countDown();
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
        if (!this.socketManager.isCurrent(this, this.generation)) return;
        handleMessage(text);
    }

    @Override
    public void onMessage(WebSocket webSocket, ByteString bytes) {
        if (!this.socketManager.isCurrent(this, this.generation)) return;
        handleMessage(bytes.utf8());
    }

    private void handleMessage(String message) {
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
                    SettingsResponse response = GsonParser.fromJson(settingsObject, SettingsResponse.class);

                    this.socketManager.setResponseKick(response.getKickMessage().replace("\r", ""));
                    this.socketManager.setShieldKick(response.getShieldMode().replace("\r", ""));
                    this.antiVPN.getLog().fine("Received settings from the AntiVPN Server.");
                    break;

                case "VERIFY":
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
    public void onClosing(WebSocket webSocket, int code, String reason) {
        webSocket.close(code, reason);
    }

    @Override
    public void onClosed(WebSocket webSocket, int code, String reason) {
        this.open.set(false);
        this.connecting.set(false);
        this.connectLatch.countDown();
        if (!this.socketManager.isCurrent(this, this.generation)) return;
        logClose(code, reason, true);
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        this.open.set(false);
        this.connecting.set(false);
        this.connectLatch.countDown();
        if (!this.socketManager.isCurrent(this, this.generation)) return;
        onError(t);
        logClose(1006, t.getMessage(), false);
    }

    private void onError(Throwable e) {
        String errorMsg;
        String message = e.getMessage();

        if (e instanceof ConnectException) {
            errorMsg = "Connection refused. The AntiVPN socket server might be offline or blocked by a firewall.";
        } else if (e instanceof UnknownHostException || e instanceof UnresolvedAddressException) {
            errorMsg = "Unknown host. Please check your server URI or DNS settings.";
        } else if (message != null && message.toLowerCase().contains("ping")) {
            this.antiVPN.getLog().error("OkHttp native ping failed: no pong received in time. %s", message);
            errorMsg = message;
        } else {
            errorMsg = message != null ? message : e.getClass().getSimpleName();
            e.printStackTrace();
        }

        this.antiVPN.getLog().error("Socket connection error: %s", errorMsg);
    }

    private void logClose(int code, String reason, boolean remote) {
        String readableReason = (reason == null || reason.isEmpty()) ? getReadableCloseReason(code) : reason;
        String initiator = remote ? "Server" : "Client";
        this.antiVPN.getLog().error("Disconnected from AntiVPN Server [%s]. (Code: %d, Reason: %s)", initiator, code, readableReason);
    }

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
