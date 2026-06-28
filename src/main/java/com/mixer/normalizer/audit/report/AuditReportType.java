package com.mixer.normalizer.audit.report;

import java.util.Arrays;
import java.util.Optional;

public enum AuditReportType {
    OVERVIEW("overview", "Обзор", "Основные показатели по журналу аудита"),
    PROCESSES("processes", "Процессы", "Процессы аудита и их итоговые статусы"),
    LOG_ENTRIES("log-entries", "Журнал", "События по компонентам и действиям"),
    HTTP_EXCHANGES("http-exchanges", "HTTP-обмены", "Входящие и исходящие HTTP-вызовы"),
    ERRORS("errors", "Ошибки", "Исключения и сведения о сбоях"),
    ENDPOINTS("endpoints", "Эндпоинты", "Нагрузка и задержки по HTTP-точкам"),
    COMPONENTS("components", "Компоненты", "Итоги по компонентам, действиям и статусам"),
    OPERATIONS("operations", "Операции", "Итоги по бизнес-операциям"),
    MIXERS("mixers", "Миксеры", "Активность и ошибки по миксерам"),
    EXTERNAL_SERVICES("external-services", "Внешние сервисы", "Использование внешних сервисов"),
    TIMELINE("timeline", "По часам", "Почасовая активность процессов"),
    SLOW_REQUESTS("slow-requests", "Медленные запросы", "Самые долгие HTTP-обмены");

    private final String id;
    private final String title;
    private final String description;

    AuditReportType(String id, String title, String description) {
        this.id = id;
        this.title = title;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public static Optional<AuditReportType> fromId(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(type -> type.id.equalsIgnoreCase(id.trim()))
                .findFirst();
    }
}
