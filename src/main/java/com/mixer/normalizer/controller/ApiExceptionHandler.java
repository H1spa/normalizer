package com.mixer.normalizer.controller;

import com.mixer.normalizer.audit.AuditCodes;
import com.mixer.normalizer.audit.service.AuditLogService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

/*
 * Общая обработка ошибок для всех контроллеров.
 * Вместо пустого ответа клиент получает JSON с кодом, типом и сообщением.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private final AuditLogService auditLogService;

    public ApiExceptionHandler(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    // Ошибки формата запроса и времени считаются некорректным запросом: HTTP 400.
    @ExceptionHandler({
            IllegalArgumentException.class,
            DateTimeParseException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<Map<String, Object>> handleBadRequest(Exception e) {
        auditLogService.recordError(AuditCodes.COMPONENT_WEB, e, null);
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    // Ошибки состояния процесса считаются конфликтом: например finish без begin.
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(IllegalStateException e) {
        auditLogService.recordError(AuditCodes.COMPONENT_WEB, e, null);
        return error(HttpStatus.CONFLICT, e.getMessage());
    }

    // Сюда попадают ошибки валидации @NotNull, @NotBlank, @Positive и похожих правил.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        auditLogService.recordError(AuditCodes.COMPONENT_WEB, e, null);
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            fields.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", HttpStatus.BAD_REQUEST.getReasonPhrase());
        body.put("message", "Validation failed");
        body.put("fields", fields);

        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<Map<String, Object>> handleExternalFailure(RestClientException e) {
        auditLogService.recordError(AuditCodes.COMPONENT_EXTERNAL, e, null);
        return error(HttpStatus.BAD_GATEWAY, "External service unavailable");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception e) {
        auditLogService.recordError(AuditCodes.COMPONENT_WEB, e, null);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal processing error");
    }

    // Собирает единый формат ответа об ошибке.
    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message == null ? status.getReasonPhrase() : message);
        return ResponseEntity.status(status).body(body);
    }
}
