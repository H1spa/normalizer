package com.mixer.normalizer.service;

import com.mixer.normalizer.audit.AuditCodes;
import com.mixer.normalizer.audit.service.AuditLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@ConditionalOnProperty(name = "equipment.polling.enabled", havingValue = "true")
public class EquipmentPoller {

    private static final Logger log = LoggerFactory.getLogger(EquipmentPoller.class);

    private final RestTemplate restTemplate;

    private final String pollingUrl;
    private final String legacyGateField;
    private final String legacyTiltField;
    private final String legacyGateOpenValue;
    // Храним уже нормализованный набор значений, которые означают tilt=true.
    private final Set<String> legacyTiltTrueValues;
    private final EventNormalizer normalizer;
    private final EquipmentStateHolder stateHolder;
    private final AsutpEquipmentClient asutpEquipmentClient;
    private final AuditLogService auditLogService;

    /*
     * Этот компонент создается только если equipment.polling.enabled=true.
     * @Scheduled ниже работает как таймер: Spring сам вызывает pollEquipment()
     * через заданный интервал и обновляет состояние оборудования в normalizer.
     */
    public EquipmentPoller(@Value("${equipment.polling.url}") String pollingUrl,
                           @Value("${equipment.polling.legacy-gate-field:gate}") String legacyGateField,
                           @Value("${equipment.polling.legacy-tilt-field:tilt}") String legacyTiltField,
                           @Value("${equipment.polling.legacy-gate-open-value:OPEN}") String legacyGateOpenValue,
                           @Value("${equipment.polling.legacy-tilt-true-values:true,1}") String legacyTiltTrueValues,
                           @Value("${equipment.polling.connect-timeout-millis:10000}") int connectTimeoutMillis,
                           @Value("${equipment.polling.read-timeout-millis:30000}") int readTimeoutMillis,
                           EventNormalizer normalizer,
                           EquipmentStateHolder stateHolder,
                           AsutpEquipmentClient asutpEquipmentClient,
                           AuditLogService auditLogService) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        // Таймауты нужны, чтобы зависший внешний источник не остановил scheduler надолго.
        requestFactory.setConnectTimeout(connectTimeoutMillis);
        requestFactory.setReadTimeout(readTimeoutMillis);
        this.restTemplate = new RestTemplate(requestFactory);
        this.pollingUrl = pollingUrl;
        this.legacyGateField = legacyGateField;
        this.legacyTiltField = legacyTiltField;
        this.legacyGateOpenValue = legacyGateOpenValue;
        this.legacyTiltTrueValues = parseExpectedValues(legacyTiltTrueValues);
        this.normalizer = normalizer;
        this.stateHolder = stateHolder;
        this.asutpEquipmentClient = asutpEquipmentClient;
        this.auditLogService = auditLogService;
    }

    @Scheduled(fixedDelayString = "${equipment.polling.interval-seconds:120}000")
    public void pollEquipment() {
        try (AuditLogService.AuditScope scope = auditLogService.beginSystemScope(
                AuditCodes.EVENT_POLL,
                AuditCodes.OPERATION_UNKNOWN)) {
            try {
                // Если включена АСУ ТП, берем состояние оттуда; иначе используем старый polling URL.
                Map<Integer, EquipmentStateHolder.EquipmentState> states = asutpEquipmentClient.isEnabled()
                        ? pollAsutp()
                        : pollLegacy();

                applyStates(states);
                auditLogService.log(
                        AuditCodes.COMPONENT_POLLER,
                        AuditCodes.ACTION_POLL_COMPLETED,
                        AuditCodes.INFO,
                        AuditCodes.SUCCESS);
                scope.success();
            } catch (Exception e) {
                // Ошибка одного polling-цикла не должна останавливать приложение.
                auditLogService.recordError(AuditCodes.COMPONENT_POLLER, e, null);
                log.error("Polling failed alias={} error={}",
                        AuditCodes.EVENT_POLL, e.getClass().getSimpleName());
            }
        }
    }

    // Опрос через полноценный клиент АСУ ТП.
    private Map<Integer, EquipmentStateHolder.EquipmentState> pollAsutp() {
        log.debug("Polling external source alias={}", AuditCodes.ENDPOINT_EQUIPMENT_DATA);
        return asutpEquipmentClient.pollStates();
    }

    // Старый формат: GET возвращает map вида mixerId -> { gate, tilt }.
    private Map<Integer, EquipmentStateHolder.EquipmentState> pollLegacy() {
        if (pollingUrl == null || pollingUrl.isBlank()) {
            log.warn("Equipment polling is enabled, but equipment.polling.url is empty");
            return Map.of();
        }

        log.debug("Polling external source alias={}", AuditCodes.ENDPOINT_EQUIPMENT_LEGACY);

        /*
         * Ожидаемый ответ legacy-источника:
         * {
         *   "123": { "gate": "OPEN", "tilt": false },
         *   "456": { "gate": "CLOSED", "tilt": true }
         * }
         */
        Instant startedAt = Instant.now();
        Map<?, ?> response;
        try {
            auditLogService.log(
                    AuditCodes.COMPONENT_EQUIPMENT,
                    AuditCodes.ACTION_HTTP_REQUEST,
                    AuditCodes.INFO,
                    AuditCodes.STARTED);
            ResponseEntity<Map> responseEntity = restTemplate.exchange(
                    pollingUrl,
                    HttpMethod.GET,
                    null,
                    Map.class);
            response = responseEntity.getBody();
            auditLogService.recordHttp(
                    AuditCodes.COMPONENT_EQUIPMENT,
                    AuditCodes.ENDPOINT_EQUIPMENT_LEGACY,
                    "OUTBOUND",
                    HttpMethod.GET.name(),
                    responseEntity.getStatusCode().value(),
                    null,
                    response,
                    null,
                    startedAt,
                    Instant.now());
        } catch (RestClientException e) {
            Integer status = e instanceof HttpStatusCodeException statusError
                    ? statusError.getStatusCode().value()
                    : null;
            auditLogService.recordHttp(
                    AuditCodes.COMPONENT_EQUIPMENT,
                    AuditCodes.ENDPOINT_EQUIPMENT_LEGACY,
                    "OUTBOUND",
                    HttpMethod.GET.name(),
                    status,
                    null,
                    null,
                    null,
                    startedAt,
                    Instant.now());
            auditLogService.recordError(AuditCodes.COMPONENT_EQUIPMENT, e, null);
            throw e;
        }
        if (response == null) {
            return Map.of();
        }

        Map<Integer, EquipmentStateHolder.EquipmentState> states = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : response.entrySet()) {
            Integer mixerId = parseMixerId(entry.getKey());
            if (mixerId == null) {
                log.warn("Invalid mixer id in external state alias={}", AuditCodes.ENDPOINT_EQUIPMENT_LEGACY);
                continue;
            }

            if (!(entry.getValue() instanceof Map<?, ?> data)) {
                log.warn("Invalid external state alias={} mixer={}", AuditCodes.ENDPOINT_EQUIPMENT_LEGACY, mixerId);
                continue;
            }

            Object gateValue = data.get(legacyGateField);
            Object tiltValue = data.get(legacyTiltField);

            // gate сравнивается с настроенным значением OPEN, tilt - со списком true-значений.
            boolean gateOpen = sameValue(gateValue, legacyGateOpenValue);
            boolean tilt = containsValue(legacyTiltTrueValues, tiltValue);

            states.put(mixerId, new EquipmentStateHolder.EquipmentState(gateOpen, tilt));
        }

        return states;
    }

    // Применяет только реальные изменения, чтобы не плодить одинаковые прерывания операций.
    private void applyStates(Map<Integer, EquipmentStateHolder.EquipmentState> states) {
        for (Map.Entry<Integer, EquipmentStateHolder.EquipmentState> entry : states.entrySet()) {
            int mixerId = entry.getKey();
            EquipmentStateHolder.EquipmentState state = entry.getValue();
            boolean knownState = stateHolder.hasState(mixerId);
            EquipmentStateHolder.EquipmentState oldState = stateHolder.getState(mixerId);

            /*
             * Первое состояние нужно записать даже если оно равно дефолту.
             * Повторные одинаковые состояния пропускаем, чтобы не дергать EventNormalizer без причины.
             */
            if (!knownState || oldState.gateOpen() != state.gateOpen() || oldState.tilt() != state.tilt()) {
                if (knownState) {
                    log.info("Equipment changed for mixer {}: gate={}, tilt={}", mixerId, state.gateOpen(), state.tilt());
                } else {
                    log.info("Equipment loaded for mixer {}: gate={}, tilt={}", mixerId, state.gateOpen(), state.tilt());
                }
                normalizer.updateEquipmentFromPolling(mixerId, state.gateOpen(), state.tilt());
            }
        }
    }

    // Сравнение строковых значений без учета регистра и пробелов.
    private boolean sameValue(Object actual, String expected) {
        if (actual == null || expected == null) {
            return false;
        }
        return expected.equalsIgnoreCase(String.valueOf(actual).trim());
    }

    // Проверяет, входит ли фактическое значение в список разрешенных значений.
    private boolean containsValue(Set<String> expectedValues, Object actual) {
        if (actual == null || expectedValues == null || expectedValues.isEmpty()) {
            return false;
        }

        return expectedValues.contains(normalizeValue(actual));
    }

    private Set<String> parseExpectedValues(String expectedValues) {
        // "true,1,YES" превращается в Set: ["true", "1", "yes"].
        if (expectedValues == null || expectedValues.isBlank()) {
            return Set.of();
        }
        return Stream.of(expectedValues.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    private String normalizeValue(Object value) {
        return String.valueOf(value).trim().toLowerCase(Locale.ROOT);
    }

    private Integer parseMixerId(Object value) {
        // Внешний JSON почти всегда дает ключи как строки, поэтому парсим аккуратно.
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (RuntimeException e) {
            return null;
        }
    }
}
