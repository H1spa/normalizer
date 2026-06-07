package com.mixer.normalizer.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class EquipmentStateHolder {

    // Concurrent reads/writes are enough here because each update replaces the whole value.
    private final Map<Integer, EquipmentState> states = new ConcurrentHashMap<>();
    private final EquipmentState defaultState;

    public EquipmentStateHolder(@Value("${equipment.default-gate-open:true}") boolean defaultGateOpen,
                                @Value("${equipment.default-tilt:false}") boolean defaultTilt) {
        this.defaultState = new EquipmentState(defaultGateOpen, defaultTilt);
    }

    public record EquipmentState(boolean gateOpen, boolean tilt) {
    }

    public void update(int mixerId, boolean gateOpen, boolean tilt) {
        states.put(mixerId, new EquipmentState(gateOpen, tilt));
    }

    public EquipmentState getState(int mixerId) {
        return states.getOrDefault(mixerId, defaultState);
    }

    public EquipmentState getDefaultState() {
        return defaultState;
    }
}
