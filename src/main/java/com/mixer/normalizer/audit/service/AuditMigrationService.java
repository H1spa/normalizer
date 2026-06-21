package com.mixer.normalizer.audit.service;

import com.mixer.normalizer.config.AuditProperties;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.ServiceConfigurationError;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class AuditMigrationService {
    private static final Logger log = LoggerFactory.getLogger(AuditMigrationService.class);

    private final DataSource dataSource;
    private final AuditProperties properties;
    private final AtomicBoolean migrated = new AtomicBoolean();
    private final AtomicBoolean running = new AtomicBoolean();

    public AuditMigrationService(DataSource dataSource, AuditProperties properties) {
        this.dataSource = dataSource;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void migrateAfterStartup() {
        migrateIfNeeded();
    }

    @Scheduled(fixedDelayString = "${logging-audit.migration-retry-millis:30000}")
    public void retryMigration() {
        migrateIfNeeded();
    }

    private void migrateIfNeeded() {
        if (!properties.isEnabled() || migrated.get() || !running.compareAndSet(false, true)) {
            return;
        }
        try {
            Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .load()
                    .migrate();
            migrated.set(true);
            log.info("Audit database migrations completed");
        } catch (RuntimeException | ServiceConfigurationError e) {
            log.warn("Audit database unavailable; migration will be retried ({})", e.getClass().getSimpleName());
        } finally {
            running.set(false);
        }
    }
}
