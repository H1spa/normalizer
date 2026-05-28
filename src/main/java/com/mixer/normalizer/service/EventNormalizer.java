package com.mixer.normalizer.service;

import com.mixer.normalizer.dto.EventRequest;
import com.mixer.normalizer.dto.OutputEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EventNormalizer {

    private static final Logger log = LoggerFactory.getLogger(EventNormalizer.class);
    private final Map<Integer, MixerState> states = new ConcurrentHashMap<>();
    private final OutputSender outputSender;

    private static final DateTimeFormatter TIME_FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd_HH-mm-ss")
            .optionalStart()
            .appendLiteral('z')
            .optionalEnd()
            .toFormatter();

    public EventNormalizer(OutputSender outputSender) {
        this.outputSender = outputSender;
    }

    public enum OpType {
        INGOTS, FLUX, DISLAY, SCOOP, PROBA
    }

    private static class ActiveOperation {
        String beginTime;
        String folder;
        String finishTime;
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
        MixedOperation(OpType type, String beginTime, String folder) {
            this.type = type;
            this.beginTime = beginTime;
            this.folder = folder;
        }
    }

    private static class MixerState {
        boolean gateOpen = true;
        boolean tilt = false;
        MixedOperation activeMixed;
        ActiveOperation activeIngots;
        ActiveOperation pendingScoop;
        ActiveOperation pendingProba;
        String lastCompletedFluxFinishTime;
        String lastFluxFolder;
    }

    private LocalDateTime parseTime(String timeStr) {
        String normalized = timeStr.endsWith("z") ? timeStr.substring(0, timeStr.length() - 1) : timeStr;
        return LocalDateTime.parse(normalized, DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
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
            default: throw new IllegalArgumentException("Unknown type: " + type);
        }
        OutputEvent event = new OutputEvent(mixerId, typeStr, begin, finish, folder);
        outputSender.send(event);
        log.info("Emitted event: {}", event);
    }

    private void interruptActiveMixed(MixerState state, int mixerId, String reason, LocalDateTime now) {
        if (state.activeMixed != null && state.activeMixed.finishTime == null) {
            String finishTimeStr = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            state.activeMixed.finishTime = finishTimeStr;
            emitEvent(mixerId, state.activeMixed.type, state.activeMixed.beginTime, finishTimeStr, state.activeMixed.folder);
            if (state.activeMixed.type == OpType.FLUX) {
                state.lastCompletedFluxFinishTime = finishTimeStr;
                state.lastFluxFolder = state.activeMixed.folder;
            }
            state.activeMixed = null;
            log.info("Interrupted {} due to {}", state.activeMixed != null ? state.activeMixed.type : "mixed", reason);
        }
    }

    private void interruptActiveScoop(MixerState state, int mixerId, String reason, LocalDateTime now) {
        if (state.pendingScoop != null && state.pendingScoop.finishTime == null) {
            String finishTimeStr = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            state.pendingScoop.finishTime = finishTimeStr;
            emitEvent(mixerId, OpType.SCOOP, state.pendingScoop.beginTime, finishTimeStr, state.pendingScoop.folder);
            state.pendingScoop = null;
            log.info("Interrupted scoop due to {}", reason);
        }
    }

    public void updateEquipment(int mixerId, boolean gateOpen, boolean tilt) {
        MixerState state = getState(mixerId);
        synchronized (state) {
            boolean oldGate = state.gateOpen;
            boolean oldTilt = state.tilt;
            state.gateOpen = gateOpen;
            state.tilt = tilt;

            LocalDateTime now = LocalDateTime.now();
            if (!gateOpen) {
                interruptActiveMixed(state, mixerId, "gate closed", now);
                interruptActiveScoop(state, mixerId, "gate closed", now);
            }
            if (state.activeMixed != null && state.activeMixed.finishTime == null) {
                if (state.activeMixed.type == OpType.FLUX && tilt) {
                    interruptActiveMixed(state, mixerId, "tilt became true (flux requires false)", now);
                } else if (state.activeMixed.type == OpType.DISLAY && !tilt) {
                    interruptActiveMixed(state, mixerId, "tilt became false (dislay requires true)", now);
                }
            }
            log.info("Equipment updated for mixer {}: gate={}, tilt={}", mixerId, gateOpen, tilt);
        }
    }

    public void handleEvent(EventRequest request, OpType opType, String endpointPath) {
        int mixerId = request.getMixerId();
        String rawStatus = request.getStatus().toLowerCase();
        String rawTime = request.getTimeStamp();
        String folder = request.getFolder();

        if (!"begin".equals(rawStatus) && !"finish".equals(rawStatus)) {
            throw new IllegalArgumentException("Status must be 'begin' or 'finish'");
        }

        LocalDateTime eventTime;
        try {
            eventTime = parseTime(rawTime);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid time_stamp format: " + rawTime);
        }

        MixerState state = getState(mixerId);
        synchronized (state) {
            if ("begin".equals(rawStatus)) {
                if (opType == OpType.FLUX || opType == OpType.DISLAY || opType == OpType.SCOOP) {
                    if (!state.gateOpen) {
                        throw new IllegalStateException("Gate is CLOSED, cannot begin " + opType);
                    }
                }
                if (opType == OpType.FLUX && state.tilt) {
                    throw new IllegalStateException("Tilt is true (must be false) for flux");
                }
                if (opType == OpType.DISLAY && !state.tilt) {
                    throw new IllegalStateException("Tilt is false (must be true) for dislay");
                }
            }

            String normalizedTime = eventTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));

            switch (opType) {
                case INGOTS:
                    handleIngots(state, mixerId, rawStatus, normalizedTime, folder, eventTime);
                    break;
                case FLUX:
                case DISLAY:
                    handleMixed(state, mixerId, opType, rawStatus, normalizedTime, folder, eventTime);
                    break;
                case SCOOP:
                    handleScoop(state, mixerId, rawStatus, normalizedTime, folder, eventTime);
                    break;
                case PROBA:
                    handleProba(state, mixerId, rawStatus, normalizedTime, folder, eventTime);
                    break;
            }
        }
    }

    private void handleIngots(MixerState state, int mixerId, String status, String timeStr, String folder, LocalDateTime time) {
        if ("begin".equals(status)) {
            if (state.activeIngots == null || state.activeIngots.finishTime != null) {
                state.activeIngots = new ActiveOperation(timeStr, folder);
                log.info("Begin ingots for mixer {}", mixerId);
            } else {
                LocalDateTime existingBegin = parseTime(state.activeIngots.beginTime);
                if (time.isBefore(existingBegin)) {
                    state.activeIngots.beginTime = timeStr;
                    state.activeIngots.folder = folder;
                    log.info("Updated ingots begin to earlier time {}", timeStr);
                }
            }
            if (state.activeMixed != null) {
                log.info("Ingots started, discarding active mixed operation {}", state.activeMixed.type);
                state.activeMixed = null;
            }
        } else {
            if (state.activeIngots == null || state.activeIngots.finishTime != null) {
                throw new IllegalStateException("Finish ingots without active begin");
            }
            LocalDateTime beginTime = parseTime(state.activeIngots.beginTime);
            if (time.isBefore(beginTime)) {
                throw new IllegalStateException("Finish time before begin time");
            }
            state.activeIngots.finishTime = timeStr;
            emitEvent(mixerId, OpType.INGOTS, state.activeIngots.beginTime, timeStr, state.activeIngots.folder);
            state.activeIngots = null;
            log.info("Finished ingots for mixer {}", mixerId);
        }
    }

    private void handleMixed(MixerState state, int mixerId, OpType opType, String status, String timeStr, String folder, LocalDateTime time) {
        if (state.activeIngots != null && state.activeIngots.finishTime == null) {
            log.info("Ingots active, ignoring {} event", opType);
            return;
        }

        if ("begin".equals(status)) {
            if (state.activeMixed != null && state.activeMixed.finishTime == null) {
                if (state.activeMixed.type == OpType.FLUX && opType == OpType.DISLAY) {
                    LocalDateTime existingBegin = parseTime(state.activeMixed.beginTime);
                    if (time.isBefore(existingBegin)) {
                        state.activeMixed.beginTime = timeStr;
                        log.info("Converted slag begin to flux, updated begin time to {}", timeStr);
                    } else {
                        log.info("Converted slag begin to flux, begin time unchanged");
                    }
                    return;
                }
                if (state.activeMixed.type == opType) {
                    LocalDateTime existingBegin = parseTime(state.activeMixed.beginTime);
                    if (time.isBefore(existingBegin)) {
                        state.activeMixed.beginTime = timeStr;
                        state.activeMixed.folder = folder;
                        log.info("Updated {} begin to earlier time {}", opType, timeStr);
                    }
                    return;
                }
                log.warn("Cannot begin {} while active {} is running", opType, state.activeMixed.type);
                return;
            }

            if (opType == OpType.DISLAY) {
                if (state.lastCompletedFluxFinishTime != null && state.lastFluxFolder != null) {
                    String sepTime = state.lastCompletedFluxFinishTime;
                    OutputEvent sepEvent = new OutputEvent(mixerId, "separation", sepTime, sepTime, state.lastFluxFolder);
                    outputSender.send(sepEvent);
                    log.info("Generated separation event: {}", sepEvent);
                    state.lastCompletedFluxFinishTime = null;
                    state.lastFluxFolder = null;
                }
                state.activeMixed = new MixedOperation(OpType.DISLAY, timeStr, folder);
                log.info("Begin dislay for mixer {}", mixerId);
            } else {
                state.activeMixed = new MixedOperation(OpType.FLUX, timeStr, folder);
                log.info("Begin flux for mixer {}", mixerId);
            }
        } else {
            if (state.activeMixed == null || state.activeMixed.finishTime != null) {
                throw new IllegalStateException("Finish " + opType + " without active mixed operation");
            }
            if (opType == OpType.DISLAY && state.activeMixed.type == OpType.FLUX) {
                log.info("Finishing dislay converts to flux finish");
            } else if (opType != state.activeMixed.type) {
                throw new IllegalStateException("Finish type mismatch: active " + state.activeMixed.type + ", received " + opType);
            }
            LocalDateTime beginTime = parseTime(state.activeMixed.beginTime);
            if (time.isBefore(beginTime)) {
                throw new IllegalStateException("Finish time before begin time");
            }
            String newFinish = timeStr;
            if (state.activeMixed.finishTime != null) {
                LocalDateTime existingFinish = parseTime(state.activeMixed.finishTime);
                if (time.isAfter(existingFinish)) {
                    state.activeMixed.finishTime = newFinish;
                }
            } else {
                state.activeMixed.finishTime = newFinish;
            }
            if (state.activeMixed.finishTime != null) {
                emitEvent(mixerId, state.activeMixed.type, state.activeMixed.beginTime, state.activeMixed.finishTime, state.activeMixed.folder);
                if (state.activeMixed.type == OpType.FLUX) {
                    state.lastCompletedFluxFinishTime = state.activeMixed.finishTime;
                    state.lastFluxFolder = state.activeMixed.folder;
                }
                state.activeMixed = null;
                log.info("Finished {} for mixer {}", opType, mixerId);
            }
        }
    }

    private void handleScoop(MixerState state, int mixerId, String status, String timeStr, String folder, LocalDateTime time) {
        if ("begin".equals(status)) {
            if (state.pendingScoop == null || state.pendingScoop.finishTime != null) {
                state.pendingScoop = new ActiveOperation(timeStr, folder);
                log.info("Begin scoop for mixer {}", mixerId);
            } else {
                LocalDateTime existingBegin = parseTime(state.pendingScoop.beginTime);
                if (time.isBefore(existingBegin)) {
                    state.pendingScoop.beginTime = timeStr;
                    state.pendingScoop.folder = folder;
                    log.info("Updated scoop begin to earlier time {}", timeStr);
                }
            }
        } else {
            if (state.pendingScoop == null || state.pendingScoop.finishTime != null) {
                throw new IllegalStateException("Finish scoop without active begin");
            }
            LocalDateTime beginTime = parseTime(state.pendingScoop.beginTime);
            if (time.isBefore(beginTime)) {
                throw new IllegalStateException("Finish time before begin time");
            }
            state.pendingScoop.finishTime = timeStr;
            emitEvent(mixerId, OpType.SCOOP, state.pendingScoop.beginTime, timeStr, state.pendingScoop.folder);
            state.pendingScoop = null;
            log.info("Finished scoop for mixer {}", mixerId);
        }
    }

    private void handleProba(MixerState state, int mixerId, String status, String timeStr, String folder, LocalDateTime time) {
        if ("begin".equals(status)) {
            if (state.pendingProba == null || state.pendingProba.finishTime != null) {
                state.pendingProba = new ActiveOperation(timeStr, folder);
                log.info("Begin proba for mixer {}", mixerId);
            } else {
                LocalDateTime existingBegin = parseTime(state.pendingProba.beginTime);
                if (time.isBefore(existingBegin)) {
                    state.pendingProba.beginTime = timeStr;
                    state.pendingProba.folder = folder;
                    log.info("Updated proba begin to earlier time {}", timeStr);
                }
            }
        } else {
            if (state.pendingProba == null || state.pendingProba.finishTime != null) {
                throw new IllegalStateException("Finish proba without active begin");
            }
            LocalDateTime beginTime = parseTime(state.pendingProba.beginTime);
            if (time.isBefore(beginTime)) {
                throw new IllegalStateException("Finish time before begin time");
            }
            state.pendingProba.finishTime = timeStr;
            emitEvent(mixerId, OpType.PROBA, state.pendingProba.beginTime, timeStr, state.pendingProba.folder);
            state.pendingProba = null;
            log.info("Finished proba for mixer {}", mixerId);
        }
    }
}