package com.mixer.normalizer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "outer-answer")
public class OuterAnswerProperties {
    private String url;
    private String createPath = "/";
    private String finishPath = "/{id}";

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getCreatePath() { return createPath; }
    public void setCreatePath(String createPath) { this.createPath = createPath; }
    public String getFinishPath() { return finishPath; }
    public void setFinishPath(String finishPath) { this.finishPath = finishPath; }

    public String getCreateFullUrl() {
        return url + createPath;
    }
    public String getFinishFullUrl(String eventId) {
        return url + finishPath.replace("{id}", eventId);
    }
}