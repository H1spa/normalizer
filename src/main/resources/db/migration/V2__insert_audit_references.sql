INSERT INTO ref_log_level (code, name) VALUES
    ('INFO', 'Information'),
    ('WARN', 'Warning'),
    ('ERROR', 'Error');

INSERT INTO ref_log_status (code, name) VALUES
    ('STARTED', 'Started'),
    ('SUCCESS', 'Success'),
    ('FAILED', 'Failed');

INSERT INTO ref_event_type (code, name) VALUES
    ('EVENT_ALIAS_000', 'Event alias 000'),
    ('EVENT_ALIAS_001', 'Event alias 001'),
    ('EVENT_ALIAS_002', 'Event alias 002'),
    ('EVENT_ALIAS_003', 'Event alias 003'),
    ('EVENT_ALIAS_004', 'Event alias 004');

INSERT INTO ref_operation_alias (code, name) VALUES
    ('OPERATION_ALIAS_000', 'Operation alias 000'),
    ('OPERATION_ALIAS_001', 'Operation alias 001'),
    ('OPERATION_ALIAS_002', 'Operation alias 002'),
    ('OPERATION_ALIAS_003', 'Operation alias 003'),
    ('OPERATION_ALIAS_004', 'Operation alias 004'),
    ('OPERATION_ALIAS_005', 'Operation alias 005'),
    ('OPERATION_ALIAS_006', 'Operation alias 006');

INSERT INTO ref_component (code, name) VALUES
    ('COMPONENT_ALIAS_001', 'Component alias 001'),
    ('COMPONENT_ALIAS_002', 'Component alias 002'),
    ('COMPONENT_ALIAS_003', 'Component alias 003'),
    ('COMPONENT_ALIAS_004', 'Component alias 004'),
    ('COMPONENT_ALIAS_005', 'Component alias 005'),
    ('COMPONENT_ALIAS_006', 'Component alias 006');

INSERT INTO ref_external_service (code, name) VALUES
    ('SERVICE_ALIAS_001', 'Service alias 001'),
    ('SERVICE_ALIAS_002', 'Service alias 002');

INSERT INTO ref_endpoint_alias (service_id, alias_code, operation_code, operation_name) VALUES
    (NULL, 'ENDPOINT_ALIAS_001', 'OPERATION_001', 'Operation 001'),
    (NULL, 'ENDPOINT_ALIAS_002', 'OPERATION_002', 'Operation 002'),
    (NULL, 'ENDPOINT_ALIAS_003', 'OPERATION_003', 'Operation 003'),
    (NULL, 'ENDPOINT_ALIAS_004', 'OPERATION_004', 'Operation 004'),
    (NULL, 'ENDPOINT_ALIAS_005', 'OPERATION_005', 'Operation 005'),
    (NULL, 'ENDPOINT_ALIAS_006', 'OPERATION_006', 'Operation 006');

INSERT INTO ref_endpoint_alias (service_id, alias_code, operation_code, operation_name)
SELECT id, 'ENDPOINT_ALIAS_101', 'OPERATION_101', 'Operation 101'
FROM ref_external_service WHERE code = 'SERVICE_ALIAS_001';

INSERT INTO ref_endpoint_alias (service_id, alias_code, operation_code, operation_name)
SELECT id, 'ENDPOINT_ALIAS_102', 'OPERATION_102', 'Operation 102'
FROM ref_external_service WHERE code = 'SERVICE_ALIAS_001';

INSERT INTO ref_endpoint_alias (service_id, alias_code, operation_code, operation_name)
SELECT id, 'ENDPOINT_ALIAS_201', 'OPERATION_201', 'Operation 201'
FROM ref_external_service WHERE code = 'SERVICE_ALIAS_002';

INSERT INTO ref_endpoint_alias (service_id, alias_code, operation_code, operation_name)
SELECT id, 'ENDPOINT_ALIAS_202', 'OPERATION_202', 'Operation 202'
FROM ref_external_service WHERE code = 'SERVICE_ALIAS_002';

INSERT INTO ref_endpoint_alias (service_id, alias_code, operation_code, operation_name)
SELECT id, 'ENDPOINT_ALIAS_203', 'OPERATION_203', 'Operation 203'
FROM ref_external_service WHERE code = 'SERVICE_ALIAS_002';

INSERT INTO ref_endpoint_alias (service_id, alias_code, operation_code, operation_name)
SELECT id, 'ENDPOINT_ALIAS_204', 'OPERATION_204', 'Operation 204'
FROM ref_external_service WHERE code = 'SERVICE_ALIAS_002';

INSERT INTO ref_action (component_id, code, name)
SELECT component.id, action.action_code, action.action_name
FROM (VALUES
    ('COMPONENT_ALIAS_001', 'ACTION_ALIAS_001', 'Action alias 001'),
    ('COMPONENT_ALIAS_001', 'ACTION_ALIAS_002', 'Action alias 002'),
    ('COMPONENT_ALIAS_001', 'ACTION_ALIAS_008', 'Action alias 008'),
    ('COMPONENT_ALIAS_001', 'ACTION_ALIAS_009', 'Action alias 009'),
    ('COMPONENT_ALIAS_001', 'ACTION_ALIAS_010', 'Action alias 010'),
    ('COMPONENT_ALIAS_002', 'ACTION_ALIAS_003', 'Action alias 003'),
    ('COMPONENT_ALIAS_002', 'ACTION_ALIAS_004', 'Action alias 004'),
    ('COMPONENT_ALIAS_002', 'ACTION_ALIAS_005', 'Action alias 005'),
    ('COMPONENT_ALIAS_002', 'ACTION_ALIAS_006', 'Action alias 006'),
    ('COMPONENT_ALIAS_002', 'ACTION_ALIAS_010', 'Action alias 010'),
    ('COMPONENT_ALIAS_003', 'ACTION_ALIAS_007', 'Action alias 007'),
    ('COMPONENT_ALIAS_003', 'ACTION_ALIAS_008', 'Action alias 008'),
    ('COMPONENT_ALIAS_003', 'ACTION_ALIAS_010', 'Action alias 010'),
    ('COMPONENT_ALIAS_004', 'ACTION_ALIAS_007', 'Action alias 007'),
    ('COMPONENT_ALIAS_004', 'ACTION_ALIAS_008', 'Action alias 008'),
    ('COMPONENT_ALIAS_004', 'ACTION_ALIAS_010', 'Action alias 010'),
    ('COMPONENT_ALIAS_005', 'ACTION_ALIAS_010', 'Action alias 010'),
    ('COMPONENT_ALIAS_006', 'ACTION_ALIAS_009', 'Action alias 009'),
    ('COMPONENT_ALIAS_006', 'ACTION_ALIAS_010', 'Action alias 010'),
    ('COMPONENT_ALIAS_006', 'ACTION_ALIAS_011', 'Action alias 011'),
    ('COMPONENT_ALIAS_006', 'ACTION_ALIAS_012', 'Action alias 012')
) AS action(component_code, action_code, action_name)
JOIN ref_component component ON component.code = action.component_code;
