package com.library.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed binding for the {@code app.*} configuration namespace in application.yml. */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Jwt jwt = new Jwt();
    private final Internal internal = new Internal();
    private final ServiceB serviceB = new ServiceB();

    public Jwt getJwt() {
        return jwt;
    }

    public Internal getInternal() {
        return internal;
    }

    public ServiceB getServiceB() {
        return serviceB;
    }

    public static class Jwt {
        private String secret;
        private long expirationMs;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public long getExpirationMs() {
            return expirationMs;
        }

        public void setExpirationMs(long expirationMs) {
            this.expirationMs = expirationMs;
        }
    }

    public static class Internal {
        private String apiKey;

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
    }

    public static class ServiceB {
        private String baseUrl;
        private long timeoutMs;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public long getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
        }
    }
}
