package com.mixer.normalizer.service;

import com.mixer.normalizer.dto.OutputEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;

@Component
@ConditionalOnExpression("!'${output.url:}'.isBlank()")
public class HttpOutputSender implements OutputSender {

    private static final Logger log = LoggerFactory.getLogger(HttpOutputSender.class);

    private final RestTemplate restTemplate;
    private final String outputUrl;
    private final String outputMethod;

    public HttpOutputSender(@Value("${output.url}") String outputUrl,
                            @Value("${output.method:POST}") String outputMethod,
                            @Value("${output.connect-timeout-millis:10000}") int connectTimeoutMillis,
                            @Value("${output.read-timeout-millis:30000}") int readTimeoutMillis) {
        this.outputUrl = outputUrl;
        this.outputMethod = outputMethod;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMillis);
        requestFactory.setReadTimeout(readTimeoutMillis);
        this.restTemplate = new RestTemplate(requestFactory);
    }

    @Override
    public void send(OutputEvent event) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<OutputEvent> request = new HttpEntity<>(event, headers);
            restTemplate.exchange(outputUrl, httpMethod(outputMethod), request, Void.class);

            log.info("Sent normalized event to {}: {}", outputUrl, event);
        } catch (Exception e) {
            log.error("Failed to send normalized event to {}: {}", outputUrl, event, e);
        }
    }

    private HttpMethod httpMethod(String method) {
        if (method == null || method.isBlank()) {
            return HttpMethod.POST;
        }
        return HttpMethod.valueOf(method.trim().toUpperCase());
    }
}
