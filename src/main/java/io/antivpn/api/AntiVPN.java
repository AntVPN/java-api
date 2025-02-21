package io.antivpn.api;

import io.antivpn.api.config.AntiVPNConfig;
import io.antivpn.api.logger.Console;
import io.antivpn.api.logger.VPNLogger;
import io.antivpn.api.socket.SocketManager;
import lombok.Getter;

import java.time.Duration;
import java.util.Timer;
import java.util.logging.Level;

@Getter
public class AntiVPN {
    private final String pluginName;
    private final VPNLogger vpnLogger;
    private final AntiVPNConfig antiVPNConfig;
    private final Console console;
    private final SocketManager socketManager;

    private AntiVPN(String pluginName, VPNLogger vpnLogger, AntiVPNConfig antiVPNConfig, Console console, Duration cacheDuration) {
        this.pluginName = pluginName;
        this.vpnLogger = vpnLogger;
        this.antiVPNConfig = antiVPNConfig;
        this.console = console;
        this.socketManager = new SocketManager(this, cacheDuration);
    }

    public void fireUp() {
        this.socketManager.connect();
    }

    public static AntiVPN create(String pluginName, VPNLogger vpnLogger, AntiVPNConfig antiVPNConfig, Duration cacheDuration) {
        return new AntiVPN(pluginName, vpnLogger, antiVPNConfig, new Console(antiVPNConfig, vpnLogger), cacheDuration);
    }

    public static AntiVPN create(String pluginName, VPNLogger vpnLogger, Console console, AntiVPNConfig antiVPNConfig, Duration cacheDuration) {
        return new AntiVPN(pluginName, vpnLogger, antiVPNConfig, console, cacheDuration);
    }
}