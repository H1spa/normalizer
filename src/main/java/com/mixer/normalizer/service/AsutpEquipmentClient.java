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

@Component
public class AsutpEquipmentClient {

    private static final Logger log = LoggerFactory.getLogger(AsutpEquipmentClient.class);

    private final RestTemplate restTemplate;
    private final AsutpProperties properties;
    private final EquipmentStateHolder equipmentStateHolder;

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
        if (!properties.isEnabled()) {
            return Map.of();
        }

        try {
            return pollWithToken(ensureDataToken());
        } catch (HttpClientErrorException.Unauthorized e) {
            log.info("ASUTP token expired, refreshing");
            dataToken = null;
            return pollWithToken(ensureDataToken());
        }
    }

    private Map<Integer, EquipmentState> pollWithToken(String token) {
        Object response = request(properties.getUrl3(), properties.getMethod3(), properties.getDataBodyMap(), token);
        Map<String, Object> tagValues = extractTagValues(response);
        return toEquipmentStates(tagValues);
    }

    private String ensureDataToken() {
        if (dataToken == null || dataToken.isBlank()) {
            dataToken = authenticate();
        }
        return dataToken;
    }

    private String authenticate() {
        validateSettings();

        Map<String, Object> authBody = new LinkedHashMap<>();
        authBody.putAll(properties.getAuthBodyMap());
        putIfPresent(authBody, properties.getDomainNameField(), properties.getDomainName());
        putIfPresent(authBody, properties.getPasswordField(), properties.getPassword());

        Object authResponse = request(properties.getUrl1(), properties.getMethod1(), authBody, null);
        String authToken = extractToken(authResponse);
        if (authToken == null || authToken.isBlank()) {
            throw new IllegalStateException("ASUTP url_1 did not return token");
        }

        Map<String, Object> contextBody = new LinkedHashMap<>();
        contextBody.putAll(properties.getContextBodyMap());
        putIfPresent(contextBody, properties.getDomainNameField(), properties.getDomainName());
        putIfPresent(contextBody, properties.getLastHouseIdField(), properties.getLastHouseId());

        Object contextResponse = request(properties.getUrl2(), properties.getMethod2(), contextBody, authToken);
        String contextToken = extractToken(contextResponse);

        log.info("ASUTP authorization completed");
        return contextToken == null || contextToken.isBlank() ? authToken : contextToken;
    }

    private Object request(String url, String method, Map<String, Object> body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null && !token.isBlank()) {
            headers.set(tokenHeader(), tokenHeaderValue(token));
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

    private String tokenHeaderValue(String token) {
        String scheme = properties.getTokenScheme();
        String value = shouldBase64EncodeToken(scheme)
                ? Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8))
                : token;
        if (scheme == null || scheme.isBlank()) {
            return value;
        }
        return scheme.trim() + " " + value;
    }

    private boolean shouldBase64EncodeToken(String scheme) {
        return properties.isTokenBase64Encode()
                || (scheme != null && "Basic".equalsIgnoreCase(scheme.trim()));
    }

    private String extractToken(Object response) {
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
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }

            Object tagId = firstPresent(map, properties.getTagIdFieldList());
            if (tagId == null) {
                continue;
            }

            values.put(String.valueOf(tagId), map.get(properties.getTagValueField()));
        }

        return values;
    }

    private List<?> extractTagList(Object response) {
        if (response instanceof List<?> list) {
            return list;
        }
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

            if (!hasGate && !hasTilt) {
                continue;
            }

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
            return Boolean.parseBoolean(String.valueOf(value));
        }
        return Math.abs(number) > properties.getTiltThreshold();
    }

    private boolean sameValue(Object actual, String expected) {
        if (actual == null || expected == null) {
            return false;
        }

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
        require(properties.getDomainName(), "ASUTP_DOMAIN_NAME");
        require(properties.getPassword(), "ASUTP_PASSWORD");
        require(properties.getLastHouseId(), "ASUTP_LAST_HOUSE_ID");
    }

    private void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is empty");
        }
    }
}
