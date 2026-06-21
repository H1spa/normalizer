package com.mixer.normalizer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "logging-audit")
public class AuditProperties {
    private boolean enabled = true;
    private boolean saveRequestBody;
    private boolean saveResponseBody;
    private boolean maskSecrets = true;
    private int maxPayloadChars = 8192;
    private int queueCapacity = 10000;
    private int shutdownWaitSeconds = 10;
    private int warningIntervalSeconds = 30;
    private long migrationRetryMillis = 30000;
    private String correlationHeader = "X-Correlation-ID";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isSaveRequestBody() {
        return saveRequestBody;
    }

    public void setSaveRequestBody(boolean saveRequestBody) {
        this.saveRequestBody = saveRequestBody;
    }

    public boolean isSaveResponseBody() {
        return saveResponseBody;
    }

    public void setSaveResponseBody(boolean saveResponseBody) {
        this.saveResponseBody = saveResponseBody;
    }

    public boolean isMaskSecrets() {
        return maskSecrets;
    }

    public void setMaskSecrets(boolean maskSecrets) {
        this.maskSecrets = maskSecrets;
    }

    public int getMaxPayloadChars() {
        return maxPayloadChars;
    }

    public void setMaxPayloadChars(int maxPayloadChars) {
        this.maxPayloadChars = positive(maxPayloadChars, "max-payload-chars");
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = positive(queueCapacity, "queue-capacity");
    }

    public int getShutdownWaitSeconds() {
        return shutdownWaitSeconds;
    }

    public int getWarningIntervalSeconds() {
        return warningIntervalSeconds;
    }

    public void setWarningIntervalSeconds(int warningIntervalSeconds) {
        this.warningIntervalSeconds = positive(warningIntervalSeconds, "warning-interval-seconds");
    }

    public void setShutdownWaitSeconds(int shutdownWaitSeconds) {
        this.shutdownWaitSeconds = positive(shutdownWaitSeconds, "shutdown-wait-seconds");
    }

    public long getMigrationRetryMillis() {
        return migrationRetryMillis;
    }

    public void setMigrationRetryMillis(long migrationRetryMillis) {
        if (migrationRetryMillis <= 0) {
            throw new IllegalArgumentException("logging-audit.migration-retry-millis must be positive");
        }
        this.migrationRetryMillis = migrationRetryMillis;
    }

    public String getCorrelationHeader() {
        return correlationHeader;
    }

    public void setCorrelationHeader(String correlationHeader) {
        if (correlationHeader == null || correlationHeader.isBlank()) {
            throw new IllegalArgumentException("logging-audit.correlation-header must not be blank");
        }
        this.correlationHeader = correlationHeader.trim();
    }

    private int positive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException("logging-audit." + name + " must be positive");
        }
        return value;
    }
}
