package io.antivpn.api.data.socket.request.impl;

import io.antivpn.api.data.socket.request.Request;
import io.antivpn.api.data.socket.request.RequestType;
import io.antivpn.api.utils.IDGenerator;
import lombok.Getter;

@Getter
public class CheckRequest extends Request {
    private final String transactionalId;
    private final String address;
    private final String username;

    public CheckRequest(String address, String username) {
        super(RequestType.VERIFY);
        this.transactionalId = IDGenerator.generateUniqueID();
        this.address = address;
        this.username = username;
    }
}
