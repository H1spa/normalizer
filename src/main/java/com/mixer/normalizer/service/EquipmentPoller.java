package com.mixer.normalizer.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
@ConditionalOnProperty(name = "equipment.polling.enabled", havingValue = "true")
public class EquipmentPoller {

    private static final Logger log = LoggerFactory.getLogger(EquipmentPoller.class);

    private final RestTemplate restTemplate = new RestTemplate();

    private final String pollingUrl;
    private final EventNormalizer normalizer;
    private final EquipmentStateHolder stateHolder;

    public EquipmentPoller(@Value("${equipment.polling.url}") String pollingUrl,
                           EventNormalizer normalizer,
                           EquipmentStateHolder stateHolder) {
        this.pollingUrl = pollingUrl;
        this.normalizer = normalizer;
        this.stateHolder = stateHolder;
    }

    @Scheduled(fixedDelayString = "${equipment.polling.interval-seconds:120}000")
    public void pollEquipment() {
        if (pollingUrl == null || pollingUrl.isBlank()) {
            log.warn("Equipment polling is enabled, but equipment.polling.url is empty");
            return;
        }

        try {
            log.debug("Polling equipment from {}", pollingUrl);

            Map<?, ?> response = restTemplate.getForObject(pollingUrl, Map.class);
            if (response == null) {
                return;
            }

            for (Map.Entry<?, ?> entry : response.entrySet()) {
                int mixerId = Integer.parseInt(String.valueOf(entry.getKey()));

                if (!(entry.getValue() instanceof Map<?, ?> data)) {
                    log.warn("Invalid equipment state for mixer {}: {}", mixerId, entry.getValue());
                    continue;
                }

                Object gateValue = data.get("gate");
                Object tiltValue = data.get("tilt");

                boolean gateOpen = "OPEN".equalsIgnoreCase(String.valueOf(gateValue));
                boolean tilt = Boolean.TRUE.equals(tiltValue);

                EquipmentStateHolder.EquipmentState oldState = stateHolder.getState(mixerId);

                if (oldState.gateOpen() != gateOpen || oldState.tilt() != tilt) {
                    log.info("Equipment changed for mixer {}: gate={}, tilt={}", mixerId, gateOpen, tilt);
                    normalizer.updateEquipmentFromPolling(mixerId, gateOpen, tilt);
                }
            }
        } catch (Exception e) {
            log.error("Failed to poll equipment", e);
        }
    }
}
