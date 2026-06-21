package com.mixer.normalizer.audit.entity;

import java.util.UUID;

public record AuditLogRecord(
        UUID correlationId,
        String componentCode,
        String actionCode,
        String levelCode,
        String statusCode,
        String message,
        Long durationMillis
) {
}
