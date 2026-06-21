package com.mixer.normalizer.audit;

import com.mixer.normalizer.service.EventNormalizer;
import org.springframework.stereotype.Component;

@Component
public class AuditAliasResolver {

    public String inboundEndpoint(String method, String path) {
        if ("POST".equalsIgnoreCase(method)) {
            return switch (path) {
                case "/scoop" -> "ENDPOINT_ALIAS_001";
                case "/table" -> "ENDPOINT_ALIAS_002";
                case "/shovel_mixer" -> "ENDPOINT_ALIAS_003";
                case "/shovel_slag" -> "ENDPOINT_ALIAS_004";
                case "/sampling" -> "ENDPOINT_ALIAS_005";
                default -> null;
            };
        }
        if ("PUT".equalsIgnoreCase(method) && path != null && path.matches("/equipment/[^/]+")) {
            return "ENDPOINT_ALIAS_006";
        }
        return null;
    }

    public String operation(EventNormalizer.OpType type) {
        return switch (type) {
            case INGOTS -> "OPERATION_ALIAS_001";
            case FLUX -> "OPERATION_ALIAS_002";
            case DISLAG -> "OPERATION_ALIAS_003";
            case SCOOP -> "OPERATION_ALIAS_004";
            case PROBA -> "OPERATION_ALIAS_005";
        };
    }

    public String phase(String status) {
        if ("begin".equalsIgnoreCase(status)) {
            return AuditCodes.EVENT_BEGIN;
        }
        if ("finish".equalsIgnoreCase(status)) {
            return AuditCodes.EVENT_FINISH;
        }
        return AuditCodes.EVENT_UNKNOWN;
    }
}
