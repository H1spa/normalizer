package com.mixer.normalizer.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
@ConditionalOnProperty(name = "equipment.polling.enabled", havingValue = "true")
public class EquipmentPoller {

    private static final Logger log = LoggerFactory.getLogger(EquipmentPoller.class);

    private final RestTemplate restTemplate;

    private final String pollingUrl;
    private final String legacyGateField;
    private final String legacyTiltField;
    private final String legacyGateOpenValue;
    private final String legacyTiltTrueValues;
    private final EventNormalizer normalizer;
    private final EquipmentStateHolder stateHolder;
    private final AsutpEquipmentClient asutpEquipmentClient;

    public EquipmentPoller(@Value("${equipment.polling.url}") String pollingUrl,
                           @Value("${equipment.polling.legacy-gate-field:gate}") String legacyGateField,
                           @Value("${equipment.polling.legacy-tilt-field:tilt}") String legacyTiltField,
                           @Value("${equipment.polling.legacy-gate-open-value:OPEN}") String legacyGateOpenValue,
                           @Value("${equipment.polling.legacy-tilt-true-values:true,1}") String legacyTiltTrueValues,
                           @Value("${equipment.polling.connect-timeout-millis:10000}") int connectTimeoutMillis,
                           @Value("${equipment.polling.read-timeout-millis:30000}") int readTimeoutMillis,
                           EventNormalizer normalizer,
                           EquipmentStateHolder stateHolder,
                           AsutpEquipmentClient asutpEquipmentClient) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMillis);
        requestFactory.setReadTimeout(readTimeoutMillis);
        this.restTemplate = new RestTemplate(requestFactory);
        this.pollingUrl = pollingUrl;
        this.legacyGateField = legacyGateField;
        this.legacyTiltField = legacyTiltField;
        this.legacyGateOpenValue = legacyGateOpenValue;
        this.legacyTiltTrueValues = legacyTiltTrueValues;
        this.normalizer = normalizer;
        this.stateHolder = stateHolder;
        this.asutpEquipmentClient = asutpEquipmentClient;
    }

    @Scheduled(fixedDelayString = "${equipment.polling.interval-seconds:120}000")
    public void pollEquipment() {
        try {
            Map<Integer, EquipmentStateHolder.EquipmentState> states = asutpEquipmentClient.isEnabled()
                    ? pollAsutp()
                    : pollLegacy();

            applyStates(states);
        } catch (Exception e) {
            log.error("Failed to poll equipment", e);
        }
    }

    private Map<Integer, EquipmentStateHolder.EquipmentState> pollAsutp() {
        log.debug("Polling equipment from ASUTP");
        return asutpEquipmentClient.pollStates();
    }

    private Map<Integer, EquipmentStateHolder.EquipmentState> pollLegacy() {
        if (pollingUrl == null || pollingUrl.isBlank()) {
            log.warn("Equipment polling is enabled, but equipment.polling.url is empty");
            return Map.of();
        }

        log.debug("Polling equipment from {}", pollingUrl);

        Map<?, ?> response = restTemplate.getForObject(pollingUrl, Map.class);
        if (response == null) {
            return Map.of();
        }

        Map<Integer, EquipmentStateHolder.EquipmentState> states = new java.util.LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : response.entrySet()) {
            int mixerId = Integer.parseInt(String.valueOf(entry.getKey()));

            if (!(entry.getValue() instanceof Map<?, ?> data)) {
                log.warn("Invalid equipment state for mixer {}: {}", mixerId, entry.getValue());
                continue;
            }

            Object gateValue = data.get(legacyGateField);
            Object tiltValue = data.get(legacyTiltField);

            boolean gateOpen = sameValue(gateValue, legacyGateOpenValue);
            boolean tilt = containsValue(legacyTiltTrueValues, tiltValue);

            states.put(mixerId, new EquipmentStateHolder.EquipmentState(gateOpen, tilt));
        }

        return states;
    }

    private void applyStates(Map<Integer, EquipmentStateHolder.EquipmentState> states) {
        for (Map.Entry<Integer, EquipmentStateHolder.EquipmentState> entry : states.entrySet()) {
            int mixerId = entry.getKey();
            EquipmentStateHolder.EquipmentState state = entry.getValue();
            EquipmentStateHolder.EquipmentState oldState = stateHolder.getState(mixerId);

            if (oldState.gateOpen() != state.gateOpen() || oldState.tilt() != state.tilt()) {
                log.info("Equipment changed for mixer {}: gate={}, tilt={}", mixerId, state.gateOpen(), state.tilt());
                normalizer.updateEquipmentFromPolling(mixerId, state.gateOpen(), state.tilt());
            }
        }
    }

    private boolean sameValue(Object actual, String expected) {
        if (actual == null || expected == null) {
            return false;
        }
        return expected.equalsIgnoreCase(String.valueOf(actual).trim());
    }

    private boolean containsValue(String expectedValues, Object actual) {
        if (actual == null || expectedValues == null) {
            return false;
        }

        String actualText = String.valueOf(actual).trim();
        for (String expected : expectedValues.split(",")) {
            if (expected.trim().equalsIgnoreCase(actualText)) {
                return true;
            }
        }
        return false;
    }
}
