package com.mixer.normalizer.audit.entity;

public record AuditErrorRecord(
        AuditLogRecord logEntry,
        String errorClass,
        String errorMessage,
        String stackTrace
) {
}
