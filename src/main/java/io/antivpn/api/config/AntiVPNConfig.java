package io.antivpn.api.config;

import java.util.logging.Level;

public class AntiVPNConfig {
    private String endpoint = "wss://connection.antivpn.io/live_checker";
    private String apiKey = "your-api-key-here";
    private boolean debug = false;
    private Level level = Level.FINE;

    private AntiVPNConfig() {
    }

    public AntiVPNConfig withDefaultEndpoint() {
        this.endpoint = "wss://connection.antivpn.io/live_checker";
        return this;
    }

    public AntiVPNConfig withEndpoint(String endpoint) {
        this.endpoint = endpoint;
        return this;
    }

    public AntiVPNConfig withApiKey(String apiKey) {
        this.apiKey = apiKey;
        return this;
    }

    public AntiVPNConfig setDebug(boolean debug) {
        this.debug = debug;
        return this;
    }

    public AntiVPNConfig setLevel(Level level) {
        this.level = level;
        return this;
    }

    public static AntiVPNConfig create() {
        return new AntiVPNConfig();
    }


    public String getEndpoint() {
        return this.endpoint;
    }

    public String getApiKey() {
        return this.apiKey;
    }

    public boolean isDebug() {
        return this.debug;
    }

    public Level getLevel() {
        return this.level;
    }
}
