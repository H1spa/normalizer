package com.mixer.normalizer.service;

import com.mixer.normalizer.dto.OutputEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;

@Component
@ConditionalOnExpression("!'${output.url:}'.isBlank()")
public class HttpOutputSender implements OutputSender {

    private static final Logger log = LoggerFactory.getLogger(HttpOutputSender.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final String outputUrl;

    public HttpOutputSender(@Value("${output.url}") String outputUrl) {
        this.outputUrl = outputUrl;
    }

    @Override
    public void send(OutputEvent event) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<OutputEvent> request = new HttpEntity<>(event, headers);
            restTemplate.postForEntity(outputUrl, request, Void.class);

            log.info("Sent normalized event to {}: {}", outputUrl, event);
        } catch (Exception e) {
            log.error("Failed to send normalized event to {}: {}", outputUrl, event, e);
        }
    }
}
