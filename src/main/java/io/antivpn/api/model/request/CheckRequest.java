package io.antivpn.api.model.request;

import io.antivpn.api.util.IDGenerator;
import lombok.Getter;

@Getter
public class CheckRequest extends Request {
    private final String transactionalId;
    private final String address;
    private final String userId;
    private final String username;

    public CheckRequest(String address, String userId, String username) {
        super(RequestType.VERIFY);
        this.transactionalId = IDGenerator.generateUniqueID();
        this.address = address;
        this.userId = userId;
        this.username = username;
    }
}
