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

    private String checkId;
    private String username;
    private String uniqueId;
    private String address;
    private String hostname;
    private String server;
    private String version;
    private String event;
    private boolean premium;

    public UserDataRequest() {
        super(RequestType.USER_DATA);
    }

    public UserDataRequest(String checkId, String username, String uniqueId, String version, String address, String hostname, String server, Event event, boolean premium) {
        super(RequestType.USER_DATA);
        this.checkId = checkId;
        this.username = username;
        this.uniqueId = uniqueId;
        this.version = version;
        this.address = address;
        this.hostname = hostname;
        this.server = server;
        this.event = event.name();
        this.premium = premium;
    }
}
