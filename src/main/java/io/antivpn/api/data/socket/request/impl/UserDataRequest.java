package io.antivpn.api.data.socket.request.impl;

import io.antivpn.api.data.socket.request.Request;
import io.antivpn.api.data.socket.request.RequestType;
import io.antivpn.api.utils.Event;
import lombok.Getter;

@Getter
public class UserDataRequest extends Request {
    private final String username;
    private final String uniqueId;
    private final String address;
    private final String server;
    private final String version;
    private final String event;
    private final boolean premium;

    public UserDataRequest(String username, String uniqueId, String version, String address, String server, Event event, boolean premium) {
        super(RequestType.USER_DATA);
        this.username = username;
        this.uniqueId = uniqueId;
        this.version = version;
        this.address = address;
        this.server = server;
        this.event = event.name();
        this.premium = premium;
    }
}
