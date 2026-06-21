package com.mixer.normalizer.controller;

import com.mixer.normalizer.audit.AuditAliasResolver;
import com.mixer.normalizer.audit.AuditCodes;
import com.mixer.normalizer.audit.service.AuditLogService;
import com.mixer.normalizer.dto.EquipmentRequest;
import com.mixer.normalizer.dto.EventRequest;
import com.mixer.normalizer.service.EventNormalizer;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/*
 * @RestController говорит Spring, что этот класс принимает HTTP-запросы.
 * @PostMapping/@PutMapping ниже похожи на регистрацию обработчика маршрута в Express,
 * FastAPI или другом веб-фреймворке.
 */
@RestController
public class MixerController {

    private final EventNormalizer normalizer;
    private final AuditLogService auditLogService;
    private final AuditAliasResolver auditAliasResolver;

    public MixerController(EventNormalizer normalizer,
                           AuditLogService auditLogService,
                           AuditAliasResolver auditAliasResolver) {
        this.normalizer = normalizer;
        this.auditLogService = auditLogService;
        this.auditAliasResolver = auditAliasResolver;
    }

    /*
     * Все методы ниже имеют одинаковую форму:
     * 1. Spring превращает JSON body в EventRequest.
     * 2. Валидация проверяет обязательные поля.
     * 3. Контроллер выбирает begin/finish и передает событие в машину состояний.
     * 4. Ответ 202 Accepted означает: запрос принят, сама нормализация уже выполнена внутри сервиса.
     */
    // /scoop отвечает за scoop: операция может идти параллельно с другими, но требует открытую шторку.
    @PostMapping("/scoop")
    public ResponseEntity<Void> handleScoop(@Valid @RequestBody EventRequest request) {
        return handleEvent(request, EventNormalizer.OpType.SCOOP);
    }

    // /table отвечает за ingots: эта операция несовместима с активными flux/dislag.
    @PostMapping("/table")
    public ResponseEntity<Void> handleTable(@Valid @RequestBody EventRequest request) {
        return handleEvent(request, EventNormalizer.OpType.INGOTS);
    }

    // /shovel_mixer отвечает за flux: начало/конец флюса приходит тем же POST, меняется только status.
    @PostMapping("/shovel_mixer")
    public ResponseEntity<Void> handleShovelMixer(@Valid @RequestBody EventRequest request) {
        return handleEvent(request, EventNormalizer.OpType.FLUX);
    }

    // /shovel_slag отвечает за dislag: URL сам выбирает тип операции, отдельное поле type не нужно.
    @PostMapping("/shovel_slag")
    public ResponseEntity<Void> handleShovelSlag(@Valid @RequestBody EventRequest request) {
        return handleEvent(request, EventNormalizer.OpType.DISLAG);
    }

    // /sampling отвечает за proba: шторка для begin не нужна, но наклон все равно запрещает старт.
    @PostMapping("/sampling")
    public ResponseEntity<Void> handleSampling(@Valid @RequestBody EventRequest request) {
        return handleEvent(request, EventNormalizer.OpType.PROBA);
    }

    /*
     * Ручное обновление состояния оборудования.
     * Обычно состояние приходит через polling, но этот endpoint полезен для mock-сервисов,
     * ручной проверки и прямой интеграции с источником состояния.
     */
    @PutMapping("/equipment/{mixer_id}")
    public ResponseEntity<Void> updateEquipment(@PathVariable("mixer_id") int mixerId, @Valid @RequestBody EquipmentRequest eq) {
        auditLogService.enrichCurrent(mixerId, AuditCodes.EVENT_EQUIPMENT, AuditCodes.OPERATION_UNKNOWN);
        auditLogService.log(AuditCodes.COMPONENT_WEB, AuditCodes.ACTION_VALIDATED, AuditCodes.INFO, AuditCodes.SUCCESS);
        auditLogService.log(AuditCodes.COMPONENT_CORE, AuditCodes.ACTION_PROCESSING, AuditCodes.INFO, AuditCodes.STARTED);
        boolean gateOpen = "OPEN".equalsIgnoreCase(eq.getGate());
        boolean tilt = Boolean.TRUE.equals(eq.getTilt());
        normalizer.updateEquipment(mixerId, gateOpen, tilt);
        return ResponseEntity.ok().build();
    }

    private ResponseEntity<Void> handleEvent(EventRequest request, EventNormalizer.OpType operationType) {
        String phaseAlias = auditAliasResolver.phase(request.getStatus());
        auditLogService.enrichCurrent(
                request.getMixerId(),
                phaseAlias,
                auditAliasResolver.operation(operationType));
        auditLogService.log(AuditCodes.COMPONENT_WEB, AuditCodes.ACTION_VALIDATED, AuditCodes.INFO, AuditCodes.SUCCESS);
        auditLogService.log(AuditCodes.COMPONENT_CORE, AuditCodes.ACTION_PROCESSING, AuditCodes.INFO, AuditCodes.STARTED);

        if (isBegin(request)) {
            normalizer.handleBegin(request, operationType);
        } else if (isFinish(request)) {
            normalizer.handleFinish(request, operationType);
        } else {
            throw new IllegalArgumentException("Invalid status");
        }
        return ResponseEntity.accepted().build();
    }

    // Статусы сравниваются без учета регистра, чтобы "BEGIN" и "begin" не расходились по смыслу.
    private boolean isBegin(EventRequest request) {
        return "begin".equalsIgnoreCase(request.getStatus());
    }

    private boolean isFinish(EventRequest request) {
        return "finish".equalsIgnoreCase(request.getStatus());
    }
}
