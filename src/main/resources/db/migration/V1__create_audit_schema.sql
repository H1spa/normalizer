CREATE TABLE ref_log_level (
    id SMALLSERIAL PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL
);

CREATE TABLE ref_log_status (
    id SMALLSERIAL PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL
);

CREATE TABLE ref_component (
    id SMALLSERIAL PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL
);

CREATE TABLE ref_event_type (
    id SMALLSERIAL PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL
);

CREATE TABLE ref_operation_alias (
    id SMALLSERIAL PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL
);

CREATE TABLE ref_external_service (
    id SMALLSERIAL PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL
);

CREATE TABLE ref_endpoint_alias (
    id SMALLSERIAL PRIMARY KEY,
    service_id SMALLINT REFERENCES ref_external_service(id),
    alias_code VARCHAR(100) NOT NULL UNIQUE,
    operation_code VARCHAR(100) NOT NULL,
    operation_name VARCHAR(255) NOT NULL
);

CREATE TABLE ref_action (
    id BIGSERIAL PRIMARY KEY,
    component_id SMALLINT NOT NULL REFERENCES ref_component(id),
    code VARCHAR(150) NOT NULL,
    name VARCHAR(255) NOT NULL,
    CONSTRAINT uq_action_component_code UNIQUE (component_id, code)
);

CREATE TABLE audit_process (
    id BIGSERIAL PRIMARY KEY,
    correlation_id UUID NOT NULL UNIQUE,
    mixer_id INTEGER,
    event_type_id SMALLINT REFERENCES ref_event_type(id),
    operation_alias_id SMALLINT REFERENCES ref_operation_alias(id),
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    final_status_id SMALLINT REFERENCES ref_log_status(id)
);

CREATE TABLE audit_log_entry (
    id BIGSERIAL PRIMARY KEY,
    process_id BIGINT NOT NULL REFERENCES audit_process(id),
    action_id BIGINT NOT NULL REFERENCES ref_action(id),
    level_id SMALLINT NOT NULL REFERENCES ref_log_level(id),
    status_id SMALLINT NOT NULL REFERENCES ref_log_status(id),
    message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    duration_ms BIGINT CHECK (duration_ms IS NULL OR duration_ms >= 0)
);

CREATE TABLE audit_http_exchange (
    id BIGSERIAL PRIMARY KEY,
    log_entry_id BIGINT NOT NULL REFERENCES audit_log_entry(id),
    endpoint_alias_id SMALLINT REFERENCES ref_endpoint_alias(id),
    direction VARCHAR(16) NOT NULL CHECK (direction IN ('INBOUND', 'OUTBOUND')),
    http_method VARCHAR(20),
    http_status INTEGER CHECK (http_status IS NULL OR http_status BETWEEN 100 AND 599),
    request_hash VARCHAR(64),
    response_hash VARCHAR(64),
    external_operation_hash VARCHAR(64),
    request_masked TEXT,
    response_masked TEXT,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    duration_ms BIGINT CHECK (duration_ms IS NULL OR duration_ms >= 0)
);

CREATE TABLE audit_error (
    id BIGSERIAL PRIMARY KEY,
    log_entry_id BIGINT NOT NULL REFERENCES audit_log_entry(id),
    error_class VARCHAR(255),
    error_message TEXT,
    stack_trace TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
