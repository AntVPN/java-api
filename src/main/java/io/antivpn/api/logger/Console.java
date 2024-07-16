package io.antivpn.api.logger;

import io.antivpn.api.config.AntiVPNConfig;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class Console {
    private final AntiVPNConfig antiVPNConfig;
    private final VPNLogger vpnLogger;

    public void log(String message, Object... args) {
        this.vpnLogger.log(
                placeholder(message, args)
        );
    }

    public void fine(String message, Object... args) {
        this.vpnLogger.fine(
                placeholder(message, args)
        );
    }

    public void error(String message, Object... args) {
        this.vpnLogger.error(
                placeholder(message, args)
        );
    }

    public void debug(String message, Object... args) {
        if (!this.antiVPNConfig.isDebug()) return;
        this.vpnLogger.debug(
                placeholder(message, args)
        );
    }

    private String placeholder(String message, Object... args) {
        // Add [ServerAntiVPN] prefix
        message = "[ServerAntiVPN] "  + message;

        // Replace %s with args
        return String.format(message, args);
    }
}
