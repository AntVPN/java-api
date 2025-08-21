package io.antivpn.api.data.socket.response.impl;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class CheckResponse {
    private String transactional_id;
    private String check_id;
    private String username;
    private String ip;
    private String country;
    private String kick_message;

    private boolean valid;
    @SerializedName("is_attack")
    private boolean isAttack;

    public String toString() {
        return String.format(
                "DataResponse(transactional_id=%s, check_id=%s, username=%s, ip=%s, country=%s, kick_message=%s, valid=%s, is_attack=%s)",
                this.transactional_id, this.check_id, this.username, this.ip, this.country, this.kick_message, this.valid, this.isAttack
        );
    }
}