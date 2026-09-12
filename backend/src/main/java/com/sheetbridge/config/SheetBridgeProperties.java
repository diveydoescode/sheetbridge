package com.sheetbridge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sheetbridge")
public class SheetBridgeProperties {

    private String actorHeader = "X-Actor";
    private String defaultActor = "system";
    private String corsOrigins = "http://localhost:5173";
    private final Sync sync = new Sync();
    private final Google google = new Google();

    public String getActorHeader() {
        return actorHeader;
    }

    public void setActorHeader(String actorHeader) {
        this.actorHeader = actorHeader;
    }

    public String getDefaultActor() {
        return defaultActor;
    }

    public void setDefaultActor(String defaultActor) {
        this.defaultActor = defaultActor;
    }

    public String getCorsOrigins() {
        return corsOrigins;
    }

    public void setCorsOrigins(String corsOrigins) {
        this.corsOrigins = corsOrigins;
    }

    public Sync getSync() {
        return sync;
    }

    public Google getGoogle() {
        return google;
    }

    public static class Sync {
        private int batchSize = 10;
        private long backoffBaseMs = 100;
        private long backoffMaxMs = 8000;
        private int sheetsMaxAttempts = 6;

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public long getBackoffBaseMs() {
            return backoffBaseMs;
        }

        public void setBackoffBaseMs(long backoffBaseMs) {
            this.backoffBaseMs = backoffBaseMs;
        }

        public long getBackoffMaxMs() {
            return backoffMaxMs;
        }

        public void setBackoffMaxMs(long backoffMaxMs) {
            this.backoffMaxMs = backoffMaxMs;
        }

        public int getSheetsMaxAttempts() {
            return sheetsMaxAttempts;
        }

        public void setSheetsMaxAttempts(int sheetsMaxAttempts) {
            this.sheetsMaxAttempts = sheetsMaxAttempts;
        }
    }

    public static class Google {
        private String accessToken = "";
        private String applicationCredentials = "";

        public String getAccessToken() {
            return accessToken;
        }

        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }

        public String getApplicationCredentials() {
            return applicationCredentials;
        }

        public void setApplicationCredentials(String applicationCredentials) {
            this.applicationCredentials = applicationCredentials;
        }
    }
}
