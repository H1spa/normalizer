package com.mixer.normalizer.config;

import com.mixer.normalizer.service.EventNormalizer;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.Set;

/*
 * Настройки бизнес-логики normalizer.
 * Значения приходят из application.yml и могут быть переопределены переменными окружения.
 */
@Component
@ConfigurationProperties(prefix = "normalizer")
public class NormalizerProperties {
    // Форматы времени и зоны.
    private String fixedZone = "+07:00";
    private String defaultInputZone = "+07:00";
    private String inputTimePattern = "yyyy-MM-dd_HH-mm-ss";
    private String inputOffsetPattern = "XX";
    private String storageTimePattern = "yyyy-MM-dd_HH-mm-ss";
    private String outerTimePattern = "yyyy-MM-dd_HH-mm-ssxx";

    public String getFixedZone() {
        return fixedZone;
    }

    public void setFixedZone(String fixedZone) {
        this.fixedZone = fixedZone;
    }

    public String getDefaultInputZone() {
        return defaultInputZone;
    }

    public void setDefaultInputZone(String defaultInputZone) {
        this.defaultInputZone = defaultInputZone;
    }

    public String getInputTimePattern() {
        return inputTimePattern;
    }

    public void setInputTimePattern(String inputTimePattern) {
        this.inputTimePattern = inputTimePattern;
    }

    public String getInputOffsetPattern() {
        return inputOffsetPattern;
    }

    public void setInputOffsetPattern(String inputOffsetPattern) {
        this.inputOffsetPattern = inputOffsetPattern;
    }

    public String getStorageTimePattern() {
        return storageTimePattern;
    }

    public void setStorageTimePattern(String storageTimePattern) {
        this.storageTimePattern = storageTimePattern;
    }

    public String getOuterTimePattern() {
        return outerTimePattern;
    }

    public void setOuterTimePattern(String outerTimePattern) {
        this.outerTimePattern = outerTimePattern;
    }

    public String getSeparationType() {
        return "separation";
    }

    public ZoneId fixedZoneId() {
        return ZoneId.of(fixedZone);
    }

    public ZoneId defaultInputZoneId() {
        return ZoneId.of(defaultInputZone);
    }

    public DateTimeFormatter storageFormatter() {
        return DateTimeFormatter.ofPattern(storageTimePattern);
    }

    public DateTimeFormatter outerFormatter() {
        return DateTimeFormatter.ofPattern(outerTimePattern);
    }

    // Открытая шторка нужна только операциям, которые физически работают через открытую шторку.
    public Set<EventNormalizer.OpType> gateRequiredTypeSet() {
        Set<EventNormalizer.OpType> result = EnumSet.noneOf(EventNormalizer.OpType.class);
        result.add(EventNormalizer.OpType.FLUX);
        result.add(EventNormalizer.OpType.DISLAG);
        result.add(EventNormalizer.OpType.SCOOP);
        return result;
    }

    // Переводит внутренний тип операции в текст, который ожидает внешняя система.
    public String eventTypeName(EventNormalizer.OpType type) {
        return switch (type) {
            case FLUX -> "flux";
            case DISLAG -> "dislag";
            case INGOTS -> "ingots";
            case SCOOP -> "scoop";
            case PROBA -> "proba";
        };
    }
}
