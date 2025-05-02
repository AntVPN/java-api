package io.antivpn.api.data.socket.response.impl;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Date;

@Data
@AllArgsConstructor
public class SettingsResponse {
    private final int enabled;
    private final String kickMessage;
    private final String shieldMode;
    private final Date date;

    public SettingsResponse() {
        this(1,
                "§cVPN Detected!\n§cPlease disable your VPN and rejoin.\n§cIf you believe this is a mistake, please contact an administrator.",
                "§cShield is enabled!\n§cPlease wait a couple seconds before joining.\n§cIf you believe this is a mistake, please contact an administrator.",
                new Date()
        );
    }

    public boolean isEnabled() {
        return this.enabled == 1;
    }
}