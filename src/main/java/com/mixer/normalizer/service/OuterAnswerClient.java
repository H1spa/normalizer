package com.mixer.normalizer.service;

import com.mixer.normalizer.config.OuterAnswerProperties;
import com.mixer.normalizer.dto.OuterAnswerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Component
public class OuterAnswerClient {

    private static final Logger log = LoggerFactory.getLogger(OuterAnswerClient.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final OuterAnswerProperties properties;
    private final Semaphore semaphore;

    public OuterAnswerClient(OuterAnswerProperties properties) {
        this.properties = properties;
        this.semaphore = new Semaphore(properties.getMaxConcurrent(), true);
    }

    public String createEvent(int mixerId, String beginTime, String folder) {
        acquire();

        try {
            Map<String, Object> body = Map.of(
                    "mixer_id", mixerId,
                    "begin_time", beginTime,
                    "folder", folder
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            String url = properties.getCreateFullUrl();
            OuterAnswerResponse response = restTemplate.postForObject(url, entity, OuterAnswerResponse.class);

            if (response == null || response.getId() == null || response.getId().isBlank()) {
                throw new IllegalStateException("outer-answer did not return id");
            }

            log.info("Created outer-answer event externalId={}", response.getId());
            return response.getId();
        } finally {
            semaphore.release();
        }
    }

    public void finishEvent(String externalEventId, String finishTime) {
        acquire();

        try {
            Map<String, String> body = Map.of("finish_time", finishTime);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

            String url = properties.getFinishFullUrl(externalEventId);
            restTemplate.put(url, entity);

            log.info("Finished outer-answer event externalId={} at {}", externalEventId, finishTime);
        } finally {
            semaphore.release();
        }
    }

    private void acquire() {
        try {
            if (!semaphore.tryAcquire(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Too many concurrent requests to outer-answer");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting outer-answer semaphore", e);
        }
    }
}
