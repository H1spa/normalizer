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

@Component
public class OuterAnswerClient {
    private static final Logger log = LoggerFactory.getLogger(OuterAnswerClient.class);
    private final RestTemplate rest = new RestTemplate();
    private final OuterAnswerProperties properties;

    public OuterAnswerClient(OuterAnswerProperties properties) {
        this.properties = properties;
    }

    public String createEvent(int mixerId, String beginTime, String folder) {
        Map<String, Object> body = Map.of(
                "mixer_id", mixerId,
                "begin_time", beginTime,
                "folder", folder
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        String url = properties.getCreateFullUrl();
        OuterAnswerResponse response = rest.postForObject(url, request, OuterAnswerResponse.class);
        if (response == null || response.getId() == null) {
            throw new RuntimeException("Outer answer did not return id");
        }
        log.info("Created event in outer-answer, id={}", response.getId());
        return response.getId();
    }

    public void finishEvent(String eventId, String finishTime) {
        Map<String, String> body = Map.of("finish_time", finishTime);
        String url = properties.getFinishFullUrl(eventId);
        rest.put(url, body);
        log.info("Finished event {} in outer-answer at {}", eventId, finishTime);
    }
}