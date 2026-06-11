package io.antivpn.api.config;

import java.util.logging.Level;

public class AntiVPNConfig {
    private String endpoint = "wss://api.antivpn.io/connect";
    private String apiKey = "your-api-key-here";
    private String userAgent = null;
    private boolean debug = false;
    private Level level = Level.FINE;

    private AntiVPNConfig() {
    }

    public AntiVPNConfig withEndpoint(String endpoint) {
        this.endpoint = endpoint;
        return this;
    }

    public AntiVPNConfig withLoggingLevel(Level level) {
        this.level = level;
        return this;
    }

    public AntiVPNConfig withApiKey(String apiKey) {
        this.apiKey = apiKey;
        return this;
    }

    public AntiVPNConfig withUserAgent(String userAgent) {
        this.userAgent = userAgent;
        return this;
    }

    public AntiVPNConfig withDebug(boolean debug) {
        this.debug = debug;
        return this;
    }

    public AntiVPNConfig withLevel(Level level) {
        this.level = level;
        return this;
    }

    public static AntiVPNConfig create() {
        return new AntiVPNConfig();
    }

    /**
     * Validates that the config has a non-blank API key and a valid endpoint URI.
     *
     * @throws IllegalStateException if apiKey or endpoint is invalid
     */
    public void validate() {
        if (this.apiKey == null || this.apiKey.isBlank() || this.apiKey.equals("your-api-key-here")) {
            throw new IllegalStateException("AntiVPNConfig.apiKey is missing or unset.");
        }
        try {
            java.net.URI.create(this.endpoint);
        } catch (Exception e) {
            throw new IllegalStateException("AntiVPNConfig.endpoint is not a valid URI: " + this.endpoint);
        }
    }

    public String getEndpoint() {
        return this.endpoint;
    }

    public String getApiKey() {
        return this.apiKey;
    }

    public String getUserAgent() {
        return this.userAgent;
    }

    public boolean isDebug() {
        return this.debug;
    }

    public Level getLevel() {
        return this.level;
    }

    @Deprecated
    public AntiVPNConfig setDebug(boolean debug) {
        return withDebug(debug);
    }

    @Deprecated
    public AntiVPNConfig setLevel(Level level) {
        return withLevel(level);
    }
}
