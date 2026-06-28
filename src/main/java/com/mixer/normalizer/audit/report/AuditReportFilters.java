package com.mixer.normalizer.audit.report;

import java.time.Instant;

public record AuditReportFilters(
        Instant from,
        Instant to,
        Integer mixerId,
        String status,
        String component,
        String operation,
        String endpoint,
        String direction,
        int limit
) {
    public AuditReportFilters withStatus(String newStatus) {
        return new AuditReportFilters(
                from,
                to,
                mixerId,
                newStatus,
                component,
                operation,
                endpoint,
                direction,
                limit);
    }
}
