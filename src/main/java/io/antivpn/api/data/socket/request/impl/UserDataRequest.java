package io.antivpn.api.data.socket.request.impl;

import io.antivpn.api.data.socket.request.Request;
import io.antivpn.api.data.socket.request.RequestType;
import io.antivpn.api.utils.Event;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(fluent = true)
public class UserDataRequest extends Request {

    private String sessionId;
    private String username;
    private String userId;
    private String address;
    private String hostname;
    private String server;
    private String version;
    private String event;
    private boolean premium;

    public UserDataRequest() {
        super(RequestType.USER_DATA);
    }

    public UserDataRequest(String sessionId, String username, String userId, String version, String address, String hostname, String server, Event event, boolean premium) {
        super(RequestType.USER_DATA);
        this.sessionId = sessionId;
        this.username = username;
        this.userId = userId;
        this.version = version;
        this.address = address;
        this.hostname = hostname;
        this.server = server;
        this.event = event.name();
        this.premium = premium;
    }
}
