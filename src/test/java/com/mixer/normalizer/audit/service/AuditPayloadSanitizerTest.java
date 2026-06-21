package com.mixer.normalizer.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mixer.normalizer.config.AuditProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuditPayloadSanitizerTest {
    private AuditProperties properties;
    private AuditPayloadSanitizer sanitizer;

    @BeforeEach
    void setUp() {
        properties = new AuditProperties();
        sanitizer = new AuditPayloadSanitizer(new ObjectMapper(), properties);
    }

    @Test
    void masksSecretsAddressesRoutesAndOperationNames() {
        String input = """
                {
                  "password": "secret-value",
                  "token": "token-value",
                  "id": "external-id-value",
                  "tagIds": ["tag-1", "tag-2"],
                  "folder": "/private/images",
                  "message": "POST http://10.20.30.40/shovel_mixer?token=x on private.local started flux"
                }
                """;

        String result = sanitizer.sanitize(input);

        assertThat(result)
                .doesNotContain("secret-value")
                .doesNotContain("token-value")
                .doesNotContain("external-id-value")
                .doesNotContain("tag-1")
                .doesNotContain("/private/images")
                .doesNotContain("10.20.30.40")
                .doesNotContain("private.local")
                .doesNotContainIgnoringCase("shovel_mixer")
                .doesNotContainIgnoringCase("flux")
                .contains("[MASKED]")
                .contains("[URL]")
                .contains("[OPERATION]");
    }

    @Test
    void hashesOnlySanitizedRepresentation() {
        String first = sanitizer.hash("Authorization: Bearer first-secret");
        String second = sanitizer.hash("Authorization: Bearer second-secret");

        assertThat(first).hasSize(64);
        assertThat(first).isEqualTo(second);
    }

    @Test
    void refusesToStorePayloadWhenMaskingIsDisabled() {
        properties.setMaskSecrets(false);

        assertThat(sanitizer.sanitize("plain sensitive text")).isEqualTo("[MASKED]");
    }
}
