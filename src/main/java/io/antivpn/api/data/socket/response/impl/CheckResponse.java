package io.antivpn.api.data.socket.response.impl;

import lombok.Data;

@Data
public class CheckResponse {
    private String uid;
    private boolean valid;

    public String toString() {
        return String.format("DataResponse(uid=%s, valid=%s)", uid, valid);
    }
}