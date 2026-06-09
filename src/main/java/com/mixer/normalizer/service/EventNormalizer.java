package com.mixer.normalizer.service;

import com.mixer.normalizer.config.NormalizerProperties;
import com.mixer.normalizer.dto.EventRequest;
import com.mixer.normalizer.service.EquipmentStateHolder.EquipmentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EventNormalizer {

    private static final Logger log = LoggerFactory.getLogger(EventNormalizer.class);
    private static final String TILT_REASON = "mixer tilted";
    private static final String GATE_CLOSED_REASON = "gate closed";

    /*
     * EventNormalizer - центральная машина состояний.
     * Если смотреть глазами разработчика из другого языка, это объект, который держит
     * несколько потокобезопасных словарей в памяти и на каждый begin/finish решает:
     * можно ли принять событие, какой внешний service вызвать и что сохранить до finish.
     */
    private final OuterAnswerClient outerAnswerClient;
    private final EquipmentStateHolder equipmentStateHolder;
    private final NormalizerProperties properties;
    private final ZoneId fixedZone;
    private final DateTimeFormatter storageFormatter;
    private final DateTimeFormatter outerFormatter;
    private final DateTimeFormatter inputLocalFormatter;
    private final List<DateTimeFormatter> inputOffsetFormatters;
    private final Set<OpType> gateRequiredTypes;

    /*
     * ConcurrentHashMap разрешает параллельную работу разных mixer_id.
     * Внутри одного MixerState дополнительно используется synchronized(state),
     * чтобы два события одного миксера не перемешали active operation и external id.
     */
    private final Map<Integer, MixerState> states = new ConcurrentHashMap<>();

    @Value("${equipment.check-enabled:true}")
    private boolean equipmentCheckEnabled;

    public EventNormalizer(OuterAnswerClient outerAnswerClient,
                           EquipmentStateHolder equipmentStateHolder,
                           NormalizerProperties properties) {
        this.outerAnswerClient = outerAnswerClient;
        this.equipmentStateHolder = equipmentStateHolder;
        this.properties = properties;
        this.fixedZone = properties.fixedZoneId();
        this.storageFormatter = properties.storageFormatter();
        this.outerFormatter = properties.outerFormatter();
        this.inputLocalFormatter = DateTimeFormatter.ofPattern(properties.getInputTimePattern());
        this.inputOffsetFormatters = List.of(
                DateTimeFormatter.ofPattern(properties.getInputTimePattern() + properties.getInputOffsetPattern()),
                DateTimeFormatter.ofPattern(properties.getInputTimePattern() + "XX"),
                DateTimeFormatter.ofPattern(properties.getInputTimePattern() + "XXX")
        );
        this.gateRequiredTypes = properties.gateRequiredTypeSet();
    }

    public enum OpType {
        INGOTS, FLUX, DISLAG, SCOOP, PROBA
    }

    /*
     * ActiveOperation - это "незакрытая" операция.
     * Begin уже пришел, внешний сервис уже создал запись и вернул id,
     * но finish еще не пришел. Поэтому мы держим в памяти beginTime, folder и externalId,
     * чтобы позже закрыть именно ту запись, которую открыли на begin.
     */
    private static class ActiveOperation {
        String beginTime;
        String folder;
        String externalId;

        ActiveOperation(String beginTime, String folder) {
            this.beginTime = beginTime;
            this.folder = folder;
        }
    }

    /*
     * MixedOperation отличается только тем, что один слот activeMixed используется
     * для flux и dislag. Это нужно для строгой цепочки flux -> separation -> dislag.
     */
    private static class MixedOperation extends ActiveOperation {
        OpType type;

        MixedOperation(OpType type, String beginTime, String folder) {
            super(beginTime, folder);
            this.type = type;
        }
    }

    /*
     * MixerState - вся оперативная память по одному миксеру.
     * Это состояние живет только внутри процесса: если приложение перезапустить,
     * активные операции начнутся с чистого состояния. Такой подход быстрый и простой,
     * но он означает, что незавершенные begin/finish не переживают рестарт сервиса.
     */
    private static class MixerState {
        MixedOperation activeMixed;
        ActiveOperation activeIngots;
        ActiveOperation activeScoop;
        ActiveOperation activeProba;
        String lastCompletedFluxFinishTime;
        String lastFluxFolder;
    }

    // ---------- Время ----------

    private ZonedDateTime parseZoned(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            throw new IllegalArgumentException("time_stamp is required");
        }

        /*
         * Время приводим к одной зоне сразу, до любых проверок и сравнений.
         * Так begin/finish можно сравнивать между собой без путаницы часовых поясов.
         * Если во входе есть Z/UTC/+0300/+03:00, переводим реальный момент времени в +0700.
         * Если смещения нет, считаем строку временем входной зоны normalizer.default-input-zone.
         */
        String cleaned = normalizeUtcSuffix(timestamp.trim());
        for (DateTimeFormatter formatter : inputOffsetFormatters) {
            try {
                return OffsetDateTime.parse(cleaned, formatter).atZoneSameInstant(fixedZone);
            } catch (DateTimeParseException ignored) {
                // Пробуем следующий разрешенный формат смещения.
            }
        }

        try {
            LocalDateTime localDateTime = LocalDateTime.parse(cleaned, inputLocalFormatter);
            return localDateTime.atZone(properties.defaultInputZoneId()).withZoneSameInstant(fixedZone);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid time_stamp format: " + timestamp, e);
        }
    }

    private String normalizeUtcSuffix(String value) {
        String upper = value.toUpperCase(Locale.ROOT);
        if (upper.endsWith("UTC")) {
            return value.substring(0, value.length() - 3).trim() + "Z";
        }
        return value;
    }

    private String formatStorage(ZonedDateTime zdt) {
        /*
         * Внутри храним уже приведенное к +0700 время без offset в строке.
         * Зона не теряется: при обратном чтении toZonedDateTime всегда приклеивает fixedZone.
         */
        return zdt.format(storageFormatter);
    }

    private String formatOuter(ZonedDateTime zdt) {
        // Во внешние сервисы всегда уходит единый формат: yyyy-MM-dd_HH-mm-ss+0700.
        return zdt.format(outerFormatter);
    }

    private ZonedDateTime toZonedDateTime(String storageTime) {
        LocalDateTime ldt = LocalDateTime.parse(storageTime, storageFormatter);
        return ldt.atZone(fixedZone);
    }

    // ---------- Общие помощники ----------

    private MixerState getState(int mixerId) {
        return states.computeIfAbsent(mixerId, id -> new MixerState());
    }

    private boolean hasActiveMixed(MixerState state) {
        return state.activeMixed != null;
    }

    private boolean hasActiveIngots(MixerState state) {
        return state.activeIngots != null;
    }

    private String createExternal(OpType type, int mixerId, ZonedDateTime beginZdt, String folder) {
        String service = properties.eventTypeName(type);
        return outerAnswerClient.createEvent(service, mixerId, formatOuter(beginZdt), folder);
    }

    private void finishExternal(OpType type, String externalId, ZonedDateTime finishZdt) {
        if (externalId == null || externalId.isBlank()) {
            throw new IllegalStateException("External id is missing for " + properties.eventTypeName(type));
        }
        String service = properties.eventTypeName(type);
        outerAnswerClient.finishEvent(service, externalId, formatOuter(finishZdt));
    }

    private void ensureFinishNotBeforeBegin(String beginTime, ZonedDateTime finishZdt) {
        ZonedDateTime beginZdt = toZonedDateTime(beginTime);
        if (finishZdt.isBefore(beginZdt)) {
            throw new IllegalArgumentException("finish time is before begin time");
        }
    }

    private void mergeEarlierBegin(ActiveOperation operation, String timeStr, String folder, ZonedDateTime beginZdt) {
        /*
         * Повторный begin той же активной операции не делает второй POST во внешний сервис.
         * Если время раньше текущего begin, в памяти оставляем минимальное время,
         * Это защищает от повторных или чуть переупорядоченных сообщений:
         * внешний POST уже был, а внутри операции сохраняется самое раннее начало.
         */
        ZonedDateTime existingBegin = toZonedDateTime(operation.beginTime);
        if (beginZdt.isBefore(existingBegin)) {
            operation.beginTime = timeStr;
            operation.folder = folder;
            log.info("Updated active begin to earlier {}", timeStr);
        } else {
            log.info("Duplicate/extra begin ignored, external POST is not repeated");
        }
    }

    private void validateEquipmentForBegin(int mixerId, OpType opType) {
        if (!equipmentCheckEnabled) {
            log.debug("Equipment checks disabled for mixer {}", mixerId);
            return;
        }

        EquipmentState eq = equipmentStateHolder.getState(mixerId);
        if (eq.tilt()) {
            log.warn("Rejected begin {} for mixer {}: mixer tilted", opType, mixerId);
            throw new IllegalStateException("Cannot begin when tilted");
        }
        if (gateRequiredTypes.contains(opType) && !eq.gateOpen()) {
            log.warn("Rejected begin {} for mixer {}: gate closed", opType, mixerId);
            throw new IllegalStateException("Gate CLOSED");
        }
    }

    // ---------- Оборудование ----------

    public void updateEquipment(int mixerId, boolean gateOpen, boolean tilt) {
        /*
         * Состояние оборудования всегда сохраняется в локальный кэш:
         * следующие begin будут проверяться уже по этому значению.
         */
        equipmentStateHolder.update(mixerId, gateOpen, tilt);
        if (!equipmentCheckEnabled) {
            return;
        }

        MixerState state = states.get(mixerId);
        if (state == null) {
            return;
        }

        synchronized (state) {
            ZonedDateTime now = ZonedDateTime.now(fixedZone);
            String finishTime = formatStorage(now);
            if (tilt) {
                /*
                 * Наклон делает дальнейшее выполнение операции небезопасным.
                 * Поэтому закрываем текущим временем все активные операции миксера,
                 * даже те, которым не нужна открытая шторка.
                 */
                interruptMixed(state, mixerId, finishTime, TILT_REASON);
                interruptSimple(state, mixerId, OpType.INGOTS, state.activeIngots, finishTime, TILT_REASON);
                state.activeIngots = null;
                interruptSimple(state, mixerId, OpType.SCOOP, state.activeScoop, finishTime, TILT_REASON);
                state.activeScoop = null;
                interruptSimple(state, mixerId, OpType.PROBA, state.activeProba, finishTime, TILT_REASON);
                state.activeProba = null;
            } else if (!gateOpen) {
                /*
                 * Закрытая шторка опасна только для операций, которые физически
                 * завязаны на открытую шторку: flux, dislag и scoop.
                 * Ingots/proba продолжают жить, потому что их запуск не зависит от шторки.
                 */
                if (state.activeMixed != null && gateRequiredTypes.contains(state.activeMixed.type)) {
                    interruptMixed(state, mixerId, finishTime, GATE_CLOSED_REASON);
                }
                interruptSimple(state, mixerId, OpType.SCOOP, state.activeScoop, finishTime, GATE_CLOSED_REASON);
                state.activeScoop = null;
            }
        }
    }

    public void updateEquipmentFromPolling(int mixerId, boolean gateOpen, boolean tilt) {
        // Polling АСУ ТП и ручной endpoint оборудования используют одну и ту же логику обновления.
        updateEquipment(mixerId, gateOpen, tilt);
    }

    private void interruptMixed(MixerState state, int mixerId, String finishTime, String reason) {
        if (state.activeMixed == null) {
            return;
        }

        MixedOperation operation = state.activeMixed;
        ZonedDateTime finishZdt = toZonedDateTime(finishTime);
        finishExternal(operation.type, operation.externalId, finishZdt);

        if (operation.type == OpType.FLUX) {
            state.lastCompletedFluxFinishTime = finishTime;
            state.lastFluxFolder = operation.folder;
        }

        state.activeMixed = null;
        log.info("Interrupted {} for mixer {} due to {}", operation.type, mixerId, reason);
    }

    private void interruptSimple(MixerState state,
                                 int mixerId,
                                 OpType type,
                                 ActiveOperation operation,
                                 String finishTime,
                                 String reason) {
        if (operation == null) {
            return;
        }

        ZonedDateTime finishZdt = toZonedDateTime(finishTime);
        finishExternal(type, operation.externalId, finishZdt);
        log.info("Interrupted {} for mixer {} due to {}", type, mixerId, reason);
    }

    // ---------- Входящий begin ----------

    public void handleBegin(EventRequest request, OpType opType) {
        int mixerId = request.getMixerId();
        /*
         * Парсим время до входа в synchronized-блок.
         * Это чистое вычисление без изменения состояния, поэтому оно не должно держать lock дольше нужного.
         */
        ZonedDateTime beginZdt = parseZoned(request.getTimeStamp());
        String beginTimeStr = formatStorage(beginZdt);
        String folder = request.getFolder();

        log.info("Incoming begin {} mixer={} date={}", properties.eventTypeName(opType), mixerId, formatOuter(beginZdt));

        MixerState state = getState(mixerId);
        synchronized (state) {
            /*
             * Все проверки и изменения состояния одного mixerId идут под одним lock.
             * Так два параллельных begin не смогут одновременно создать две активные записи.
             */
            validateEquipmentForBegin(mixerId, opType);

            switch (opType) {
                case INGOTS -> handleIngotsBegin(state, mixerId, beginTimeStr, folder, beginZdt);
                case FLUX, DISLAG -> handleMixedBegin(state, mixerId, opType, beginTimeStr, folder, beginZdt);
                case SCOOP -> handleScoopBegin(state, mixerId, beginTimeStr, folder, beginZdt);
                case PROBA -> handleProbaBegin(state, mixerId, beginTimeStr, folder, beginZdt);
            }
        }
    }

    // ---------- Входящий finish ----------

    public void handleFinish(EventRequest request, OpType opType) {
        int mixerId = request.getMixerId();
        /*
         * Finish тоже нормализуется до +0700 до поиска операции.
         * Если входное время пришло с другим offset, сравнение с begin все равно будет корректным.
         */
        ZonedDateTime finishZdt = parseZoned(request.getTimeStamp());

        log.info("Incoming finish {} mixer={} date={}", properties.eventTypeName(opType), mixerId, formatOuter(finishZdt));

        MixerState state = getState(mixerId);
        synchronized (state) {
            switch (opType) {
                case INGOTS -> handleIngotsFinish(state, mixerId, finishZdt);
                case FLUX, DISLAG -> handleMixedFinish(state, mixerId, opType, finishZdt);
                case SCOOP -> handleScoopFinish(state, mixerId, finishZdt);
                case PROBA -> handleProbaFinish(state, mixerId, finishZdt);
            }
        }
    }

    // ---------- Ingots ----------

    private void handleIngotsBegin(MixerState state, int mixerId, String timeStr, String folder, ZonedDateTime beginZdt) {
        if (hasActiveMixed(state)) {
            /*
             * Ingots и mixed-операции используют один производственный участок.
             * Мы не удаляем активный flux/dislag автоматически, потому что это скрыло бы потерю события.
             */
            throw new IllegalStateException("Cannot begin ingots while flux/dislag is active");
        }
        if (hasActiveIngots(state)) {
            // Повторный begin ingots уточняет время, но не создает вторую внешнюю запись.
            mergeEarlierBegin(state.activeIngots, timeStr, folder, beginZdt);
            return;
        }

        ActiveOperation operation = new ActiveOperation(timeStr, folder);
        operation.externalId = createExternal(OpType.INGOTS, mixerId, beginZdt, folder);
        state.activeIngots = operation;
        log.info("Begin ingots mixer={} externalId={}", mixerId, operation.externalId);
    }

    private void handleIngotsFinish(MixerState state, int mixerId, ZonedDateTime finishZdt) {
        if (state.activeIngots == null) {
            // Finish без сохраненного begin невозможно связать с внешним id, поэтому это конфликт.
            throw new IllegalStateException("Finish ingots without active begin");
        }

        ensureFinishNotBeforeBegin(state.activeIngots.beginTime, finishZdt);
        finishExternal(OpType.INGOTS, state.activeIngots.externalId, finishZdt);
        state.activeIngots = null;
        log.info("Finished ingots mixer={}", mixerId);
    }

    // ---------- Flux / Dislag / Separation ----------

    private void handleMixedBegin(MixerState state,
                                  int mixerId,
                                  OpType opType,
                                  String timeStr,
                                  String folder,
                                  ZonedDateTime beginZdt) {
        if (hasActiveIngots(state)) {
            /*
             * Flux/dislag не стартуют поверх ingots.
             * Здесь важен явный отказ, потому что автоматическое завершение ingots могло бы отправить
             * во внешний сервис неправильную дату finish.
             */
            throw new IllegalStateException("Cannot begin flux/dislag while ingots is active");
        }

        if (state.activeMixed != null) {
            if (state.activeMixed.type == OpType.FLUX && opType == OpType.DISLAG) {
                /*
                 * Строгая конвертация slag в текущий flux.
                 * Внешний POST не повторяем: это все еще та же активная запись flux.
                 */
                mergeEarlierBegin(state.activeMixed, timeStr, folder, beginZdt);
                log.info("Converted /shovel_slag begin to active flux for mixer {}", mixerId);
                return;
            }
            if (state.activeMixed.type == opType) {
                // Такой же begin второй раз считаем повтором или уточнением раннего времени.
                mergeEarlierBegin(state.activeMixed, timeStr, folder, beginZdt);
                return;
            }
            throw new IllegalStateException("Cannot begin " + opType + " while " + state.activeMixed.type + " is active");
        }

        if (opType == OpType.DISLAG) {
            /*
             * Если перед dislag был завершенный flux, между ними нужно создать separation.
             * Это делается прямо перед созданием dislag, потому что только здесь известно время начала dislag.
             */
            generateSeparationIfNeeded(state, mixerId, beginZdt);
        }

        MixedOperation operation = new MixedOperation(opType, timeStr, folder);
        operation.externalId = createExternal(opType, mixerId, beginZdt, folder);
        state.activeMixed = operation;
        log.info("Begin {} mixer={} externalId={}", properties.eventTypeName(opType), mixerId, operation.externalId);
    }

    private void generateSeparationIfNeeded(MixerState state, int mixerId, ZonedDateTime dislagBeginZdt) {
        if (state.lastCompletedFluxFinishTime == null) {
            // Нет завершенного flux - значит separation строить не из чего.
            return;
        }

        ZonedDateTime separationBeginZdt = toZonedDateTime(state.lastCompletedFluxFinishTime);
        if (dislagBeginZdt.isBefore(separationBeginZdt)) {
            throw new IllegalStateException("Cannot generate separation: dislag begin is before flux finish");
        }

        /*
         * Separation - это промежуток между законченным flux и начатым dislag.
         * Поэтому begin separation равен finish flux, а finish separation равен begin dislag.
         * Внешне это выглядит как обычная операция: сначала POST separation,
         * затем PUT separation/{id} с датой завершения.
         */
        String service = properties.getSeparationType();
        String externalId = outerAnswerClient.createEvent(service, mixerId, formatOuter(separationBeginZdt), state.lastFluxFolder);
        outerAnswerClient.finishEvent(service, externalId, formatOuter(dislagBeginZdt));
        log.info("Generated separation mixer={} begin={} finish={} externalId={}",
                mixerId, formatOuter(separationBeginZdt), formatOuter(dislagBeginZdt), externalId);

        state.lastCompletedFluxFinishTime = null;
        state.lastFluxFolder = null;
    }

    private void handleMixedFinish(MixerState state, int mixerId, OpType opType, ZonedDateTime finishZdt) {
        if (state.activeMixed == null) {
            // Нет активной mixed-операции - значит у finish нет внешнего id для закрытия.
            throw new IllegalStateException("Finish " + opType + " without active begin");
        }

        /*
         * Если во время active flux пришел finish /shovel_slag, по строгой логике
         * это завершение текущего flux, а не произвольное создание dislag.
         */
        if (opType == OpType.DISLAG && state.activeMixed.type == OpType.FLUX) {
            log.info("Converted /shovel_slag finish to active flux finish for mixer {}", mixerId);
        } else if (opType != state.activeMixed.type) {
            throw new IllegalStateException("Finish type mismatch: active " + state.activeMixed.type + ", received " + opType);
        }

        ensureFinishNotBeforeBegin(state.activeMixed.beginTime, finishZdt);
        finishExternal(state.activeMixed.type, state.activeMixed.externalId, finishZdt);

        if (state.activeMixed.type == OpType.FLUX) {
            state.lastCompletedFluxFinishTime = formatStorage(finishZdt);
            state.lastFluxFolder = state.activeMixed.folder;
        }

        OpType finishedType = state.activeMixed.type;
        state.activeMixed = null;
        log.info("Finished {} mixer={}", properties.eventTypeName(finishedType), mixerId);
    }

    // ---------- Scoop ----------

    private void handleScoopBegin(MixerState state, int mixerId, String timeStr, String folder, ZonedDateTime beginZdt) {
        if (state.activeScoop != null) {
            // Scoop хранится отдельно, поэтому может сосуществовать с mixed/ingots, но не сам с собой.
            mergeEarlierBegin(state.activeScoop, timeStr, folder, beginZdt);
            return;
        }

        ActiveOperation operation = new ActiveOperation(timeStr, folder);
        operation.externalId = createExternal(OpType.SCOOP, mixerId, beginZdt, folder);
        state.activeScoop = operation;
        log.info("Begin scoop mixer={} externalId={}", mixerId, operation.externalId);
    }

    private void handleScoopFinish(MixerState state, int mixerId, ZonedDateTime finishZdt) {
        if (state.activeScoop == null) {
            // Без activeScoop нечего закрывать и некуда подставить внешний id.
            throw new IllegalStateException("Finish scoop without active begin");
        }

        ensureFinishNotBeforeBegin(state.activeScoop.beginTime, finishZdt);
        finishExternal(OpType.SCOOP, state.activeScoop.externalId, finishZdt);
        state.activeScoop = null;
        log.info("Finished scoop mixer={}", mixerId);
    }

    // ---------- Proba ----------

    private void handleProbaBegin(MixerState state, int mixerId, String timeStr, String folder, ZonedDateTime beginZdt) {
        if (state.activeProba != null) {
            // Повторный begin proba не создает дубль во внешней системе.
            mergeEarlierBegin(state.activeProba, timeStr, folder, beginZdt);
            return;
        }

        ActiveOperation operation = new ActiveOperation(timeStr, folder);
        operation.externalId = createExternal(OpType.PROBA, mixerId, beginZdt, folder);
        state.activeProba = operation;
        log.info("Begin proba mixer={} externalId={}", mixerId, operation.externalId);
    }

    private void handleProbaFinish(MixerState state, int mixerId, ZonedDateTime finishZdt) {
        if (state.activeProba == null) {
            // Finish proba без begin не может быть идемпотентно закрыт, потому что id неизвестен.
            throw new IllegalStateException("Finish proba without active begin");
        }

        ensureFinishNotBeforeBegin(state.activeProba.beginTime, finishZdt);
        finishExternal(OpType.PROBA, state.activeProba.externalId, finishZdt);
        state.activeProba = null;
        log.info("Finished proba mixer={}", mixerId);
    }
}
