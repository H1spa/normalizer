package com.mixer.normalizer.audit.context;

import java.time.Instant;
import java.util.UUID;

public record AuditContext(
        UUID correlationId,
        String endpointAlias,
        String ownerComponentCode,
        Instant startedAt
) {
}
