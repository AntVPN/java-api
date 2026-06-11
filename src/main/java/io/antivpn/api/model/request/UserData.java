package io.antivpn.api.model.request;

import io.antivpn.api.util.Event;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserData {
    private final String sessionId;
    private final String username;
    private final String userId;
    private final String version;
    private final String address;
    private final String hostname;
    private final String server;
    private final Event event;
    private final boolean premium;
}
