package com.mixer.normalizer.audit.report;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class AuditReportController {
    private static final int DEFAULT_LIMIT = 500;

    private final AuditReportRepository repository;

    public AuditReportController(AuditReportRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/api/reports/types")
    public List<AuditReportTypeInfo> types() {
        return Arrays.stream(AuditReportType.values())
                .map(AuditReportTypeInfo::from)
                .toList();
    }

    @GetMapping("/api/reports/options")
    public ResponseEntity<?> options() {
        try {
            return ResponseEntity.ok(repository.filterOptions());
        } catch (DataAccessException e) {
            return databaseUnavailable();
        }
    }

    @GetMapping("/api/reports/{typeId}")
    public ResponseEntity<?> report(@PathVariable String typeId,
                                    @RequestParam(required = false) String from,
                                    @RequestParam(required = false) String to,
                                    @RequestParam(required = false) Integer mixerId,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(required = false) String component,
                                    @RequestParam(required = false) String operation,
                                    @RequestParam(required = false) String endpoint,
                                    @RequestParam(required = false) String direction,
                                    @RequestParam(required = false) Integer limit) {
        AuditReportType type = AuditReportType.fromId(typeId).orElse(null);
        if (type == null) {
            return ResponseEntity.badRequest().body(error("Unknown report type: " + typeId));
        }

        AuditReportFilters filters;
        try {
            filters = new AuditReportFilters(
                    parseInstant(from),
                    parseInstant(to),
                    mixerId,
                    blankToNull(status),
                    blankToNull(component),
                    blankToNull(operation),
                    blankToNull(endpoint),
                    blankToNull(direction),
                    limit == null ? DEFAULT_LIMIT : limit);
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().body(error("Invalid date filter"));
        }

        try {
            return ResponseEntity.ok(repository.run(type, filters));
        } catch (DataAccessException e) {
            return databaseUnavailable();
        }
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException e) {
            return LocalDateTime.parse(text).atZone(ZoneId.systemDefault()).toInstant();
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() || "all".equalsIgnoreCase(value) ? null : value.trim();
    }

    private ResponseEntity<Map<String, Object>> databaseUnavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(error("Audit database is unavailable or migrations are not applied yet"));
    }

    private Map<String, Object> error(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);
        return body;
    }
}
