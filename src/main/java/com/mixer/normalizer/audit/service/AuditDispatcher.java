package com.mixer.normalizer.audit.service;

import com.mixer.normalizer.config.AuditProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class AuditDispatcher {
    private static final Logger log = LoggerFactory.getLogger(AuditDispatcher.class);

    private final AuditProperties properties;
    private final ThreadPoolExecutor executor;
    private final AtomicLong droppedRecords = new AtomicLong();
    private final AtomicLong failedRecords = new AtomicLong();
    private final AtomicLong lastWarningMillis = new AtomicLong();

    public AuditDispatcher(AuditProperties properties) {
        this.properties = properties;
        this.executor = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(properties.getQueueCapacity()),
                runnable -> {
                    Thread thread = new Thread(runnable, "audit-writer");
                    thread.setDaemon(false);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    public void dispatch(Runnable operation) {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    operation.run();
                } catch (RuntimeException e) {
                    warnSkipped("persistence", e.getClass().getSimpleName(), failedRecords.incrementAndGet());
                }
            });
        } catch (RejectedExecutionException e) {
            long dropped = droppedRecords.incrementAndGet();
            warnSkipped("queue", e.getClass().getSimpleName(), dropped);
        }
    }

    public long getDroppedRecords() {
        return droppedRecords.get();
    }

    private void warnSkipped(String reason, String errorType, long skipped) {
        long now = System.currentTimeMillis();
        long previous = lastWarningMillis.get();
        long intervalMillis = TimeUnit.SECONDS.toMillis(properties.getWarningIntervalSeconds());
        if (now - previous >= intervalMillis && lastWarningMillis.compareAndSet(previous, now)) {
            log.warn("Audit record skipped reason={} error={} count={}", reason, errorType, skipped);
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(properties.getShutdownWaitSeconds(), TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}
