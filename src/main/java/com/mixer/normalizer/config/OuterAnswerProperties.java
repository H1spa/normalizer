package com.mixer.normalizer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "outer-answer")
public class OuterAnswerProperties {
    private String url;
    private String createPath = "/";
    private String finishPath = "/{id}";
    private int maxConcurrent = 5;
    private int acquireTimeoutSeconds = 30;
    private int connectTimeoutMillis = 10000;
    private int readTimeoutMillis = 30000;
    private String createMethod = "POST";
    private String finishMethod = "PUT";
    private String mixerField = "mixer";
    private String dateField = "date";
    private String folderField = "imagesFolderPath";
    private String responseIdFields = "id,event_id,eventId,data.id";

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = trimTrailingSlash(url);
    }

    public String getCreatePath() {
        return createPath;
    }

    public void setCreatePath(String createPath) {
        this.createPath = normalizePath(createPath);
    }

    public String getFinishPath() {
        return finishPath;
    }

    public void setFinishPath(String finishPath) {
        this.finishPath = normalizePath(finishPath);
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

    public String getCreateMethod() {
        return createMethod;
    }

    public void setCreateMethod(String createMethod) {
        this.createMethod = createMethod;
    }

    public String getFinishMethod() {
        return finishMethod;
    }

    public void setFinishMethod(String finishMethod) {
        this.finishMethod = finishMethod;
    }

    public String getMixerField() {
        return mixerField;
    }

    public void setMixerField(String mixerField) {
        this.mixerField = mixerField;
    }

    public String getDateField() {
        return dateField;
    }

    public void setDateField(String dateField) {
        this.dateField = dateField;
    }

    public String getFolderField() {
        return folderField;
    }

    public void setFolderField(String folderField) {
        this.folderField = folderField;
    }

    public String getResponseIdFields() {
        return responseIdFields;
    }

    public void setResponseIdFields(String responseIdFields) {
        this.responseIdFields = responseIdFields;
    }

    public List<String> getResponseIdFieldList() {
        return parseList(responseIdFields);
    }

    public String getCreateFullUrl() {
        return join(url, createPath);
    }

    public String getFinishFullUrl(String externalEventId) {
        return join(url, finishPath
                .replace("{id}", externalEventId)
                .replace("{eventId}", externalEventId)
                .replace(":id", externalEventId)
                .replace(":eventId", externalEventId));
    }

    private static String trimTrailingSlash(String value) {
        if (value == null) {
            return null;
        }
        while (value.endsWith("/") && value.length() > 1) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private static String join(String baseUrl, String path) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("outer-answer.url is empty");
        }
        String normalizedBase = trimTrailingSlash(baseUrl);
        String normalizedPath = normalizePath(path);
        return normalizedBase + normalizedPath;
    }

    private static List<String> parseList(String value) {
        List<String> result = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return result;
        }

        for (String part : value.split(",")) {
            String item = part.trim();
            if (!item.isBlank()) {
                result.add(item);
            }
        }
        return result;
    }
}
