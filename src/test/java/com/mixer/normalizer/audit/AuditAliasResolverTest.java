package com.mixer.normalizer.audit;

import com.mixer.normalizer.service.EventNormalizer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuditAliasResolverTest {
    private final AuditAliasResolver resolver = new AuditAliasResolver();

    @Test
    void mapsBusinessOperationToOpaqueAlias() {
        String alias = resolver.operation(EventNormalizer.OpType.FLUX);

        assertThat(alias)
                .isEqualTo("OPERATION_ALIAS_002")
                .doesNotContainIgnoringCase("flux");
    }

    @Test
    void mapsInboundRouteWithoutReturningRouteText() {
        String alias = resolver.inboundEndpoint("POST", "/shovel_mixer");

        assertThat(alias)
                .isEqualTo("ENDPOINT_ALIAS_003")
                .doesNotContain("shovel_mixer");
    }

    @Test
    void ignoresUnregisteredRoutes() {
        assertThat(resolver.inboundEndpoint("GET", "/unknown")).isNull();
    }
}
