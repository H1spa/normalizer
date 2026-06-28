package com.mixer.normalizer.audit.report;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AuditReportResult(
        String id,
        String title,
        String description,
        Instant generatedAt,
        List<String> columns,
        List<Map<String, Object>> rows
) {
}
