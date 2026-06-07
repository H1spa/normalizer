package com.mixer.normalizer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "outer-answer")
public class OuterAnswerProperties {
    private String url;
    private String createPath = "/";
    private String finishPath = "/{id}";
    private int maxConcurrent = 5;

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

    public String getCreateFullUrl() {
        return join(url, createPath);
    }

    public String getFinishFullUrl(String externalEventId) {
        return join(url, finishPath.replace("{id}", externalEventId));
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
}