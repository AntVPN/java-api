package io.antivpn.api;

import io.antivpn.api.config.AntiVPNConfig;
import io.antivpn.api.logging.Log;
import io.antivpn.api.logging.VPNLogger;
import io.antivpn.api.socket.SocketManager;
import lombok.Getter;

import java.time.Duration;
import java.util.Timer;
import java.util.logging.Level;

@Getter
public class AntiVPN {
    private final String pluginName;
    private final VPNLogger vpnLogger;
    private AntiVPNConfig antiVPNConfig;
    private final Log log;
    private final SocketManager socketManager;

    private AntiVPN(String pluginName, VPNLogger vpnLogger, AntiVPNConfig antiVPNConfig, Log log, Duration cacheDuration) {
        this.pluginName = pluginName;
        this.vpnLogger = vpnLogger;
        this.antiVPNConfig = antiVPNConfig;
        this.log = log;
        this.socketManager = new SocketManager(this, cacheDuration);
    }

    public void start() {
        this.antiVPNConfig.validate();
        this.socketManager.connect();
    }

    /**
     * Hot-swaps the config at runtime.
     * If apiKey, endpoint, or userAgent changed, the socket is safely closed and reconnected.
     *
     * @param newConfig the new config to apply
     */
    public void reload(AntiVPNConfig newConfig) {
        String oldApiKey = this.antiVPNConfig.getApiKey();
        String oldEndpoint = this.antiVPNConfig.getEndpoint();
        String oldUserAgent = this.antiVPNConfig.getUserAgent();

        this.antiVPNConfig = newConfig;

        boolean needsReconnect = !oldApiKey.equals(newConfig.getApiKey())
                || !oldEndpoint.equals(newConfig.getEndpoint())
                || !java.util.Objects.equals(oldUserAgent, newConfig.getUserAgent());

        if (needsReconnect) {
            this.log.log("AntiVPN config changed (apiKey/endpoint/userAgent). Reconnecting...");
            this.socketManager.reconnect(true);
        } else {
            this.log.fine("AntiVPN config reloaded (no reconnect required).");
        }
    }

    public static AntiVPN create(String pluginName, VPNLogger vpnLogger, AntiVPNConfig antiVPNConfig, Duration cacheDuration) {
        return new AntiVPN(pluginName, vpnLogger, antiVPNConfig, new Log(antiVPNConfig, vpnLogger), cacheDuration);
    }

    @Deprecated
    public void fireUp() {
        start();
    }

    @Deprecated
    public io.antivpn.api.logger.Console getConsole() {
        return new io.antivpn.api.logger.Console(this.antiVPNConfig, this.vpnLogger);
    }
}