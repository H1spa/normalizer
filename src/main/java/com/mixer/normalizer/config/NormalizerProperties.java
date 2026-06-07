package com.mixer.normalizer.config;

import com.mixer.normalizer.service.EventNormalizer;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Component
@ConfigurationProperties(prefix = "normalizer")
public class NormalizerProperties {
    private String beginStatus = "begin";
    private String finishStatus = "finish";
    private String fixedZone = "+07:00";
    private String defaultInputZone = "UTC";
    private String inputTimePattern = "yyyy-MM-dd_HH-mm-ss";
    private String inputOffsetPattern = "xx";
    private String storageTimePattern = "yyyy-MM-dd_HH-mm-ss";
    private String outerTimePattern = "yyyy-MM-dd_HH-mm-ssxx";
    private String fluxType = "flux";
    private String dislayType = "dislay";
    private String ingotsType = "ingots";
    private String scoopType = "scoop";
    private String probaType = "proba";
    private String separationType = "separation";
    private String gateOpenValue = "OPEN";
    private String gateRequiredTypes = "FLUX,DISLAY,SCOOP";
    private boolean forbidBeginWhenTilted = true;
    private boolean interruptMixedOnTilt = true;
    private boolean interruptScoopOnTilt = true;
    private boolean interruptMixedOnGateClosed = true;
    private boolean interruptScoopOnGateClosed = true;
    private String tiltInterruptReason = "mixer tilted";
    private String gateClosedInterruptReason = "gate closed";

    public String getBeginStatus() {
        return beginStatus;
    }

    public void setBeginStatus(String beginStatus) {
        this.beginStatus = beginStatus;
    }

    public String getFinishStatus() {
        return finishStatus;
    }

    public void setFinishStatus(String finishStatus) {
        this.finishStatus = finishStatus;
    }

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

    public String getFluxType() {
        return fluxType;
    }

    public void setFluxType(String fluxType) {
        this.fluxType = fluxType;
    }

    public String getDislayType() {
        return dislayType;
    }

    public void setDislayType(String dislayType) {
        this.dislayType = dislayType;
    }

    public String getIngotsType() {
        return ingotsType;
    }

    public void setIngotsType(String ingotsType) {
        this.ingotsType = ingotsType;
    }

    public String getScoopType() {
        return scoopType;
    }

    public void setScoopType(String scoopType) {
        this.scoopType = scoopType;
    }

    public String getProbaType() {
        return probaType;
    }

    public void setProbaType(String probaType) {
        this.probaType = probaType;
    }

    public String getSeparationType() {
        return separationType;
    }

    public void setSeparationType(String separationType) {
        this.separationType = separationType;
    }

    public String getGateOpenValue() {
        return gateOpenValue;
    }

    public void setGateOpenValue(String gateOpenValue) {
        this.gateOpenValue = gateOpenValue;
    }

    public String getGateRequiredTypes() {
        return gateRequiredTypes;
    }

    public void setGateRequiredTypes(String gateRequiredTypes) {
        this.gateRequiredTypes = gateRequiredTypes;
    }

    public boolean isForbidBeginWhenTilted() {
        return forbidBeginWhenTilted;
    }

    public void setForbidBeginWhenTilted(boolean forbidBeginWhenTilted) {
        this.forbidBeginWhenTilted = forbidBeginWhenTilted;
    }

    public boolean isInterruptMixedOnTilt() {
        return interruptMixedOnTilt;
    }

    public void setInterruptMixedOnTilt(boolean interruptMixedOnTilt) {
        this.interruptMixedOnTilt = interruptMixedOnTilt;
    }

    public boolean isInterruptScoopOnTilt() {
        return interruptScoopOnTilt;
    }

    public void setInterruptScoopOnTilt(boolean interruptScoopOnTilt) {
        this.interruptScoopOnTilt = interruptScoopOnTilt;
    }

    public boolean isInterruptMixedOnGateClosed() {
        return interruptMixedOnGateClosed;
    }

    public void setInterruptMixedOnGateClosed(boolean interruptMixedOnGateClosed) {
        this.interruptMixedOnGateClosed = interruptMixedOnGateClosed;
    }

    public boolean isInterruptScoopOnGateClosed() {
        return interruptScoopOnGateClosed;
    }

    public void setInterruptScoopOnGateClosed(boolean interruptScoopOnGateClosed) {
        this.interruptScoopOnGateClosed = interruptScoopOnGateClosed;
    }

    public String getTiltInterruptReason() {
        return tiltInterruptReason;
    }

    public void setTiltInterruptReason(String tiltInterruptReason) {
        this.tiltInterruptReason = tiltInterruptReason;
    }

    public String getGateClosedInterruptReason() {
        return gateClosedInterruptReason;
    }

    public void setGateClosedInterruptReason(String gateClosedInterruptReason) {
        this.gateClosedInterruptReason = gateClosedInterruptReason;
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

    public Set<EventNormalizer.OpType> gateRequiredTypeSet() {
        List<String> parts = parseList(gateRequiredTypes);
        Set<EventNormalizer.OpType> result = EnumSet.noneOf(EventNormalizer.OpType.class);
        for (String part : parts) {
            result.add(EventNormalizer.OpType.valueOf(part.toUpperCase()));
        }
        return result;
    }

    public String eventTypeName(EventNormalizer.OpType type) {
        return switch (type) {
            case FLUX -> fluxType;
            case DISLAY -> dislayType;
            case INGOTS -> ingotsType;
            case SCOOP -> scoopType;
            case PROBA -> probaType;
        };
    }

    private static List<String> parseList(String value) {
        List<String> result = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return result;
        }

        for (String part : value.split(",")) {
            String item = part.trim();
            if (!item.isBlank()) {
                result.add(item);
            }
        }
        return result;
    }
}
