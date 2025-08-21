package io.antivpn.api.data.socket.response.impl;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class CheckResponse {
    private String transactionalId;
    private String checkId;
    private String username;
    private String ip;
    private String country;
    private String kickMessage;

    private boolean valid;
    @SerializedName("is_attack")
    private boolean isAttack;

    public String toString() {
        return String.format(
                "DataResponse(transactional_id=%s, check_id=%s, username=%s, ip=%s, country=%s, kick_message=%s, valid=%s, is_attack=%s)",
                this.transactionalId, this.checkId, this.username, this.ip, this.country, this.kickMessage, this.valid, this.isAttack
        );
    }
}