package com.mixer.normalizer.service;

import com.mixer.normalizer.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EventNormalizer {

    private static final Logger log = LoggerFactory.getLogger(EventNormalizer.class);
    private final OutputSender outputSender;
    private final OuterAnswerClient outerAnswerClient;

    // Локальное состояние оборудования (можно заменить на периодический опрос)
    private final Map<Integer, EquipmentState> equipmentStates = new ConcurrentHashMap<>();

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    public EventNormalizer(OutputSender outputSender, OuterAnswerClient outerAnswerClient) {
        this.outputSender = outputSender;
        this.outerAnswerClient = outerAnswerClient;
    }

    public enum OpType {
        INGOTS, FLUX, DISLAY, SCOOP, PROBA
    }

    private record EquipmentState(boolean gateOpen, boolean tilt) {}

    private LocalDateTime parseTime(String timeStr) {
        String normalized = timeStr.endsWith("z") ? timeStr.substring(0, timeStr.length() - 1) : timeStr;
        return LocalDateTime.parse(normalized, TIME_FORMATTER);
    }

    private String formatTime(LocalDateTime dt) {
        return dt.format(TIME_FORMATTER);
    }

    private EquipmentState getEquipmentState(int mixerId) {
        return equipmentStates.getOrDefault(mixerId, new EquipmentState(true, false));
    }

    public void updateEquipment(int mixerId, boolean gateOpen, boolean tilt) {
        equipmentStates.put(mixerId, new EquipmentState(gateOpen, tilt));
        log.info("Equipment updated for mixer {}: gate={}, tilt={}", mixerId, gateOpen, tilt);
        // Здесь можно добавить логику прерывания активных операций через вызов внешнего сервиса
    }

    public CreateEventResponse handleBegin(EventRequest request, OpType opType) {
        int mixerId = request.getMixerId();
        String rawTime = request.getTimeStamp();
        String folder = request.getFolder();

        if (!"begin".equalsIgnoreCase(request.getStatus())) {
            throw new IllegalArgumentException("Invalid status for begin");
        }

        LocalDateTime beginTime = parseTime(rawTime);
        String beginTimeStr = formatTime(beginTime);

        EquipmentState eq = getEquipmentState(mixerId);
        if (opType == OpType.FLUX || opType == OpType.DISLAY || opType == OpType.SCOOP) {
            if (!eq.gateOpen()) throw new IllegalStateException("Gate is CLOSED");
        }
        if (eq.tilt()) throw new IllegalStateException("Cannot begin when tilted");
        if (opType == OpType.FLUX && eq.tilt()) throw new IllegalStateException("Flux requires tilt false");

        String eventId = outerAnswerClient.createEvent(mixerId, beginTimeStr, folder);
        log.info("Begin {} for mixer {}, eventId={}, time={}", opType, mixerId, eventId, beginTimeStr);
        return new CreateEventResponse(eventId);
    }

    public void handleFinish(FinishEventRequest finishRequest) {
        String eventId = finishRequest.getEventId();
        String finishTimeStr = finishRequest.getTimeStamp();

        LocalDateTime finishTime = parseTime(finishTimeStr);
        String normalizedFinish = formatTime(finishTime);

        outerAnswerClient.finishEvent(eventId, normalizedFinish);
        log.info("Finished event {} at {}", eventId, normalizedFinish);
        // При необходимости можно отправить нормализованное событие в выходной поток,
        // но для этого нужно получить из внешнего сервиса данные операции (mixerId, type, begin, folder)
    }
}