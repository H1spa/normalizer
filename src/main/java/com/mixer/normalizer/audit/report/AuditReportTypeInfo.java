package com.mixer.normalizer.audit.report;

public record AuditReportTypeInfo(
        String id,
        String title,
        String description
) {
    public static AuditReportTypeInfo from(AuditReportType type) {
        return new AuditReportTypeInfo(type.getId(), type.getTitle(), type.getDescription());
    }
}
