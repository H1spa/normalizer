package com.mixer.normalizer.service;

import com.mixer.normalizer.dto.EventRequest;
import com.mixer.normalizer.dto.OutputEvent;
import com.mixer.normalizer.service.EquipmentStateHolder.EquipmentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EventNormalizer {

    private static final Logger log = LoggerFactory.getLogger(EventNormalizer.class);

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final OuterAnswerClient outerAnswerClient;
    private final OutputSender outputSender;
    private final EquipmentStateHolder equipmentStateHolder;

    private final Map<Integer, MixerState> states = new ConcurrentHashMap<>();
    private final Map<String, EventRef> eventRefs = new ConcurrentHashMap<>();
    private final Map<String, String> recentlyFinished = new ConcurrentHashMap<>();

    public EventNormalizer(OuterAnswerClient outerAnswerClient,
                           OutputSender outputSender,
                           EquipmentStateHolder equipmentStateHolder) {
        this.outerAnswerClient = outerAnswerClient;
        this.outputSender = outputSender;
        this.equipmentStateHolder = equipmentStateHolder;
    }

    public enum OpType {
        INGOTS("ingots"),
        FLUX("flux"),
        DISLAY("dislay"),
        SCOOP("scoop"),
        PROBA("proba");

        private final String outputType;

        OpType(String outputType) {
            this.outputType = outputType;
        }

        public String outputType() {
            return outputType;
        }

        public boolean requiresGateOpen() {
            return this == FLUX || this == DISLAY || this == SCOOP;
        }
    }

    private enum Slot {
        MIXED,
        INGOTS,
        SCOOP,
        PROBA
    }

    private static final class Operation {
        final String eventId;
        final String externalEventId;
        final int mixerId;
        final OpType type;

        String beginTime;
        String finishTime;
        String folder;

        Operation(String eventId,
                  String externalEventId,
                  int mixerId,
                  OpType type,
                  String beginTime,
                  String folder) {
            this.eventId = eventId;
            this.externalEventId = externalEventId;
            this.mixerId = mixerId;
            this.type = type;
            this.beginTime = beginTime;
            this.folder = folder;
        }
    }

    private record EventRef(int mixerId, Slot slot) {
    }

    private static final class MixerState {
        Operation activeMixed;
        Operation activeIngots;
        Operation activeScoop;
        Operation activeProba;

        String lastCompletedFluxFinishTime;
        String lastFluxFolder;
    }

    public String handleBegin(EventRequest request, OpType opType) {
        int mixerId = request.getMixerId();
        String beginTime = normalizeTimestamp(request.getTimeStamp());
        String folder = request.getFolder();

        MixerState state = getState(mixerId);

        synchronized (state) {
            ensureEquipmentAllowsBegin(mixerId, opType);

            return switch (opType) {
                case INGOTS -> beginIngots(state, mixerId, beginTime, folder);
                case FLUX, DISLAY -> beginMixed(state, mixerId, opType, beginTime, folder);
                case SCOOP -> beginSimple(state, mixerId, OpType.SCOOP, Slot.SCOOP, beginTime, folder);
                case PROBA -> beginSimple(state, mixerId, OpType.PROBA, Slot.PROBA, beginTime, folder);
            };
        }
    }

    public void finishByEventId(String eventId, String rawFinishTime) {
        String finishTime = normalizeTimestamp(rawFinishTime);

        if (recentlyFinished.containsKey(eventId)) {
            log.info("Duplicate finish for already completed eventId={}, ignoring", eventId);
            return;
        }

        EventRef ref = eventRefs.get(eventId);
        if (ref == null) {
            throw new IllegalStateException("Finish without active begin: event_id=" + eventId);
        }

        MixerState state = getState(ref.mixerId());

        synchronized (state) {
            Operation operation = getOperationBySlot(state, ref.slot());

            if (operation == null || !operation.eventId.equals(eventId)) {
                eventRefs.remove(eventId);
                throw new IllegalStateException("Finish without active begin: event_id=" + eventId);
            }

            finishOperation(state, operation, ref.slot(), finishTime, true);
        }
    }

    public void updateEquipment(int mixerId, boolean gateOpen, boolean tilt) {
        updateEquipmentInternal(mixerId, gateOpen, tilt, "manual");
    }

    public void updateEquipmentFromPolling(int mixerId, boolean gateOpen, boolean tilt) {
        updateEquipmentInternal(mixerId, gateOpen, tilt, "polling");
    }

    private void updateEquipmentInternal(int mixerId, boolean gateOpen, boolean tilt, String source) {
        equipmentStateHolder.update(mixerId, gateOpen, tilt);

        MixerState state = getState(mixerId);

        synchronized (state) {
            String finishTime = format(LocalDateTime.now());

            if (tilt) {
                log.info("Mixer {} tilted from {}, interrupting all active operations", mixerId, source);

                forceFinish(state, state.activeMixed, Slot.MIXED, finishTime, "mixer tilted");
                forceFinish(state, state.activeIngots, Slot.INGOTS, finishTime, "mixer tilted");
                forceFinish(state, state.activeScoop, Slot.SCOOP, finishTime, "mixer tilted");
                forceFinish(state, state.activeProba, Slot.PROBA, finishTime, "mixer tilted");
                return;
            }

            if (!gateOpen) {
                log.info("Gate closed for mixer {} from {}, interrupting gate-sensitive operations", mixerId, source);

                forceFinish(state, state.activeMixed, Slot.MIXED, finishTime, "gate closed");
                forceFinish(state, state.activeScoop, Slot.SCOOP, finishTime, "gate closed");
            }
        }
    }

    private String beginIngots(MixerState state, int mixerId, String beginTime, String folder) {
        if (state.activeIngots != null) {
            updateBeginToEarlier(state.activeIngots, beginTime, folder);
            return state.activeIngots.eventId;
        }

        if (state.activeMixed != null) {
            log.info("Ingots begin discards active mixed operation eventId={}", state.activeMixed.eventId);
            dropOperation(state, state.activeMixed, Slot.MIXED, beginTime, "ingots priority");
        }

        Operation operation = createOperation(mixerId, OpType.INGOTS, Slot.INGOTS, beginTime, folder);
        state.activeIngots = operation;

        log.info("Begin ingots mixerId={}, eventId={}", mixerId, operation.eventId);
        return operation.eventId;
    }

    private String beginMixed(MixerState state, int mixerId, OpType requestedType, String beginTime, String folder) {
        if (state.activeIngots != null) {
            log.info("Ingots is active for mixer {}, ignoring {} begin", mixerId, requestedType);
            return null;
        }

        if (state.activeMixed != null) {
            Operation active = state.activeMixed;

            if (active.type == OpType.FLUX && requestedType == OpType.DISLAY) {
                updateBeginToEarlier(active, beginTime, folder);
                log.info("Converted dislay/slag begin into active flux, eventId={}", active.eventId);
                return active.eventId;
            }

            if (active.type == requestedType) {
                updateBeginToEarlier(active, beginTime, folder);
                log.info("Merged duplicated {} begin into active eventId={}", requestedType, active.eventId);
                return active.eventId;
            }

            throw new IllegalStateException("Cannot begin " + requestedType + " while active " + active.type + " is running");
        }

        if (requestedType == OpType.DISLAY
                && state.lastCompletedFluxFinishTime != null
                && state.lastFluxFolder != null) {
            emitSeparation(mixerId, state.lastCompletedFluxFinishTime, state.lastFluxFolder);
            state.lastCompletedFluxFinishTime = null;
            state.lastFluxFolder = null;
        }

        Operation operation = createOperation(mixerId, requestedType, Slot.MIXED, beginTime, folder);
        state.activeMixed = operation;

        log.info("Begin {} mixerId={}, eventId={}", requestedType, mixerId, operation.eventId);
        return operation.eventId;
    }

    private String beginSimple(MixerState state,
                               int mixerId,
                               OpType type,
                               Slot slot,
                               String beginTime,
                               String folder) {
        Operation active = getOperationBySlot(state, slot);

        if (active != null) {
            updateBeginToEarlier(active, beginTime, folder);
            log.info("Merged duplicated {} begin into active eventId={}", type, active.eventId);
            return active.eventId;
        }

        Operation operation = createOperation(mixerId, type, slot, beginTime, folder);
        setOperationBySlot(state, slot, operation);

        log.info("Begin {} mixerId={}, eventId={}", type, mixerId, operation.eventId);
        return operation.eventId;
    }

    private Operation createOperation(int mixerId, OpType type, Slot slot, String beginTime, String folder) {
        String localEventId = UUID.randomUUID().toString();
        String externalEventId = outerAnswerClient.createEvent(mixerId, beginTime, folder);

        Operation operation = new Operation(localEventId, externalEventId, mixerId, type, beginTime, folder);
        eventRefs.put(localEventId, new EventRef(mixerId, slot));

        return operation;
    }

    private void finishOperation(MixerState state,
                                 Operation operation,
                                 Slot slot,
                                 String finishTime,
                                 boolean emitOutput) {
        LocalDateTime begin = parse(operation.beginTime);
        LocalDateTime finish = parse(finishTime);

        if (finish.isBefore(begin)) {
            throw new IllegalArgumentException("Finish before begin");
        }

        operation.finishTime = finishTime;

        outerAnswerClient.finishEvent(operation.externalEventId, finishTime);

        if (emitOutput) {
            emitEvent(operation.mixerId, operation.type, operation.beginTime, finishTime, operation.folder);
        }

        if (operation.type == OpType.FLUX) {
            state.lastCompletedFluxFinishTime = finishTime;
            state.lastFluxFolder = operation.folder;
        }

        clearOperationBySlot(state, slot, operation.eventId);
        rememberFinished(operation.eventId, finishTime);

        log.info("Finished {} mixerId={}, eventId={}", operation.type, operation.mixerId, operation.eventId);
    }

    private void forceFinish(MixerState state,
                             Operation operation,
                             Slot slot,
                             String finishTime,
                             String reason) {
        if (operation == null) {
            return;
        }

        LocalDateTime begin = parse(operation.beginTime);
        LocalDateTime finish = parse(finishTime);

        if (finish.isBefore(begin)) {
            finishTime = operation.beginTime;
        }

        operation.finishTime = finishTime;

        outerAnswerClient.finishEvent(operation.externalEventId, finishTime);
        emitEvent(operation.mixerId, operation.type, operation.beginTime, finishTime, operation.folder);

        if (operation.type == OpType.FLUX) {
            state.lastCompletedFluxFinishTime = finishTime;
            state.lastFluxFolder = operation.folder;
        }

        clearOperationBySlot(state, slot, operation.eventId);
        rememberFinished(operation.eventId, finishTime);

        log.info("Interrupted {} mixerId={}, eventId={}, reason={}",
                operation.type, operation.mixerId, operation.eventId, reason);
    }

    private void dropOperation(MixerState state,
                               Operation operation,
                               Slot slot,
                               String finishTime,
                               String reason) {
        if (operation == null) {
            return;
        }

        LocalDateTime begin = parse(operation.beginTime);
        LocalDateTime finish = parse(finishTime);

        if (finish.isBefore(begin)) {
            finishTime = operation.beginTime;
        }

        // Закрываем outer-answer, но не отправляем событие в нормализованный выходной поток.
        outerAnswerClient.finishEvent(operation.externalEventId, finishTime);

        clearOperationBySlot(state, slot, operation.eventId);
        rememberFinished(operation.eventId, finishTime);

        log.info("Dropped {} mixerId={}, eventId={}, reason={}",
                operation.type, operation.mixerId, operation.eventId, reason);
    }

    private void emitSeparation(int mixerId, String time, String folder) {
        outputSender.send(new OutputEvent(mixerId, "separation", time, time, folder));
        log.info("Generated separation mixerId={}, time={}", mixerId, time);
    }

    private void emitEvent(int mixerId, OpType type, String begin, String finish, String folder) {
        outputSender.send(new OutputEvent(mixerId, type.outputType(), begin, finish, folder));
    }

    private void ensureEquipmentAllowsBegin(int mixerId, OpType opType) {
        EquipmentState equipment = equipmentStateHolder.getState(mixerId);

        if (equipment.tilt()) {
            throw new IllegalStateException("Cannot begin when mixer is tilted");
        }

        if (opType.requiresGateOpen() && !equipment.gateOpen()) {
            throw new IllegalStateException("Gate CLOSED");
        }
    }

    private void updateBeginToEarlier(Operation operation, String beginTime, String folder) {
        if (parse(beginTime).isBefore(parse(operation.beginTime))) {
            operation.beginTime = beginTime;
            operation.folder = folder;
        }
    }

    private MixerState getState(int mixerId) {
        return states.computeIfAbsent(mixerId, id -> new MixerState());
    }

    private Operation getOperationBySlot(MixerState state, Slot slot) {
        return switch (slot) {
            case MIXED -> state.activeMixed;
            case INGOTS -> state.activeIngots;
            case SCOOP -> state.activeScoop;
            case PROBA -> state.activeProba;
        };
    }

    private void setOperationBySlot(MixerState state, Slot slot, Operation operation) {
        switch (slot) {
            case MIXED -> state.activeMixed = operation;
            case INGOTS -> state.activeIngots = operation;
            case SCOOP -> state.activeScoop = operation;
            case PROBA -> state.activeProba = operation;
        }
    }

    private void clearOperationBySlot(MixerState state, Slot slot, String eventId) {
        Operation current = getOperationBySlot(state, slot);

        if (current != null && current.eventId.equals(eventId)) {
            setOperationBySlot(state, slot, null);
        }

        eventRefs.remove(eventId);
    }

    private void rememberFinished(String eventId, String finishTime) {
        recentlyFinished.put(eventId, finishTime);

        // Простая защита от бесконечного роста кэша идемпотентности.
        if (recentlyFinished.size() > 10_000) {
            int removed = 0;
            for (String key : recentlyFinished.keySet()) {
                recentlyFinished.remove(key);
                removed++;
                if (removed >= 1_000) {
                    break;
                }
            }
        }
    }

    private String normalizeTimestamp(String timestamp) {
        return format(parse(timestamp));
    }

    private LocalDateTime parse(String timestamp) {
        String normalized = timestamp;

        if (normalized.endsWith("z") || normalized.endsWith("Z")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return LocalDateTime.parse(normalized, FORMATTER);
    }

    private String format(LocalDateTime dateTime) {
        return dateTime.format(FORMATTER);
    }
}
