package com.mixer.normalizer.audit.context;

public final class AuditContextHolder {
    private static final ThreadLocal<AuditContext> CURRENT = new ThreadLocal<>();

    private AuditContextHolder() {
    }

    public static AuditContext get() {
        return CURRENT.get();
    }

    public static void set(AuditContext context) {
        CURRENT.set(context);
    }

    public static void clear() {
        CURRENT.remove();
    }
}
