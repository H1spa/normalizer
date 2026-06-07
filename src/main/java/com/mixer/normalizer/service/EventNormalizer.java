package com.mixer.normalizer.service;

import com.mixer.normalizer.dto.EventRequest;
import com.mixer.normalizer.dto.OutputEvent;
import com.mixer.normalizer.service.EquipmentStateHolder.EquipmentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.TemporalAccessor;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EventNormalizer {

    private static final Logger log = LoggerFactory.getLogger(EventNormalizer.class);

    // Фиксированный часовой пояс +07:00
    private static final ZoneId FIXED_ZONE = ZoneId.of("+07:00");
    private static final DateTimeFormatter STORAGE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final DateTimeFormatter OUTER_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ssxx");

    private final OuterAnswerClient outerAnswerClient;
    private final OutputSender outputSender;
    private final EquipmentStateHolder equipmentStateHolder;

    private final Map<Integer, MixerState> states = new ConcurrentHashMap<>();

    @Value("${equipment.check-enabled:true}")
    private boolean equipmentCheckEnabled;

    public EventNormalizer(OuterAnswerClient outerAnswerClient,
                           OutputSender outputSender,
                           EquipmentStateHolder equipmentStateHolder) {
        this.outerAnswerClient = outerAnswerClient;
        this.outputSender = outputSender;
        this.equipmentStateHolder = equipmentStateHolder;
    }

    public enum OpType {
        INGOTS, FLUX, DISLAY, SCOOP, PROBA
    }

    private static class ActiveOperation {
        String beginTime;
        String folder;
        String finishTime;
        String externalId;
        ActiveOperation(String beginTime, String folder) {
            this.beginTime = beginTime;
            this.folder = folder;
        }
    }

    private static class MixedOperation {
        OpType type;
        String beginTime;
        String folder;
        String finishTime;
        String externalId;
        MixedOperation(OpType type, String beginTime, String folder) {
            this.type = type;
            this.beginTime = beginTime;
            this.folder = folder;
        }
    }

    private static class MixerState {
        MixedOperation activeMixed;
        ActiveOperation activeIngots;
        ActiveOperation pendingScoop;
        ActiveOperation pendingProba;
        String lastCompletedFluxFinishTime;
        String lastFluxFolder;
    }

    // ---------- Парсинг и форматирование времени ----------
    private ZonedDateTime parseZoned(String timestamp) {
        String cleaned = timestamp.trim().toLowerCase();
        if (cleaned.endsWith("z")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        DateTimeFormatter formatter = new DateTimeFormatterBuilder()
                .appendPattern("yyyy-MM-dd_HH-mm-ss")
                .optionalStart()
                .appendPattern("xx")
                .optionalEnd()
                .toFormatter();
        TemporalAccessor parsed = formatter.parseBest(cleaned, ZonedDateTime::from, LocalDateTime::from);
        ZonedDateTime zdt;
        if (parsed instanceof ZonedDateTime) {
            zdt = (ZonedDateTime) parsed;
        } else {
            zdt = ((LocalDateTime) parsed).atZone(ZoneOffset.UTC);
        }
        return zdt.withZoneSameInstant(FIXED_ZONE);
    }

    private String formatStorage(ZonedDateTime zdt) {
        return zdt.format(STORAGE_FORMATTER);
    }

    private String formatOuter(ZonedDateTime zdt) {
        return zdt.format(OUTER_FORMATTER);
    }

    private ZonedDateTime toZonedDateTime(String storageTime) {
        LocalDateTime ldt = LocalDateTime.parse(storageTime, STORAGE_FORMATTER);
        return ldt.atZone(FIXED_ZONE);
    }

    private MixerState getState(int mixerId) {
        return states.computeIfAbsent(mixerId, id -> new MixerState());
    }

    private void emitEvent(int mixerId, OpType type, String begin, String finish, String folder) {
        String typeStr;
        switch (type) {
            case FLUX: typeStr = "flux"; break;
            case DISLAY: typeStr = "dislay"; break;
            case INGOTS: typeStr = "ingots"; break;
            case SCOOP: typeStr = "scoop"; break;
            case PROBA: typeStr = "proba"; break;
            default: throw new IllegalArgumentException();
        }
        OutputEvent event = new OutputEvent(mixerId, typeStr, begin, finish, folder);
        outputSender.send(event);
        log.info("Emitted event: {}", event);
    }

    // ---------- Оборудование ----------
    public void updateEquipment(int mixerId, boolean gateOpen, boolean tilt) {
        equipmentStateHolder.update(mixerId, gateOpen, tilt);
        if (!equipmentCheckEnabled) {
            log.debug("Equipment checks disabled, skipping interrupt for mixer {}", mixerId);
            return;
        }
        MixerState state = getState(mixerId);
        synchronized (state) {
            ZonedDateTime now = ZonedDateTime.now(FIXED_ZONE);
            String finishTime = formatStorage(now);
            if (tilt) {
                interruptMixed(state, mixerId, finishTime, "mixer tilted");
                interruptScoop(state, mixerId, finishTime, "mixer tilted");
            } else if (!gateOpen) {
                interruptMixed(state, mixerId, finishTime, "gate closed");
                interruptScoop(state, mixerId, finishTime, "gate closed");
            }
        }
    }

    public void updateEquipmentFromPolling(int mixerId, boolean gateOpen, boolean tilt) {
        updateEquipment(mixerId, gateOpen, tilt);
    }

    private void interruptMixed(MixerState state, int mixerId, String finishTime, String reason) {
        if (state.activeMixed != null && state.activeMixed.finishTime == null) {
            state.activeMixed.finishTime = finishTime;
            if (state.activeMixed.externalId != null) {
                ZonedDateTime finishZdt = toZonedDateTime(finishTime);
                outerAnswerClient.finishEvent(state.activeMixed.externalId, formatOuter(finishZdt));
            }
            emitEvent(mixerId, state.activeMixed.type, state.activeMixed.beginTime, finishTime, state.activeMixed.folder);
            if (state.activeMixed.type == OpType.FLUX) {
                state.lastCompletedFluxFinishTime = finishTime;
                state.lastFluxFolder = state.activeMixed.folder;
            }
            state.activeMixed = null;
            log.info("Interrupted mixed due to {}", reason);
        }
    }

    private void interruptScoop(MixerState state, int mixerId, String finishTime, String reason) {
        if (state.pendingScoop != null && state.pendingScoop.finishTime == null) {
            state.pendingScoop.finishTime = finishTime;
            if (state.pendingScoop.externalId != null) {
                ZonedDateTime finishZdt = toZonedDateTime(finishTime);
                outerAnswerClient.finishEvent(state.pendingScoop.externalId, formatOuter(finishZdt));
            }
            emitEvent(mixerId, OpType.SCOOP, state.pendingScoop.beginTime, finishTime, state.pendingScoop.folder);
            state.pendingScoop = null;
            log.info("Interrupted scoop due to {}", reason);
        }
    }

    // ---------- Обработка begin ----------
    public void handleBegin(EventRequest request, OpType opType) {
        int mixerId = request.getMixerId();
        MixerState state = getState(mixerId);
        synchronized (state) {
            EquipmentState eq = equipmentStateHolder.getState(mixerId);

            if (equipmentCheckEnabled) {
                if (opType == OpType.FLUX || opType == OpType.DISLAY || opType == OpType.SCOOP) {
                    if (!eq.gateOpen()) throw new IllegalStateException("Gate CLOSED");
                }
                if (eq.tilt()) throw new IllegalStateException("Cannot begin when tilted");
                if (opType == OpType.FLUX && eq.tilt()) throw new IllegalStateException("Flux requires tilt false");
            } else {
                log.debug("Equipment checks disabled for mixer {}, proceeding without gate/tilt validation", mixerId);
            }

            ZonedDateTime beginZdt = parseZoned(request.getTimeStamp());
            String beginTimeStr = formatStorage(beginZdt);
            String folder = request.getFolder();

            switch (opType) {
                case INGOTS:
                    handleIngotsBegin(state, mixerId, beginTimeStr, folder, beginZdt);
                    break;
                case FLUX:
                case DISLAY:
                    handleMixedBegin(state, mixerId, opType, beginTimeStr, folder, beginZdt);
                    break;
                case SCOOP:
                    handleScoopBegin(state, mixerId, beginTimeStr, folder, beginZdt);
                    break;
                case PROBA:
                    handleProbaBegin(state, mixerId, beginTimeStr, folder, beginZdt);
                    break;
            }
        }
    }

    // ---------- Обработка finish ----------
    public void handleFinish(EventRequest request, OpType opType) {
        int mixerId = request.getMixerId();
        MixerState state = getState(mixerId);
        synchronized (state) {
            ZonedDateTime finishZdt = parseZoned(request.getTimeStamp());
            String finishTimeStr = formatStorage(finishZdt);

            switch (opType) {
                case INGOTS:
                    handleIngotsFinish(state, mixerId, finishTimeStr, finishZdt);
                    break;
                case FLUX:
                case DISLAY:
                    handleMixedFinish(state, mixerId, opType, finishTimeStr, finishZdt);
                    break;
                case SCOOP:
                    handleScoopFinish(state, mixerId, finishTimeStr, finishZdt);
                    break;
                case PROBA:
                    handleProbaFinish(state, mixerId, finishTimeStr, finishZdt);
                    break;
            }
        }
    }

    // ---------- INGOTS ----------
    private void handleIngotsBegin(MixerState state, int mixerId, String timeStr, String folder, ZonedDateTime beginZdt) {
        if (state.activeIngots == null || state.activeIngots.finishTime != null) {
            state.activeIngots = new ActiveOperation(timeStr, folder);
            String externalId = outerAnswerClient.createEvent(mixerId, formatOuter(beginZdt), folder);
            state.activeIngots.externalId = externalId;
            log.info("Begin ingots, externalId={}", externalId);
            if (state.activeMixed != null) {
                log.info("Ingots discarding active mixed");
                state.activeMixed = null;
            }
        } else {
            ZonedDateTime existingBegin = toZonedDateTime(state.activeIngots.beginTime);
            if (beginZdt.isBefore(existingBegin)) {
                state.activeIngots.beginTime = timeStr;
                state.activeIngots.folder = folder;
                log.info("Updated ingots begin to earlier {}", timeStr);
            }
        }
    }

    private void handleIngotsFinish(MixerState state, int mixerId, String finishStr, ZonedDateTime finishZdt) {
        if (state.activeIngots == null || state.activeIngots.finishTime != null) {
            throw new IllegalStateException("Finish ingots without active begin");
        }
        ZonedDateTime beginZdt = toZonedDateTime(state.activeIngots.beginTime);
        if (finishZdt.isBefore(beginZdt)) throw new IllegalStateException("Finish before begin");
        if (state.activeIngots.finishTime == null || finishZdt.isAfter(toZonedDateTime(state.activeIngots.finishTime))) {
            state.activeIngots.finishTime = finishStr;
        }
        if (state.activeIngots.externalId != null) {
            outerAnswerClient.finishEvent(state.activeIngots.externalId, formatOuter(finishZdt));
        }
        emitEvent(mixerId, OpType.INGOTS, state.activeIngots.beginTime, state.activeIngots.finishTime, state.activeIngots.folder);
        state.activeIngots = null;
        log.info("Finished ingots");
    }

    // ---------- MIXED ----------
    private void handleMixedBegin(MixerState state, int mixerId, OpType opType, String timeStr, String folder, ZonedDateTime beginZdt) {
        if (state.activeIngots != null && state.activeIngots.finishTime == null) {
            log.info("Ingots active, ignoring mixed begin");
            return;
        }
        if (state.activeMixed != null && state.activeMixed.finishTime == null) {
            // Конверсия slag -> flux
            if (state.activeMixed.type == OpType.FLUX && opType == OpType.DISLAY) {
                ZonedDateTime existingBegin = toZonedDateTime(state.activeMixed.beginTime);
                if (beginZdt.isBefore(existingBegin)) {
                    state.activeMixed.beginTime = timeStr;
                    log.info("Converted slag begin to flux, updated begin to {}", timeStr);
                }
                return;
            }
            if (state.activeMixed.type == opType) {
                ZonedDateTime existingBegin = toZonedDateTime(state.activeMixed.beginTime);
                if (beginZdt.isBefore(existingBegin)) {
                    state.activeMixed.beginTime = timeStr;
                    state.activeMixed.folder = folder;
                    log.info("Updated {} begin to earlier {}", opType, timeStr);
                }
                return;
            }
            log.warn("Cannot begin {} while active {} is running", opType, state.activeMixed.type);
            return;
        }

        // Нет активной mixed операции
        if (opType == OpType.DISLAY) {
            if (state.lastCompletedFluxFinishTime != null && state.lastFluxFolder != null) {
                ZonedDateTime sepZdt = toZonedDateTime(state.lastCompletedFluxFinishTime);
                String externalId = outerAnswerClient.createEvent(mixerId, formatOuter(sepZdt), state.lastFluxFolder);
                outerAnswerClient.finishEvent(externalId, formatOuter(sepZdt));
                OutputEvent sepEvent = new OutputEvent(mixerId, "separation", state.lastCompletedFluxFinishTime, state.lastCompletedFluxFinishTime, state.lastFluxFolder);
                outputSender.send(sepEvent);
                log.info("Generated separation event, recorded in outer-answer, externalId={}", externalId);
                state.lastCompletedFluxFinishTime = null;
                state.lastFluxFolder = null;
            }
            state.activeMixed = new MixedOperation(OpType.DISLAY, timeStr, folder);
        } else {
            state.activeMixed = new MixedOperation(OpType.FLUX, timeStr, folder);
        }
        String externalId = outerAnswerClient.createEvent(mixerId, formatOuter(beginZdt), folder);
        state.activeMixed.externalId = externalId;
        log.info("Begin {} externalId={}", opType, externalId);
    }

    private void handleMixedFinish(MixerState state, int mixerId, OpType opType, String finishStr, ZonedDateTime finishZdt) {
        if (state.activeIngots != null && state.activeIngots.finishTime == null) {
            log.info("Ingots active, ignoring mixed finish");
            return;
        }
        if (state.activeMixed == null || state.activeMixed.finishTime != null) {
            throw new IllegalStateException("Finish " + opType + " without active mixed operation");
        }
        if (opType == OpType.DISLAY && state.activeMixed.type == OpType.FLUX) {
            log.info("Finishing dislay converts to flux finish");
        } else if (opType != state.activeMixed.type) {
            throw new IllegalStateException("Finish type mismatch: active " + state.activeMixed.type + ", received " + opType);
        }
        ZonedDateTime beginZdt = toZonedDateTime(state.activeMixed.beginTime);
        if (finishZdt.isBefore(beginZdt)) throw new IllegalStateException("Finish before begin");
        if (state.activeMixed.finishTime == null || finishZdt.isAfter(toZonedDateTime(state.activeMixed.finishTime))) {
            state.activeMixed.finishTime = finishStr;
        }
        if (state.activeMixed.externalId != null) {
            outerAnswerClient.finishEvent(state.activeMixed.externalId, formatOuter(finishZdt));
        }
        emitEvent(mixerId, state.activeMixed.type, state.activeMixed.beginTime, state.activeMixed.finishTime, state.activeMixed.folder);
        if (state.activeMixed.type == OpType.FLUX) {
            state.lastCompletedFluxFinishTime = state.activeMixed.finishTime;
            state.lastFluxFolder = state.activeMixed.folder;
        }
        state.activeMixed = null;
        log.info("Finished {} for mixer {}", opType, mixerId);
    }

    // ---------- SCOOP ----------
    private void handleScoopBegin(MixerState state, int mixerId, String timeStr, String folder, ZonedDateTime beginZdt) {
        if (state.pendingScoop == null || state.pendingScoop.finishTime != null) {
            state.pendingScoop = new ActiveOperation(timeStr, folder);
            String externalId = outerAnswerClient.createEvent(mixerId, formatOuter(beginZdt), folder);
            state.pendingScoop.externalId = externalId;
            log.info("Begin scoop externalId={}", externalId);
        } else {
            ZonedDateTime existingBegin = toZonedDateTime(state.pendingScoop.beginTime);
            if (beginZdt.isBefore(existingBegin)) {
                state.pendingScoop.beginTime = timeStr;
                state.pendingScoop.folder = folder;
                log.info("Updated scoop begin to earlier {}", timeStr);
            }
        }
    }

    private void handleScoopFinish(MixerState state, int mixerId, String finishStr, ZonedDateTime finishZdt) {
        if (state.pendingScoop == null || state.pendingScoop.finishTime != null) {
            throw new IllegalStateException("Finish scoop without active begin");
        }
        ZonedDateTime beginZdt = toZonedDateTime(state.pendingScoop.beginTime);
        if (finishZdt.isBefore(beginZdt)) throw new IllegalStateException("Finish before begin");
        if (state.pendingScoop.finishTime == null || finishZdt.isAfter(toZonedDateTime(state.pendingScoop.finishTime))) {
            state.pendingScoop.finishTime = finishStr;
        }
        if (state.pendingScoop.externalId != null) {
            outerAnswerClient.finishEvent(state.pendingScoop.externalId, formatOuter(finishZdt));
        }
        emitEvent(mixerId, OpType.SCOOP, state.pendingScoop.beginTime, state.pendingScoop.finishTime, state.pendingScoop.folder);
        state.pendingScoop = null;
        log.info("Finished scoop");
    }

    // ---------- PROBA ----------
    private void handleProbaBegin(MixerState state, int mixerId, String timeStr, String folder, ZonedDateTime beginZdt) {
        if (state.pendingProba == null || state.pendingProba.finishTime != null) {
            state.pendingProba = new ActiveOperation(timeStr, folder);
            String externalId = outerAnswerClient.createEvent(mixerId, formatOuter(beginZdt), folder);
            state.pendingProba.externalId = externalId;
            log.info("Begin proba externalId={}", externalId);
        } else {
            ZonedDateTime existingBegin = toZonedDateTime(state.pendingProba.beginTime);
            if (beginZdt.isBefore(existingBegin)) {
                state.pendingProba.beginTime = timeStr;
                state.pendingProba.folder = folder;
                log.info("Updated proba begin to earlier {}", timeStr);
            }
        }
    }

    private void handleProbaFinish(MixerState state, int mixerId, String finishStr, ZonedDateTime finishZdt) {
        if (state.pendingProba == null || state.pendingProba.finishTime != null) {
            throw new IllegalStateException("Finish proba without active begin");
        }
        ZonedDateTime beginZdt = toZonedDateTime(state.pendingProba.beginTime);
        if (finishZdt.isBefore(beginZdt)) throw new IllegalStateException("Finish before begin");
        if (state.pendingProba.finishTime == null || finishZdt.isAfter(toZonedDateTime(state.pendingProba.finishTime))) {
            state.pendingProba.finishTime = finishStr;
        }
        if (state.pendingProba.externalId != null) {
            outerAnswerClient.finishEvent(state.pendingProba.externalId, formatOuter(finishZdt));
        }
        emitEvent(mixerId, OpType.PROBA, state.pendingProba.beginTime, state.pendingProba.finishTime, state.pendingProba.folder);
        state.pendingProba = null;
        log.info("Finished proba");
    }
}