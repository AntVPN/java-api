package io.antivpn.api.data.socket.response.impl;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class CheckResponse {
    private String uid;
    private String username;
    private String ip;
    private String country;
    private boolean valid;
    @SerializedName("is_attack")
    private boolean isAttack;

    public String toString() {
        return String.format(
                "DataResponse(uid=%s, username=%s, ip=%s, country=%s, valid=%s, is_attack=%s)",
                this.uid, this.username, this.ip, this.country, this.valid, this.isAttack
        );
    }
}