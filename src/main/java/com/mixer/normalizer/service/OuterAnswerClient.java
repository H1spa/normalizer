package com.mixer.normalizer.service;

import com.mixer.normalizer.config.OuterAnswerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Component
public class OuterAnswerClient {

    private static final Logger log = LoggerFactory.getLogger(OuterAnswerClient.class);

    private final RestTemplate restTemplate;
    private final OuterAnswerProperties properties;
    // Limits pressure on outer-answer and keeps callers from piling up forever.
    private final Semaphore semaphore;

    public OuterAnswerClient(OuterAnswerProperties properties) {
        this.properties = properties;
        this.semaphore = new Semaphore(properties.getMaxConcurrent(), true);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeoutMillis());
        requestFactory.setReadTimeout(properties.getReadTimeoutMillis());
        this.restTemplate = new RestTemplate(requestFactory);
    }

    public String createEvent(int mixerId, String beginTime, String folder) {
        acquire();

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put(properties.getMixerField(), mixerId);
            body.put(properties.getDateField(), beginTime);
            body.put(properties.getFolderField(), folder);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            String url = properties.getCreateFullUrl();
            ResponseEntity<Object> response = restTemplate.exchange(url, httpMethod(properties.getCreateMethod()), entity, Object.class);
            String externalId = extractExternalId(response.getBody());

            if (externalId == null || externalId.isBlank()) {
                throw new IllegalStateException("outer-answer did not return id");
            }

            log.info("Created outer-answer event externalId={}", externalId);
            return externalId;
        } finally {
            // acquire() completes before the try block, so each release matches one permit.
            semaphore.release();
        }
    }

    public void finishEvent(String externalEventId, String finishTime) {
        acquire();

        try {
            Map<String, String> body = Map.of(properties.getDateField(), finishTime);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

            String url = properties.getFinishFullUrl(externalEventId);
            restTemplate.exchange(url, httpMethod(properties.getFinishMethod()), entity, Void.class);

            log.info("Finished outer-answer event externalId={} at {}", externalEventId, finishTime);
        } finally {
            // Always return the permit after the external request succeeds or fails.
            semaphore.release();
        }
    }

    private void acquire() {
        try {
            if (!semaphore.tryAcquire(properties.getAcquireTimeoutSeconds(), TimeUnit.SECONDS)) {
                throw new IllegalStateException("Too many concurrent requests to outer-answer");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting outer-answer semaphore", e);
        }
    }

    private HttpMethod httpMethod(String method) {
        if (method == null || method.isBlank()) {
            return HttpMethod.POST;
        }
        return HttpMethod.valueOf(method.trim().toUpperCase());
    }

    private String extractExternalId(Object response) {
        for (String field : properties.getResponseIdFieldList()) {
            Object value = extractPath(response, field);
            if (value != null) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private Object extractPath(Object value, String path) {
        if (value == null || path == null || path.isBlank()) {
            return null;
        }

        Object current = value;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(part);
            if (current == null) {
                return null;
            }
        }
        return current;
    }
}
