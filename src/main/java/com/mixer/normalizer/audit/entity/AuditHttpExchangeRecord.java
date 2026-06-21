package com.mixer.normalizer.audit.entity;

import java.time.Instant;

public record AuditHttpExchangeRecord(
        AuditLogRecord logEntry,
        String endpointAlias,
        String direction,
        String httpMethod,
        Integer httpStatus,
        String requestHash,
        String responseHash,
        String externalOperationHash,
        String requestMasked,
        String responseMasked,
        Instant startedAt,
        Instant finishedAt
) {
}
