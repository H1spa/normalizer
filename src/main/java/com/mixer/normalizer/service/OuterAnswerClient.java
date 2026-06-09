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
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/*
 * Клиент внешнего сервиса outer-answer.
 * На begin создает запись операции через POST service.
 * На finish закрывает ту же запись через PUT service/id, где id пришел из ответа begin.
 */
@Component
public class OuterAnswerClient {

    private static final Logger log = LoggerFactory.getLogger(OuterAnswerClient.class);

    private final RestTemplate restTemplate;
    private final OuterAnswerProperties properties;
    /*
     * RestTemplate - обычный HTTP-клиент Spring.
     * Semaphore - счетчик одновременных запросов: он не дает normalizer завалить
     * OuterAnswerMock/outer-answer слишком большим числом параллельных HTTP-вызовов.
     */
    private final Semaphore semaphore;

    public OuterAnswerClient(OuterAnswerProperties properties) {
        this.properties = properties;
        // Fair semaphore=true выдает разрешения в порядке ожидания, без голодания потоков.
        this.semaphore = new Semaphore(properties.getMaxConcurrent(), true);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeoutMillis());
        requestFactory.setReadTimeout(properties.getReadTimeoutMillis());
        this.restTemplate = new RestTemplate(requestFactory);
    }

    private static final String MIXER_FIELD = "mixer";
    private static final String DATE_FIELD = "date";
    private static final String FOLDER_FIELD = "imageFolderPath";
    private static final String ID_FIELD = "id";

    public String createEvent(String service, int mixerId, String beginTime, String folder) {
        // Любой запрос к outer-answer сначала проходит через лимит параллельности.
        acquire();

        try {
            /*
             * На begin создается новая внешняя запись.
             * Внешнему сервису нужны только три вещи:
             * mixer - к какому миксеру относится запись,
             * date - время начала операции,
             * imageFolderPath - где лежат изображения события.
             * { "mixer": 123, "date": "...+0700", "imageFolderPath": "/images/path" }
             * Здесь нет type: тип операции уже выбран URL-ом service.
             */
            Map<String, Object> body = new LinkedHashMap<>();
            body.put(MIXER_FIELD, mixerId);
            body.put(DATE_FIELD, beginTime);
            body.put(FOLDER_FIELD, folder);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            String url = properties.getCreateFullUrl(service);
            ResponseEntity<Object> response = restTemplate.exchange(url, HttpMethod.POST, entity, Object.class);
            String externalId = extractExternalId(response.getBody());

            // Без id мы не сможем потом закрыть событие, поэтому это критическая ошибка.
            if (externalId == null || externalId.isBlank()) {
                throw new IllegalStateException("outer-answer did not return id");
            }

            log.info("POST {} returned id={}", service, externalId);
            return externalId;
        } catch (RestClientException e) {
            log.error("Failed to POST {} to outer-answer", service, e);
            throw e;
        } finally {
            // acquire() успешно завершился до try, поэтому release соответствует одному разрешению.
            semaphore.release();
        }
    }

    public void finishEvent(String service, String externalEventId, String finishTime) {
        // Закрытие тоже ограничивается семафором, чтобы не перегрузить внешний сервис.
        acquire();

        try {
            /*
             * На finish мы не создаем новую запись, а дозаполняем уже открытую.
             * Поэтому в тело кладется только date завершения.
             * mixer, imageFolderPath и любые другие поля сюда не добавляются.
             */
            Map<String, String> body = Map.of(DATE_FIELD, finishTime);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

            String url = properties.getFinishFullUrl(service, externalEventId);
            restTemplate.exchange(url, HttpMethod.PUT, entity, Void.class);

            log.info("PUT {}/{} with date={}", service, externalEventId, finishTime);
        } catch (RestClientException e) {
            log.error("Failed to PUT {}/{} to outer-answer", service, externalEventId, e);
            throw e;
        } finally {
            // Всегда возвращаем разрешение семафора, даже если внешний запрос упал.
            semaphore.release();
        }
    }

    // Ждет свободное место в лимите параллельных запросов к outer-answer.
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

    // Внешний сервис возвращает id созданной записи в поле "id".
    private String extractExternalId(Object response) {
        Object value = extractPath(response, ID_FIELD);
        if (value != null) {
            return String.valueOf(value);
        }
        return null;
    }

    // Достает значение из вложенной map по пути вроде "data.id".
    private Object extractPath(Object value, String path) {
        if (value == null || path == null || path.isBlank()) {
            return null;
        }

        Object current = value;
        for (String part : path.split("\\.")) {
            // Каждый сегмент пути должен быть map-объектом, иначе такого поля в ответе нет.
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
