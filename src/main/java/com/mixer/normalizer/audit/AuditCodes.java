package com.mixer.normalizer.audit;

public final class AuditCodes {
    public static final String INFO = "INFO";
    public static final String WARN = "WARN";
    public static final String ERROR = "ERROR";
    public static final String STARTED = "STARTED";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";

    public static final String COMPONENT_WEB = "COMPONENT_ALIAS_001";
    public static final String COMPONENT_CORE = "COMPONENT_ALIAS_002";
    public static final String COMPONENT_EQUIPMENT = "COMPONENT_ALIAS_003";
    public static final String COMPONENT_EXTERNAL = "COMPONENT_ALIAS_004";
    public static final String COMPONENT_DATABASE = "COMPONENT_ALIAS_005";
    public static final String COMPONENT_POLLER = "COMPONENT_ALIAS_006";

    public static final String ACTION_RECEIVED = "ACTION_ALIAS_001";
    public static final String ACTION_VALIDATED = "ACTION_ALIAS_002";
    public static final String ACTION_PROCESSING = "ACTION_ALIAS_003";
    public static final String ACTION_NORMALIZED = "ACTION_ALIAS_004";
    public static final String ACTION_EQUIPMENT = "ACTION_ALIAS_005";
    public static final String ACTION_OUTPUT = "ACTION_ALIAS_006";
    public static final String ACTION_HTTP_REQUEST = "ACTION_ALIAS_007";
    public static final String ACTION_HTTP_RESPONSE = "ACTION_ALIAS_008";
    public static final String ACTION_COMPLETED = "ACTION_ALIAS_009";
    public static final String ACTION_FAILED = "ACTION_ALIAS_010";
    public static final String ACTION_POLL_STARTED = "ACTION_ALIAS_011";
    public static final String ACTION_POLL_COMPLETED = "ACTION_ALIAS_012";

    public static final String EVENT_UNKNOWN = "EVENT_ALIAS_000";
    public static final String EVENT_BEGIN = "EVENT_ALIAS_001";
    public static final String EVENT_FINISH = "EVENT_ALIAS_002";
    public static final String EVENT_POLL = "EVENT_ALIAS_003";
    public static final String EVENT_EQUIPMENT = "EVENT_ALIAS_004";

    public static final String OPERATION_UNKNOWN = "OPERATION_ALIAS_000";

    public static final String ENDPOINT_EXTERNAL_CREATE = "ENDPOINT_ALIAS_101";
    public static final String ENDPOINT_EXTERNAL_FINISH = "ENDPOINT_ALIAS_102";
    public static final String ENDPOINT_EQUIPMENT_AUTH = "ENDPOINT_ALIAS_201";
    public static final String ENDPOINT_EQUIPMENT_CONTEXT = "ENDPOINT_ALIAS_202";
    public static final String ENDPOINT_EQUIPMENT_DATA = "ENDPOINT_ALIAS_203";
    public static final String ENDPOINT_EQUIPMENT_LEGACY = "ENDPOINT_ALIAS_204";

    private AuditCodes() {
    }
}
