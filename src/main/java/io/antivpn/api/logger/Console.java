package io.antivpn.api.logger;

import io.antivpn.api.config.AntiVPNConfig;
import lombok.RequiredArgsConstructor;

import java.util.logging.Level;

@RequiredArgsConstructor
public class Console {
    private final AntiVPNConfig antiVPNConfig;
    private final VPNLogger vpnLogger;
    private final Level level;

    public void log(String message, Object... args) {
        if (level.intValue() <= Level.INFO.intValue()) {
            this.vpnLogger.log(
                    placeholder(message, args)
            );
        }
    }

    public void fine(String message, Object... args) {
        if (level.intValue() <= Level.FINE.intValue()) {
            this.vpnLogger.fine(
                    placeholder(message, args)
            );
        }
    }

    public void error(String message, Object... args) {
        if (level.intValue() <= Level.SEVERE.intValue()) {
            this.vpnLogger.error(
                    placeholder(message, args)
            );
        }
    }

    public void debug(String message, Object... args) {
        if (!this.antiVPNConfig.isDebug()) return;
        this.vpnLogger.debug(
                placeholder(message, args)
        );
    }

    private String placeholder(String message, Object... args) {
        // Add [ServerAntiVPN] prefix
        message = "[ServerAntiVPN] " + message;

        // Replace %s with args
        return String.format(message, args);
    }
}
