package com.mixer.normalizer.audit.web;

import com.mixer.normalizer.audit.AuditAliasResolver;
import com.mixer.normalizer.audit.AuditCodes;
import com.mixer.normalizer.audit.context.AuditContext;
import com.mixer.normalizer.audit.service.AuditLogService;
import com.mixer.normalizer.config.AuditProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Component
public class AuditRequestFilter extends OncePerRequestFilter {
    private final AuditAliasResolver aliasResolver;
    private final AuditLogService auditLogService;
    private final AuditProperties properties;

    public AuditRequestFilter(AuditAliasResolver aliasResolver,
                              AuditLogService auditLogService,
                              AuditProperties properties) {
        this.aliasResolver = aliasResolver;
        this.auditLogService = auditLogService;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String endpointAlias = aliasResolver.inboundEndpoint(request.getMethod(), request.getRequestURI());
        if (endpointAlias == null) {
            filterChain.doFilter(request, response);
            return;
        }

        ContentCachingRequestWrapper cachedRequest = new ContentCachingRequestWrapper(
                request,
                properties.getMaxPayloadChars());
        ContentCachingResponseWrapper cachedResponse = new ContentCachingResponseWrapper(response);
        AuditContext context = auditLogService.beginRequest(endpointAlias);
        cachedResponse.setHeader(properties.getCorrelationHeader(), context.correlationId().toString());

        Exception failure = null;
        try {
            filterChain.doFilter(cachedRequest, cachedResponse);
        } catch (IOException | ServletException | RuntimeException e) {
            failure = e;
            auditLogService.recordError(AuditCodes.COMPONENT_WEB, e, elapsed(context.startedAt()));
            throw e;
        } finally {
            Instant finishedAt = Instant.now();
            int status = cachedResponse.getStatus();
            boolean successful = failure == null && status < 400;

            auditLogService.recordHttp(
                    AuditCodes.COMPONENT_WEB,
                    endpointAlias,
                    "INBOUND",
                    request.getMethod(),
                    status,
                    body(cachedRequest.getContentAsByteArray(), request.getCharacterEncoding()),
                    body(cachedResponse.getContentAsByteArray(), response.getCharacterEncoding()),
                    null,
                    context.startedAt(),
                    finishedAt);
            auditLogService.completeCurrent(successful);
            auditLogService.clearContext();
            cachedResponse.copyBodyToResponse();
        }
    }

    private String body(byte[] content, String encoding) {
        if (content == null || content.length == 0) {
            return null;
        }
        Charset charset = StandardCharsets.UTF_8;
        if (encoding != null && Charset.isSupported(encoding)) {
            charset = Charset.forName(encoding);
        }
        return new String(content, charset);
    }

    private long elapsed(Instant startedAt) {
        return Math.max(0L, java.time.Duration.between(startedAt, Instant.now()).toMillis());
    }
}
