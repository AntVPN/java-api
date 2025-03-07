package io.antivpn.api.data.socket.response.impl;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SettingsResponse {
    private final int enabled;
    private final String kickMessage;
    private final String logLevel;

    public SettingsResponse() {
        this(1, "§cVPN Detected!\n§cPlease disable your VPN and rejoin.\n§cIf you believe this is a mistake, please contact an administrator.", "FINE");
    }

    public boolean isEnabled() {
        return this.enabled == 1;
    }
}