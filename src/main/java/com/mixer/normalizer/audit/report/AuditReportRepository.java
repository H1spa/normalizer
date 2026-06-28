package com.mixer.normalizer.audit.report;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class AuditReportRepository {
    private static final int MAX_LIMIT = 5000;

    private final JdbcTemplate jdbcTemplate;

    public AuditReportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AuditReportResult run(AuditReportType type, AuditReportFilters filters) {
        return switch (type) {
            case OVERVIEW -> overview(type, filters);
            case PROCESSES -> processes(type, filters);
            case LOG_ENTRIES -> logEntries(type, filters);
            case HTTP_EXCHANGES -> httpExchanges(type, filters);
            case ERRORS -> errors(type, filters);
            case ENDPOINTS -> endpoints(type, filters);
            case COMPONENTS -> components(type, filters);
            case OPERATIONS -> operations(type, filters);
            case MIXERS -> mixers(type, filters);
            case EXTERNAL_SERVICES -> externalServices(type, filters);
            case TIMELINE -> timeline(type, filters);
            case SLOW_REQUESTS -> slowRequests(type, filters);
        };
    }

    public Map<String, List<String>> filterOptions() {
        Map<String, List<String>> options = new LinkedHashMap<>();
        options.put("statuses", codes("SELECT code FROM ref_log_status ORDER BY code"));
        options.put("components", codes("SELECT code FROM ref_component ORDER BY code"));
        options.put("operations", codes("SELECT code FROM ref_operation_alias ORDER BY code"));
        options.put("endpoints", codes("SELECT alias_code FROM ref_endpoint_alias ORDER BY alias_code"));
        options.put("directions", List.of("INBOUND", "OUTBOUND"));
        return options;
    }

    private AuditReportResult overview(AuditReportType type, AuditReportFilters filters) {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(metric("Всего процессов", count("""
                SELECT COUNT(*)
                FROM audit_process p
                LEFT JOIN ref_log_status final_status ON final_status.id = p.final_status_id
                LEFT JOIN ref_operation_alias operation ON operation.id = p.operation_alias_id
                """, filters, "p", "final_status", "operation")));
        rows.add(metric("Успешных процессов", count("""
                SELECT COUNT(*)
                FROM audit_process p
                LEFT JOIN ref_log_status final_status ON final_status.id = p.final_status_id
                LEFT JOIN ref_operation_alias operation ON operation.id = p.operation_alias_id
                """, filters.withStatus("SUCCESS"), "p", "final_status", "operation")));
        rows.add(metric("Процессов с ошибкой", count("""
                SELECT COUNT(*)
                FROM audit_process p
                LEFT JOIN ref_log_status final_status ON final_status.id = p.final_status_id
                LEFT JOIN ref_operation_alias operation ON operation.id = p.operation_alias_id
                """, filters.withStatus("FAILED"), "p", "final_status", "operation")));
        rows.add(metric("Открытых процессов", count("""
                SELECT COUNT(*)
                FROM audit_process p
                LEFT JOIN ref_log_status final_status ON final_status.id = p.final_status_id
                LEFT JOIN ref_operation_alias operation ON operation.id = p.operation_alias_id
                """, filters.withStatus("STARTED"), "p", "final_status", "operation")));
        rows.add(metric("HTTP-обменов", count("""
                SELECT COUNT(*)
                FROM audit_http_exchange h
                JOIN audit_log_entry l ON l.id = h.log_entry_id
                JOIN audit_process p ON p.id = l.process_id
                LEFT JOIN ref_log_status final_status ON final_status.id = p.final_status_id
                LEFT JOIN ref_operation_alias operation ON operation.id = p.operation_alias_id
                LEFT JOIN ref_endpoint_alias endpoint ON endpoint.id = h.endpoint_alias_id
                """, filters, "p", "final_status", "operation", "h", "endpoint")));
        rows.add(metric("HTTP-сбоев", count("""
                SELECT COUNT(*)
                FROM audit_http_exchange h
                JOIN audit_log_entry l ON l.id = h.log_entry_id
                JOIN audit_process p ON p.id = l.process_id
                LEFT JOIN ref_log_status final_status ON final_status.id = p.final_status_id
                LEFT JOIN ref_operation_alias operation ON operation.id = p.operation_alias_id
                LEFT JOIN ref_endpoint_alias endpoint ON endpoint.id = h.endpoint_alias_id
                """, filters, "p", "final_status", "operation", "(h.http_status >= 400 OR h.http_status IS NULL)", "h", "endpoint")));
        rows.add(metric("Ошибок", count("""
                SELECT COUNT(*)
                FROM audit_error e
                JOIN audit_log_entry l ON l.id = e.log_entry_id
                JOIN audit_process p ON p.id = l.process_id
                LEFT JOIN ref_log_status final_status ON final_status.id = p.final_status_id
                LEFT JOIN ref_operation_alias operation ON operation.id = p.operation_alias_id
                """, filters, "p", "final_status", "operation")));
        rows.add(metric("Уникальных миксеров", count("""
                SELECT COUNT(DISTINCT p.mixer_id)
                FROM audit_process p
                LEFT JOIN ref_log_status final_status ON final_status.id = p.final_status_id
                LEFT JOIN ref_operation_alias operation ON operation.id = p.operation_alias_id
                """, filters, "p", "final_status", "operation")));
        return result(type, List.of("metric", "value"), rows);
    }

    private AuditReportResult processes(AuditReportType type, AuditReportFilters filters) {
        Query query = baseQuery("""
                SELECT
                    p.started_at,
                    p.completed_at,
                    p.correlation_id,
                    p.mixer_id,
                    event_type.code AS event_type,
                    operation.code AS operation,
                    final_status.code AS final_status,
                    COUNT(DISTINCT l.id) AS log_entries,
                    COUNT(DISTINCT h.id) AS http_exchanges,
                    COUNT(DISTINCT e.id) AS errors
                FROM audit_process p
                LEFT JOIN ref_event_type event_type ON event_type.id = p.event_type_id
                LEFT JOIN ref_operation_alias operation ON operation.id = p.operation_alias_id
                LEFT JOIN ref_log_status final_status ON final_status.id = p.final_status_id
                LEFT JOIN audit_log_entry l ON l.process_id = p.id
                LEFT JOIN audit_http_exchange h ON h.log_entry_id = l.id
                LEFT JOIN audit_error e ON e.log_entry_id = l.id
                """, filters, "p", "final_status", "operation");
        query.sql().append("""
                GROUP BY p.id, event_type.code, operation.code, final_status.code
                ORDER BY p.started_at DESC
                LIMIT ?
                """);
        query.params().add(limit(filters));
        return queryResult(type, query);
    }

    private AuditReportResult logEntries(AuditReportType type, AuditReportFilters filters) {
        Query query = baseQuery("""
                SELECT
                    l.created_at,
                    p.correlation_id,
                    p.mixer_id,
                    event_type.code AS event_type,
                    operation.code AS operation,
                    component.code AS component,
                    action.code AS action,
                    level.code AS level,
                    status.code AS status,
                    l.duration_ms,
                    l.message
                FROM audit_log_entry l
                JOIN audit_process p ON p.id = l.process_id
                JOIN ref_action action ON action.id = l.action_id
                JOIN ref_component component ON component.id = action.component_id
                JOIN ref_log_level level ON level.id = l.level_id
                JOIN ref_log_status status ON status.id = l.status_id
                LEFT JOIN ref_event_type event_type ON event_type.id = p.event_type_id
                LEFT JOIN ref_operation_alias operation ON operation.id = p.operation_alias_id
                LEFT JOIN ref_log_status final_status ON final_status.id = p.final_status_id
                """, filters, "p", "final_status", "operation");
        addCodeFilter(query, filters.component(), "component.code");
        query.sql().append("""
                ORDER BY l.created_at DESC
                LIMIT ?
                """);
        query.params().add(limit(filters));
        return queryResult(type, query);
    }

    private AuditReportResult httpExchanges(AuditReportType type, AuditReportFilters filters) {
        Query query = httpBaseQuery("""
                SELECT
                    h.started_at,
                    h.finished_at,
                    p.correlation_id,
                    p.mixer_id,
                    component.code AS component,
                    service.code AS service,
                    endpoint.alias_code AS endpoint,
                    h.direction,
                    h.http_method,
                    h.http_status,
                    h.duration_ms,
                    h.request_hash,
                    h.response_hash,
                    h.external_operation_hash,
                    (h.request_masked IS NOT NULL) AS has_request_payload,
                    (h.response_masked IS NOT NULL) AS has_response_payload
                """, filters);
        query.sql().append("""
                ORDER BY COALESCE(h.started_at, l.created_at) DESC
                LIMIT ?
                """);
        query.params().add(limit(filters));
        return queryResult(type, query);
    }

    private AuditReportResult errors(AuditReportType type, AuditReportFilters filters) {
        Query query = baseQuery("""
                SELECT
                    e.created_at,
                    p.correlation_id,
                    p.mixer_id,
                    event_type.code AS event_type,
                    operation.code AS operation,
                    component.code AS component,
                    action.code AS action,
                    e.error_class,
                    e.error_message,
                    LEFT(e.stack_trace, 500) AS stack_trace_preview
                FROM audit_error e
                JOIN audit_log_entry l ON l.id = e.log_entry_id
                JOIN audit_process p ON p.id = l.process_id
                JOIN ref_action action ON action.id = l.action_id
                JOIN ref_component component ON component.id = action.component_id
                LEFT JOIN ref_event_type event_type ON event_type.id = p.event_type_id
                LEFT JOIN ref_operation_alias operation ON operation.id = p.operation_alias_id
                LEFT JOIN ref_log_status final_status ON final_status.id = p.final_status_id
                """, filters, "p", "final_status", "operation");
        addCodeFilter(query, filters.component(), "component.code");
        query.sql().append("""
                ORDER BY e.created_at DESC
                LIMIT ?
                """);
        query.params().add(limit(filters));
        return queryResult(type, query);
    }

    private AuditReportResult endpoints(AuditReportType type, AuditReportFilters filters) {
        Query query = httpBaseQuery("""
                SELECT
                    service.code AS service,
                    endpoint.alias_code AS endpoint,
                    h.direction,
                    h.http_method,
                    COUNT(*) AS total,
                    COUNT(*) FILTER (WHERE h.http_status >= 400 OR h.http_status IS NULL) AS failed,
                    MIN(h.duration_ms) AS min_duration_ms,
                    ROUND(AVG(h.duration_ms))::bigint AS avg_duration_ms,
                    MAX(h.duration_ms) AS max_duration_ms
                """, filters);
        query.sql().append("""
                GROUP BY service.code, endpoint.alias_code, h.direction, h.http_method
                ORDER BY total DESC, endpoint.alias_code
                LIMIT ?
                """);
        query.params().add(limit(filters));
        return queryResult(type, query);
    }

    private AuditReportResult components(AuditReportType type, AuditReportFilters filters) {
        Query query = baseQuery("""
                SELECT
                    component.code AS component,
                    action.code AS action,
                    level.code AS level,
                    status.code AS status,
                    COUNT(*) AS total,
                    ROUND(AVG(l.duration_ms))::bigint AS avg_duration_ms,
                    MAX(l.created_at) AS last_seen_at
                FROM audit_log_entry l
                JOIN audit_process p ON p.id = l.process_id
                JOIN ref_action action ON action.id = l.action_id
                JOIN ref_component component ON component.id = action.component_id
                JOIN ref_log_level level ON level.id = l.level_id
                JOIN ref_log_status status ON status.id = l.status_id
                LEFT JOIN ref_operation_alias operation ON operation.id = p.operation_alias_id
                LEFT JOIN ref_log_status final_status ON final_status.id = p.final_status_id
                """, filters, "p", "final_status", "operation");
        addCodeFilter(query, filters.component(), "component.code");
        query.sql().append("""
                GROUP BY component.code, action.code, level.code, status.code
                ORDER BY total DESC, component.code, action.code
                LIMIT ?
                """);
        query.params().add(limit(filters));
        return queryResult(type, query);
    }

    private AuditReportResult operations(AuditReportType type, AuditReportFilters filters) {
        Query query = baseQuery("""
                SELECT
                    event_type.code AS event_type,
                    operation.code AS operation,
                    final_status.code AS final_status,
                    COUNT(*) AS total,
                    COUNT(*) FILTER (WHERE p.mixer_id IS NOT NULL) AS with_mixer,
                    MIN(p.started_at) AS first_seen_at,
                    MAX(p.started_at) AS last_seen_at
                FROM audit_process p
                LEFT JOIN ref_event_type event_type ON event_type.id = p.event_type_id
                LEFT JOIN ref_operation_alias operation ON operation.id = p.operation_alias_id
                LEFT JOIN ref_log_status final_status ON final_status.id = p.final_status_id
                """, filters, "p", "final_status", "operation");
        query.sql().append("""
                GROUP BY event_type.code, operation.code, final_status.code
                ORDER BY total DESC, operation.code
                LIMIT ?
                """);
        query.params().add(limit(filters));
        return queryResult(type, query);
    }

    private AuditReportResult mixers(AuditReportType type, AuditReportFilters filters) {
        Query query = baseQuery("""
                SELECT
                    p.mixer_id,
                    COUNT(*) AS processes,
                    COUNT(*) FILTER (WHERE final_status.code = 'SUCCESS') AS success,
                    COUNT(*) FILTER (WHERE final_status.code = 'FAILED') AS failed,
                    COUNT(DISTINCT e.id) AS errors,
                    MIN(p.started_at) AS first_seen_at,
                    MAX(p.started_at) AS last_seen_at
                FROM audit_process p
                LEFT JOIN ref_log_status final_status ON final_status.id = p.final_status_id
                LEFT JOIN ref_operation_alias operation ON operation.id = p.operation_alias_id
                LEFT JOIN audit_log_entry l ON l.process_id = p.id
                LEFT JOIN audit_error e ON e.log_entry_id = l.id
                """, filters, "p", "final_status", "operation");
        query.sql().append("""
                GROUP BY p.mixer_id
                ORDER BY processes DESC, p.mixer_id
                LIMIT ?
                """);
        query.params().add(limit(filters));
        return queryResult(type, query);
    }

    private AuditReportResult externalServices(AuditReportType type, AuditReportFilters filters) {
        Query query = httpBaseQuery("""
                SELECT
                    service.code AS service,
                    endpoint.alias_code AS endpoint,
                    h.http_method,
                    COUNT(*) AS calls,
                    COUNT(*) FILTER (WHERE h.http_status >= 400 OR h.http_status IS NULL) AS failed_or_unknown,
                    ROUND(AVG(h.duration_ms))::bigint AS avg_duration_ms,
                    MAX(h.finished_at) AS last_call_at
                """, filters);
        addRawCondition(query, "h.direction = 'OUTBOUND'");
        query.sql().append("""
                GROUP BY service.code, endpoint.alias_code, h.http_method
                ORDER BY calls DESC, service.code, endpoint.alias_code
                LIMIT ?
                """);
        query.params().add(limit(filters));
        return queryResult(type, query);
    }

    private AuditReportResult timeline(AuditReportType type, AuditReportFilters filters) {
        Query query = baseQuery("""
                SELECT
                    date_trunc('hour', p.started_at) AS hour,
                    COUNT(*) AS processes,
                    COUNT(*) FILTER (WHERE final_status.code = 'SUCCESS') AS success,
                    COUNT(*) FILTER (WHERE final_status.code = 'FAILED') AS failed,
                    COUNT(*) FILTER (WHERE final_status.code = 'STARTED') AS open
                FROM audit_process p
                LEFT JOIN ref_log_status final_status ON final_status.id = p.final_status_id
                LEFT JOIN ref_operation_alias operation ON operation.id = p.operation_alias_id
                """, filters, "p", "final_status", "operation");
        query.sql().append("""
                GROUP BY date_trunc('hour', p.started_at)
                ORDER BY hour DESC
                LIMIT ?
                """);
        query.params().add(limit(filters));
        return queryResult(type, query);
    }

    private AuditReportResult slowRequests(AuditReportType type, AuditReportFilters filters) {
        Query query = httpBaseQuery("""
                SELECT
                    h.duration_ms,
                    h.started_at,
                    p.correlation_id,
                    p.mixer_id,
                    service.code AS service,
                    endpoint.alias_code AS endpoint,
                    h.direction,
                    h.http_method,
                    h.http_status,
                    component.code AS component
                """, filters);
        query.sql().append("""
                ORDER BY h.duration_ms DESC NULLS LAST, h.started_at DESC
                LIMIT ?
                """);
        query.params().add(limit(filters));
        return queryResult(type, query);
    }

    private Query httpBaseQuery(String select, AuditReportFilters filters) {
        Query query = baseQuery(select + """
                FROM audit_http_exchange h
                JOIN audit_log_entry l ON l.id = h.log_entry_id
                JOIN audit_process p ON p.id = l.process_id
                JOIN ref_action action ON action.id = l.action_id
                JOIN ref_component component ON component.id = action.component_id
                LEFT JOIN ref_endpoint_alias endpoint ON endpoint.id = h.endpoint_alias_id
                LEFT JOIN ref_external_service service ON service.id = endpoint.service_id
                LEFT JOIN ref_operation_alias operation ON operation.id = p.operation_alias_id
                LEFT JOIN ref_log_status final_status ON final_status.id = p.final_status_id
                """, filters, "p", "final_status", "operation");
        addCodeFilter(query, filters.component(), "component.code");
        addCodeFilter(query, filters.endpoint(), "endpoint.alias_code");
        addCodeFilter(query, filters.direction(), "h.direction");
        return query;
    }

    private Query baseQuery(String fromSql,
                            AuditReportFilters filters,
                            String processAlias,
                            String statusAlias,
                            String operationAlias) {
        StringBuilder sql = new StringBuilder(fromSql);
        List<Object> params = new ArrayList<>();
        sql.append(" WHERE 1 = 1\n");
        addTimeFilter(sql, params, filters.from(), processAlias + ".started_at", ">=");
        addTimeFilter(sql, params, filters.to(), processAlias + ".started_at", "<=");
        if (filters.mixerId() != null) {
            sql.append(" AND ").append(processAlias).append(".mixer_id = ?\n");
            params.add(filters.mixerId());
        }
        addCodeFilter(sql, params, filters.status(), statusAlias + ".code");
        addCodeFilter(sql, params, filters.operation(), operationAlias + ".code");
        return new Query(sql, params);
    }

    private long count(String fromSql,
                       AuditReportFilters filters,
                       String processAlias,
                       String statusAlias,
                       String operationAlias) {
        Query query = baseQuery(fromSql, filters, processAlias, statusAlias, operationAlias);
        Long value = jdbcTemplate.queryForObject(query.sql().toString(), Long.class, query.params().toArray());
        return value == null ? 0L : value;
    }

    private long count(String fromSql,
                       AuditReportFilters filters,
                       String processAlias,
                       String statusAlias,
                       String operationAlias,
                       String rawCondition,
                       String httpAlias,
                       String endpointAlias) {
        Query query = baseQuery(fromSql, filters, processAlias, statusAlias, operationAlias);
        addCodeFilter(query, filters.endpoint(), endpointAlias + ".alias_code");
        addCodeFilter(query, filters.direction(), httpAlias + ".direction");
        addRawCondition(query, rawCondition);
        Long value = jdbcTemplate.queryForObject(query.sql().toString(), Long.class, query.params().toArray());
        return value == null ? 0L : value;
    }

    private long count(String fromSql,
                       AuditReportFilters filters,
                       String processAlias,
                       String statusAlias,
                       String operationAlias,
                       String httpAlias,
                       String endpointAlias) {
        Query query = baseQuery(fromSql, filters, processAlias, statusAlias, operationAlias);
        addCodeFilter(query, filters.endpoint(), endpointAlias + ".alias_code");
        addCodeFilter(query, filters.direction(), httpAlias + ".direction");
        Long value = jdbcTemplate.queryForObject(query.sql().toString(), Long.class, query.params().toArray());
        return value == null ? 0L : value;
    }

    private void addTimeFilter(StringBuilder sql, List<Object> params, Instant value, String column, String operator) {
        if (value == null) {
            return;
        }
        sql.append(" AND ").append(column).append(' ').append(operator).append(" ?\n");
        params.add(Timestamp.from(value));
    }

    private void addCodeFilter(Query query, String value, String column) {
        addCodeFilter(query.sql(), query.params(), value, column);
    }

    private void addCodeFilter(StringBuilder sql, List<Object> params, String value, String column) {
        if (value == null || value.isBlank() || "all".equalsIgnoreCase(value)) {
            return;
        }
        sql.append(" AND ").append(column).append(" = ?\n");
        params.add(value.trim());
    }

    private void addRawCondition(Query query, String condition) {
        if (condition == null || condition.isBlank()) {
            return;
        }
        query.sql().append(" AND ").append(condition).append('\n');
    }

    private AuditReportResult queryResult(AuditReportType type, Query query) {
        List<Map<String, Object>> rows = query(query.sql().toString(), query.params());
        List<String> columns = rows.isEmpty() ? List.of() : new ArrayList<>(rows.get(0).keySet());
        return result(type, columns, rows);
    }

    private AuditReportResult result(AuditReportType type, List<String> columns, List<Map<String, Object>> rows) {
        return new AuditReportResult(
                type.getId(),
                type.getTitle(),
                type.getDescription(),
                Instant.now(),
                columns,
                rows);
    }

    private List<Map<String, Object>> query(String sql, List<Object> params) {
        return jdbcTemplate.queryForList(sql, params.toArray())
                .stream()
                .map(this::normalizeRow)
                .toList();
    }

    private List<String> codes(String sql) {
        return jdbcTemplate.queryForList(sql, String.class);
    }

    private Map<String, Object> normalizeRow(Map<String, Object> row) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            normalized.put(entry.getKey(), normalizeValue(entry.getValue()));
        }
        return normalized;
    }

    private Object normalizeValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant().toString();
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant().toString();
        }
        if (value instanceof Instant instant) {
            return instant.toString();
        }
        if (value instanceof UUID uuid) {
            return uuid.toString();
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros();
        }
        return value;
    }

    private Map<String, Object> metric(String name, Object value) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("metric", name);
        row.put("value", value);
        return row;
    }

    private int limit(AuditReportFilters filters) {
        return Math.max(1, Math.min(filters.limit(), MAX_LIMIT));
    }

    private record Query(StringBuilder sql, List<Object> params) {
    }
}
