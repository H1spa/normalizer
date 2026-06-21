package com.mixer.normalizer.audit.repository;

import com.mixer.normalizer.audit.entity.AuditErrorRecord;
import com.mixer.normalizer.audit.entity.AuditHttpExchangeRecord;
import com.mixer.normalizer.audit.entity.AuditLogRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Repository
public class AuditRepository {
    private final JdbcTemplate jdbcTemplate;

    public AuditRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void startProcess(UUID correlationId, String eventTypeAlias) {
        jdbcTemplate.update("""
                INSERT INTO audit_process (correlation_id, event_type_id, final_status_id)
                SELECT ?, event_type.id, status.id
                FROM ref_event_type event_type
                CROSS JOIN ref_log_status status
                WHERE event_type.code = ? AND status.code = 'STARTED'
                ON CONFLICT (correlation_id) DO NOTHING
                """, correlationId, eventTypeAlias);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enrichProcess(UUID correlationId, Integer mixerId, String eventTypeAlias, String operationAlias) {
        jdbcTemplate.update("""
                UPDATE audit_process
                SET mixer_id = COALESCE(?, mixer_id),
                    event_type_id = (SELECT id FROM ref_event_type WHERE code = ?),
                    operation_alias_id = (SELECT id FROM ref_operation_alias WHERE code = ?)
                WHERE correlation_id = ?
                """, mixerId, eventTypeAlias, operationAlias, correlationId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void addLog(AuditLogRecord record) {
        insertLog(record);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void addHttpExchange(AuditHttpExchangeRecord record) {
        long logEntryId = insertLog(record.logEntry());
        jdbcTemplate.update("""
                INSERT INTO audit_http_exchange (
                    log_entry_id, endpoint_alias_id, direction, http_method, http_status,
                    request_hash, response_hash, external_operation_hash,
                    request_masked, response_masked, started_at, finished_at, duration_ms
                )
                SELECT ?, endpoint.id, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
                FROM ref_endpoint_alias endpoint
                WHERE endpoint.alias_code = ?
                """,
                logEntryId,
                record.direction(),
                record.httpMethod(),
                record.httpStatus(),
                record.requestHash(),
                record.responseHash(),
                record.externalOperationHash(),
                record.requestMasked(),
                record.responseMasked(),
                timestamp(record.startedAt()),
                timestamp(record.finishedAt()),
                record.logEntry().durationMillis(),
                record.endpointAlias());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void addError(AuditErrorRecord record) {
        long logEntryId = insertLog(record.logEntry());
        jdbcTemplate.update("""
                INSERT INTO audit_error (log_entry_id, error_class, error_message, stack_trace)
                VALUES (?, ?, ?, ?)
                """, logEntryId, record.errorClass(), record.errorMessage(), record.stackTrace());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeProcess(UUID correlationId, String finalStatus) {
        jdbcTemplate.update("""
                UPDATE audit_process
                SET completed_at = CURRENT_TIMESTAMP,
                    final_status_id = (SELECT id FROM ref_log_status WHERE code = ?)
                WHERE correlation_id = ?
                """, finalStatus, correlationId);
    }

    private long insertLog(AuditLogRecord record) {
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO audit_log_entry (
                    process_id, action_id, level_id, status_id, message, duration_ms
                )
                SELECT process.id, action.id, level.id, status.id, ?, ?
                FROM audit_process process
                JOIN ref_component component ON component.code = ?
                JOIN ref_action action ON action.component_id = component.id AND action.code = ?
                CROSS JOIN ref_log_level level
                CROSS JOIN ref_log_status status
                WHERE process.correlation_id = ?
                  AND level.code = ?
                  AND status.code = ?
                RETURNING id
                """,
                Long.class,
                record.message(),
                record.durationMillis(),
                record.componentCode(),
                record.actionCode(),
                record.correlationId(),
                record.levelCode(),
                record.statusCode());
        if (id == null) {
            throw new IllegalStateException("Audit log insert returned no id");
        }
        return id;
    }

    private Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
