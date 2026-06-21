CREATE INDEX idx_audit_process_mixer_id ON audit_process(mixer_id);
CREATE INDEX idx_audit_process_started_at ON audit_process(started_at);
CREATE INDEX idx_audit_process_operation_alias ON audit_process(operation_alias_id);
CREATE INDEX idx_audit_log_entry_process_id ON audit_log_entry(process_id);
CREATE INDEX idx_audit_log_entry_created_at ON audit_log_entry(created_at);
CREATE INDEX idx_audit_http_exchange_alias ON audit_http_exchange(endpoint_alias_id);
CREATE INDEX idx_audit_http_exchange_started_at ON audit_http_exchange(started_at);
CREATE INDEX idx_audit_error_created_at ON audit_error(created_at);
