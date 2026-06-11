package io.antivpn.api.logging;

import io.antivpn.api.config.AntiVPNConfig;
import lombok.RequiredArgsConstructor;

import java.util.logging.Level;

@RequiredArgsConstructor
public class Log {
    private final AntiVPNConfig antiVPNConfig;
    private final VPNLogger vpnLogger;

    public void log(String message, Object... args) {
        if (antiVPNConfig.getLevel().intValue() <= Level.INFO.intValue()) {
            this.vpnLogger.log(
                    placeholder(message, args)
            );
        }
    }

    public void fine(String message, Object... args) {
        if (antiVPNConfig.getLevel().intValue() <= Level.FINE.intValue()) {
            this.vpnLogger.fine(
                    placeholder(message, args)
            );
        }
    }

    public void error(String message, Object... args) {
        if (antiVPNConfig.getLevel().intValue() <= Level.SEVERE.intValue()) {
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
        return String.format(message, args);
    }
}
