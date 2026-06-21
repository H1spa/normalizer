package com.mixer.normalizer.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.mixer.normalizer.config.AuditProperties;
import org.springframework.stereotype.Component;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class AuditPayloadSanitizer {
    private static final String MASK = "[MASKED]";
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "authorization", "password", "token", "accesstoken", "refreshtoken",
            "secret", "apikey", "cookie", "setcookie", "folder",
            "imagefolderpath", "path", "url", "uri", "host", "domain", "domainname",
            "address", "ip", "id", "externalid", "eventid", "tagid", "tagids", "tgid",
            "casthouseid", "lasthouseid");
    private static final Pattern URL = Pattern.compile("(?i)\\b(?:https?|ftp)://[^\\s\\\"']+");
    private static final Pattern IPV4 = Pattern.compile("(?<![\\d.])(?:\\d{1,3}\\.){3}\\d{1,3}(?![\\d.])");
    private static final Pattern DOMAIN = Pattern.compile("(?i)\\b(?:[a-z0-9-]+\\.)+(?:local|internal|lan|com|net|org|ru)\\b");
    private static final Pattern AUTH = Pattern.compile("(?i)(authorization|password|token|secret|api[_-]?key)\\s*[:=]\\s*[^,;\\r\\n}]+");
    private static final Pattern ROUTE = Pattern.compile("(?i)/(?:scoop|table|shovel_mixer|shovel_slag|sampling|equipment)(?:/[^\\s\\\"']*)?");
    private static final Pattern QUERY = Pattern.compile("\\?[^\\s\\\"']+");
    private static final Pattern WINDOWS_PATH = Pattern.compile("(?i)\\b[a-z]:\\\\[^\\r\\n\\\"']+");
    private static final Pattern OPERATION_NAME = Pattern.compile("(?i)\\b(?:flux|dislag|ingots|scoop|proba|separation)\\b");

    private final ObjectMapper objectMapper;
    private final AuditProperties properties;

    public AuditPayloadSanitizer(ObjectMapper objectMapper, AuditProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public String serializeAndSanitize(Object value) {
        if (value == null) {
            return null;
        }
        String text;
        if (value instanceof String string) {
            text = string;
        } else {
            try {
                text = objectMapper.writeValueAsString(value);
            } catch (JsonProcessingException e) {
                text = value.getClass().getSimpleName();
            }
        }
        return sanitize(text);
    }

    public String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        if (!properties.isMaskSecrets()) {
            return MASK;
        }

        String sanitized;
        try {
            JsonNode root = objectMapper.readTree(value);
            sanitized = sanitizeNode(root).toString();
        } catch (JsonProcessingException e) {
            sanitized = sanitizeText(value);
        }
        return truncate(sanitized);
    }

    public String hash(Object value) {
        String sanitized = serializeAndSanitize(value);
        if (sanitized == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(sanitized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public String stackTrace(Throwable error) {
        if (error == null) {
            return null;
        }
        StringWriter writer = new StringWriter();
        error.printStackTrace(new PrintWriter(writer));
        return sanitize(writer.toString());
    }

    private JsonNode sanitizeNode(JsonNode node) {
        JsonNode copy = node.deepCopy();
        sanitizeInPlace(copy);
        return copy;
    }

    private void sanitizeInPlace(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String normalized = field.getKey().toLowerCase(Locale.ROOT).replaceAll("[_-]", "");
                if (SENSITIVE_KEYS.contains(normalized)) {
                    objectNode.set(field.getKey(), TextNode.valueOf(MASK));
                } else if (field.getValue().isTextual()) {
                    objectNode.set(field.getKey(), TextNode.valueOf(sanitizeText(field.getValue().asText())));
                } else {
                    sanitizeInPlace(field.getValue());
                }
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                sanitizeInPlace(item);
            }
        }
    }

    private String sanitizeText(String value) {
        String result = URL.matcher(value).replaceAll("[URL]");
        result = IPV4.matcher(result).replaceAll("[IP]");
        result = DOMAIN.matcher(result).replaceAll("[DOMAIN]");
        result = AUTH.matcher(result).replaceAll("$1=" + MASK);
        result = ROUTE.matcher(result).replaceAll("[ENDPOINT]");
        result = QUERY.matcher(result).replaceAll("[QUERY]");
        result = WINDOWS_PATH.matcher(result).replaceAll("[PATH]");
        return OPERATION_NAME.matcher(result).replaceAll("[OPERATION]");
    }

    private String truncate(String value) {
        if (value.length() <= properties.getMaxPayloadChars()) {
            return value;
        }
        return value.substring(0, properties.getMaxPayloadChars()) + "[TRUNCATED]";
    }
}
