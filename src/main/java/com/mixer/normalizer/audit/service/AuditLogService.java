package com.mixer.normalizer.audit.service;

import com.mixer.normalizer.audit.AuditCodes;
import com.mixer.normalizer.audit.context.AuditContext;
import com.mixer.normalizer.audit.context.AuditContextHolder;
import com.mixer.normalizer.audit.entity.AuditErrorRecord;
import com.mixer.normalizer.audit.entity.AuditHttpExchangeRecord;
import com.mixer.normalizer.audit.entity.AuditLogRecord;
import com.mixer.normalizer.audit.repository.AuditRepository;
import com.mixer.normalizer.config.AuditProperties;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class AuditLogService {
    private static final String GENERIC_MESSAGE = "Audit action recorded";

    private final AuditProperties properties;
    private final AuditDispatcher dispatcher;
    private final AuditRepository repository;
    private final AuditPayloadSanitizer sanitizer;

    public AuditLogService(AuditProperties properties,
                           AuditDispatcher dispatcher,
                           AuditRepository repository,
                           AuditPayloadSanitizer sanitizer) {
        this.properties = properties;
        this.dispatcher = dispatcher;
        this.repository = repository;
        this.sanitizer = sanitizer;
    }

    public AuditContext beginRequest(String endpointAlias) {
        return beginScope(
                AuditCodes.EVENT_UNKNOWN,
                endpointAlias,
                AuditCodes.COMPONENT_WEB,
                AuditCodes.ACTION_RECEIVED);
    }

    public AuditScope beginSystemScope(String eventAlias, String operationAlias) {
        AuditContext existing = AuditContextHolder.get();
        if (existing != null) {
            return new AuditScope(this, false);
        }
        beginScope(
                eventAlias,
                null,
                AuditCodes.COMPONENT_POLLER,
                AuditCodes.ACTION_POLL_STARTED);
        enrichCurrent(null, eventAlias, operationAlias);
        return new AuditScope(this, true);
    }

    public void enrichCurrent(Integer mixerId, String eventAlias, String operationAlias) {
        AuditContext context = AuditContextHolder.get();
        if (context == null) {
            return;
        }
        UUID correlationId = context.correlationId();
        dispatcher.dispatch(() -> repository.enrichProcess(correlationId, mixerId, eventAlias, operationAlias));
    }

    public void log(String componentCode, String actionCode, String levelCode, String statusCode) {
        log(componentCode, actionCode, levelCode, statusCode, null);
    }

    public void log(String componentCode,
                    String actionCode,
                    String levelCode,
                    String statusCode,
                    Long durationMillis) {
        AuditContext context = AuditContextHolder.get();
        if (context == null) {
            return;
        }
        AuditLogRecord record = new AuditLogRecord(
                context.correlationId(),
                componentCode,
                actionCode,
                levelCode,
                statusCode,
                GENERIC_MESSAGE,
                durationMillis);
        dispatcher.dispatch(() -> repository.addLog(record));
    }

    public void recordHttp(String componentCode,
                           String endpointAlias,
                           String direction,
                           String method,
                           Integer status,
                           Object requestBody,
                           Object responseBody,
                           Object externalOperationId,
                           Instant startedAt,
                           Instant finishedAt) {
        AuditContext context = AuditContextHolder.get();
        if (context == null) {
            return;
        }

        long duration = durationMillis(startedAt, finishedAt);
        String requestSanitized = sanitizer.serializeAndSanitize(requestBody);
        String responseSanitized = sanitizer.serializeAndSanitize(responseBody);
        AuditLogRecord logEntry = new AuditLogRecord(
                context.correlationId(),
                componentCode,
                AuditCodes.ACTION_HTTP_RESPONSE,
                status != null && status >= 400 ? AuditCodes.ERROR : AuditCodes.INFO,
                status != null && status >= 400 ? AuditCodes.FAILED : AuditCodes.SUCCESS,
                GENERIC_MESSAGE,
                duration);
        AuditHttpExchangeRecord record = new AuditHttpExchangeRecord(
                logEntry,
                endpointAlias,
                direction,
                method,
                status,
                sanitizer.hash(requestSanitized),
                sanitizer.hash(responseSanitized),
                sanitizer.hash(externalOperationId),
                properties.isSaveRequestBody() ? requestSanitized : null,
                properties.isSaveResponseBody() ? responseSanitized : null,
                startedAt,
                finishedAt);
        dispatcher.dispatch(() -> repository.addHttpExchange(record));
    }

    public void recordError(String componentCode, Throwable error, Long durationMillis) {
        AuditContext context = AuditContextHolder.get();
        if (context == null) {
            return;
        }
        AuditLogRecord logEntry = new AuditLogRecord(
                context.correlationId(),
                componentCode,
                AuditCodes.ACTION_FAILED,
                AuditCodes.ERROR,
                AuditCodes.FAILED,
                GENERIC_MESSAGE,
                durationMillis);
        AuditErrorRecord record = new AuditErrorRecord(
                logEntry,
                error == null ? null : error.getClass().getSimpleName(),
                error == null ? null : sanitizer.sanitize(error.getMessage()),
                sanitizer.stackTrace(error));
        dispatcher.dispatch(() -> repository.addError(record));
    }

    public void completeCurrent(boolean success) {
        AuditContext context = AuditContextHolder.get();
        if (context == null) {
            return;
        }
        UUID correlationId = context.correlationId();
        log(
                context.ownerComponentCode(),
                success ? AuditCodes.ACTION_COMPLETED : AuditCodes.ACTION_FAILED,
                success ? AuditCodes.INFO : AuditCodes.ERROR,
                success ? AuditCodes.SUCCESS : AuditCodes.FAILED,
                durationMillis(context.startedAt(), Instant.now()));
        dispatcher.dispatch(() -> repository.completeProcess(
                correlationId,
                success ? AuditCodes.SUCCESS : AuditCodes.FAILED));
    }

    public UUID currentCorrelationId() {
        AuditContext context = AuditContextHolder.get();
        return context == null ? null : context.correlationId();
    }

    public void clearContext() {
        AuditContextHolder.clear();
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    private AuditContext beginScope(String eventAlias,
                                    String endpointAlias,
                                    String componentCode,
                                    String actionCode) {
        AuditContext context = new AuditContext(
                UUID.randomUUID(),
                endpointAlias,
                componentCode,
                Instant.now());
        AuditContextHolder.set(context);
        dispatcher.dispatch(() -> repository.startProcess(context.correlationId(), eventAlias));
        log(componentCode, actionCode, AuditCodes.INFO, AuditCodes.STARTED);
        return context;
    }

    private long durationMillis(Instant startedAt, Instant finishedAt) {
        if (startedAt == null || finishedAt == null) {
            return 0L;
        }
        return Math.max(0L, Duration.between(startedAt, finishedAt).toMillis());
    }

    public static final class AuditScope implements AutoCloseable {
        private final AuditLogService service;
        private final boolean owner;
        private boolean successful;

        private AuditScope(AuditLogService service, boolean owner) {
            this.service = service;
            this.owner = owner;
        }

        public void success() {
            successful = true;
        }

        @Override
        public void close() {
            if (!owner) {
                return;
            }
            service.completeCurrent(successful);
            service.clearContext();
        }
    }
}
