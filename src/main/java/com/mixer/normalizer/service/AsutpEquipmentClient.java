package com.mixer.normalizer.service;

import com.mixer.normalizer.config.AsutpProperties;
import com.mixer.normalizer.service.EquipmentStateHolder.EquipmentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
 * Клиент АСУ ТП для получения состояний оборудования.
 * Работает только когда asutp.enabled=true; иначе EquipmentPoller использует legacy polling.
 */
@Component
public class AsutpEquipmentClient {

    private static final Logger log = LoggerFactory.getLogger(AsutpEquipmentClient.class);

    private final RestTemplate restTemplate;
    private final AsutpProperties properties;
    private final EquipmentStateHolder equipmentStateHolder;

    // Токен защищен synchronized pollStates(); вне этого потока его читать/писать не нужно.
    private String dataToken;

    public AsutpEquipmentClient(AsutpProperties properties,
                                EquipmentStateHolder equipmentStateHolder) {
        this.properties = properties;
        this.equipmentStateHolder = equipmentStateHolder;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeoutMillis());
        requestFactory.setReadTimeout(properties.getReadTimeoutMillis());
        this.restTemplate = new RestTemplate(requestFactory);
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public synchronized Map<Integer, EquipmentState> pollStates() {
        // synchronized защищает dataToken от одновременного refresh несколькими polling-вызовами.
        if (!properties.isEnabled()) {
            return Map.of();
        }

        try {
            return pollWithToken(ensureDataToken());
        } catch (HttpClientErrorException.Unauthorized e) {
            // АСУ ТП использует токен до 401; после этого повторяем всю цепочку авторизации.
            log.info("ASUTP token expired, refreshing");
            dataToken = null;
            return pollWithToken(ensureDataToken());
        }
    }

    private Map<Integer, EquipmentState> pollWithToken(String token) {
        // url_3 принимает data_3 вида {"tagIds": ["..."]} и возвращает сырые значения тегов.
        Object response = request(properties.getUrl3(), properties.getMethod3(), buildDataRequestBody(), token, false);
        Map<String, Object> tagValues = extractTagValues(response);
        return toEquipmentStates(tagValues);
    }

    private String ensureDataToken() {
        // Токен лениво создается при первом polling и переиспользуется до 401.
        if (dataToken == null || dataToken.isBlank()) {
            dataToken = authenticate();
        }
        return dataToken;
    }

    private String authenticate() {
        validateSettings();

        // Шаг 1: url_1 — обычный POST-запрос без Authorization; он возвращает token.
        Map<String, Object> authBody = new LinkedHashMap<>();
        authBody.putAll(properties.getAuthBodyMap());
        putIfPresent(authBody, properties.getDomainNameField(), properties.getDomainName());
        putIfPresent(authBody, properties.getPasswordField(), properties.getPassword());

        Object authResponse = request(properties.getUrl1(), properties.getMethod1(), authBody, null, false);
        String authToken = extractToken(authResponse);
        if (authToken == null || authToken.isBlank()) {
            throw new IllegalStateException("ASUTP url_1 did not return token");
        }

        // Шаг 2: url_2 получает token в Basic base64 Authorization и возвращает token для url_3.
        Map<String, Object> contextBody = new LinkedHashMap<>();
        contextBody.putAll(properties.getContextBodyMap());
        putIfPresent(contextBody, properties.getDomainNameField(), properties.getDomainName());
        putIfPresent(contextBody, properties.getCastHouseIdField(), properties.getCastHouseId());

        Object contextResponse = request(properties.getUrl2(), properties.getMethod2(), contextBody, authToken, true);
        String contextToken = extractToken(contextResponse);

        log.info("ASUTP authorization completed");
        return contextToken == null || contextToken.isBlank() ? authToken : contextToken;
    }

    private Map<String, Object> buildDataRequestBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.putAll(properties.getDataBodyMap());

        String tagIdsField = properties.getDataTagIdsField();
        if (tagIdsField == null || tagIdsField.isBlank()) {
            tagIdsField = "tagIds";
        }

