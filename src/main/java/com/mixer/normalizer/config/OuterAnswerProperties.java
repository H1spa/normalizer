package com.mixer.normalizer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/*
 * Настройки клиента outer-answer.
 * Внешний адрес строится из базового URL и имени операции:
 *   begin  -> OUTER_ANSWER_URL + "/" + service
 *   finish -> OUTER_ANSWER_URL + "/" + service + "/" + id
 * Такой формат позволяет одному клиенту работать с flux, scoop, proba и другими service
 * без отдельных классов под каждую операцию.
 */
@Component
@ConfigurationProperties(prefix = "outer-answer")
public class OuterAnswerProperties {
    private String url;
    private int maxConcurrent = 5;
    private int acquireTimeoutSeconds = 30;
    private int connectTimeoutMillis = 10000;
    private int readTimeoutMillis = 30000;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = trimTrailingSlash(url);
    }

    public int getMaxConcurrent() {
        return maxConcurrent;
    }

    public void setMaxConcurrent(int maxConcurrent) {
        if (maxConcurrent <= 0) {
            throw new IllegalArgumentException("outer-answer.max-concurrent must be positive");
        }
        this.maxConcurrent = maxConcurrent;
    }

    public int getAcquireTimeoutSeconds() {
        return acquireTimeoutSeconds;
    }

    public void setAcquireTimeoutSeconds(int acquireTimeoutSeconds) {
        this.acquireTimeoutSeconds = acquireTimeoutSeconds;
    }

    public int getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public void setConnectTimeoutMillis(int connectTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    public int getReadTimeoutMillis() {
        return readTimeoutMillis;
    }

    public void setReadTimeoutMillis(int readTimeoutMillis) {
        this.readTimeoutMillis = readTimeoutMillis;
    }

    // Полный URL создания записи: base URL + имя операции, например ".../flux".
    public String getCreateFullUrl(String service) {
        return join(url, normalizeService(service));
    }

    // Полный URL завершения записи: base URL + имя операции + id, например ".../flux/abc-123".
    public String getFinishFullUrl(String service, String externalEventId) {
        return join(url, normalizeService(service) + "/" + normalizeService(externalEventId));
    }

    // Убирает лишние слеши в конце базового URL, чтобы склейка путей была стабильной.
    private static String trimTrailingSlash(String value) {
        if (value == null) {
            return null;
        }
        while (value.endsWith("/") && value.length() > 1) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    /*
     * Имя service и id берутся из бизнес-логики/ответа внешнего сервиса.
     * Убираем крайние слеши, чтобы не получить случайный двойной "//" в URL.
     */
    private static String normalizeService(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("outer-answer service/id is empty");
        }
        String result = value.trim();
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        if (result.isBlank()) {
            throw new IllegalStateException("outer-answer service/id is empty");
        }
        return result;
    }

    // Склеивает базовый URL и путь, предварительно проверяя обязательный base URL.
    private static String join(String baseUrl, String path) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("outer-answer.url is empty");
        }
        String normalizedBase = trimTrailingSlash(baseUrl);
        return normalizedBase + "/" + path;
    }
}