        // url_3 получает список нужных тегов одним массивом:
        // { "tagIds": ["список", "строковых", "тегов"] }
        body.put(tagIdsField, properties.getAllConfiguredTagIds());
        return body;
    }

    private Object request(String url, String method, Map<String, Object> body, String token, boolean basicBase64Token) {
        // Все запросы в АСУ ТП отправляются JSON-ом.
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null && !token.isBlank()) {
            /*
             * Второй запрос использует первый token как одноразовый пропуск:
             * кладем его в Authorization: Basic base64(token).
             * Третий запрос работает уже с token, который вернул url_2,
             * поэтому повторно Basic-кодировать его не нужно.
             */
            headers.set(tokenHeader(), basicBase64Token ? basicTokenHeaderValue(token) : token);
        }

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<Object> response = restTemplate.exchange(url, httpMethod(method), request, Object.class);
        return response.getBody();
    }

    private HttpMethod httpMethod(String method) {
        if (method == null || method.isBlank()) {
            return HttpMethod.POST;
        }
        return HttpMethod.valueOf(method.trim().toUpperCase());
    }

    private String tokenHeader() {
        String header = properties.getTokenHeader();
        return header == null || header.isBlank() ? "Authorization" : header;
    }

    private String basicTokenHeaderValue(String token) {
        // Basic-авторизация требует заголовок Authorization: Basic base64(token).
        return "Basic " + Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
    }

    private String extractToken(Object response) {
        // Если API вернул token простой JSON-строкой, используем ее напрямую.
        if (response instanceof String token) {
            return token;
        }

        // Разные версии API могут вернуть токен в разных полях, список полей настраивается.
        for (String field : properties.getTokenFieldList()) {
            Object token = extractPath(response, field);
            if (token != null) {
                return String.valueOf(token);
            }
        }
        return null;
    }

    private Map<String, Object> extractTagValues(Object response) {
        List<?> tags = extractTagList(response);
        Map<String, Object> values = new LinkedHashMap<>();

        for (Object item : tags) {
            // Не-map элементы игнорируем: они не похожи на описание тега.
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }

            Object tagId = firstPresent(map, properties.getTagIdFieldList());
            // Без id тега значение нельзя сопоставить с mixerId.
            if (tagId == null) {
                continue;
            }

            values.put(String.valueOf(tagId), map.get(properties.getTagValueField()));
        }

        return values;
    }

    private List<?> extractTagList(Object response) {
        // Иногда API сразу возвращает массив тегов.
        if (response instanceof List<?> list) {
            return list;
        }
        // Иногда массив лежит во вложенном поле: data/items/result и т.п.
        for (String field : properties.getTagListFieldList()) {
            Object data = extractPath(response, field);
            if (data instanceof List<?> list) {
                return list;
            }
        }
        return new ArrayList<>();
    }

    private Map<Integer, EquipmentState> toEquipmentStates(Map<String, Object> tagValues) {
        Map<Integer, String> gateTags = properties.getGateTagMap();
        Map<Integer, String> tiltTags = properties.getTiltTagMap();
        // Теги настроены как mixerId=tagId, поэтому на выходе появляются только известные миксеры.
        Set<Integer> mixerIds = new HashSet<>();
        mixerIds.addAll(gateTags.keySet());
        mixerIds.addAll(tiltTags.keySet());

        if (mixerIds.isEmpty()) {
            log.warn("ASUTP polling is enabled, but ASUTP_GATE_TAGS and ASUTP_TILT_TAGS are empty");
            return Map.of();
        }

        Map<Integer, EquipmentState> states = new LinkedHashMap<>();
        for (Integer mixerId : mixerIds) {
            String gateTag = gateTags.get(mixerId);
            String tiltTag = tiltTags.get(mixerId);
            boolean hasGate = gateTag != null && tagValues.containsKey(gateTag);
            boolean hasTilt = tiltTag != null && tagValues.containsKey(tiltTag);

            // Если по миксеру не пришел ни gate, ни tilt, обновлять нечего.
            if (!hasGate && !hasTilt) {
                continue;
            }

            /*
             * Если пришел только один из тегов, второе значение берем из дефолта,
             * чтобы состояние миксера всегда было полным: gateOpen + tilt.
             */
            boolean gateOpen = hasGate
                    ? isGateOpen(tagValues.get(gateTag))
                    : equipmentStateHolder.getDefaultState().gateOpen();
            boolean tilt = hasTilt
                    ? isTilted(tagValues.get(tiltTag))
                    : equipmentStateHolder.getDefaultState().tilt();

            states.put(mixerId, new EquipmentState(gateOpen, tilt));
        }

        return states;
    }

    private boolean isGateOpen(Object value) {
        return sameValue(value, properties.getGateOpenValue());
    }

    private boolean isTilted(Object value) {
        Double number = toDouble(value);
        if (number == null) {
            // Если значение не число, пробуем стандартный boolean-парсинг.
            return Boolean.parseBoolean(String.valueOf(value));
        }
        // Для чисел наклон считается true, если модуль значения больше порога.
        return Math.abs(number) > properties.getTiltThreshold();
    }

    private boolean sameValue(Object actual, String expected) {
        if (actual == null || expected == null) {
            return false;
        }

        // Если оба значения числовые, сравниваем как числа, а не как строки.
        Double actualNumber = toDouble(actual);
        Double expectedNumber = toDouble(expected);
        if (actualNumber != null && expectedNumber != null) {
            return Double.compare(actualNumber, expectedNumber) == 0;
        }

        return expected.equalsIgnoreCase(String.valueOf(actual).trim());
    }

    private Double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Object firstPresent(Map<?, ?> map, List<String> keys) {
        for (String key : keys) {
            if (map.containsKey(key)) {
                return map.get(key);
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

    private void putIfPresent(Map<String, Object> body, String field, String value) {
        if (field != null && !field.isBlank() && value != null && !value.isBlank()) {
            body.put(field, value);
        }
    }

    private void validateSettings() {
        require(properties.getUrl1(), "ASUTP_URL_1");
        require(properties.getUrl2(), "ASUTP_URL_2");
        require(properties.getUrl3(), "ASUTP_URL_3");
    }

    private void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is empty");
        }
    }
}
