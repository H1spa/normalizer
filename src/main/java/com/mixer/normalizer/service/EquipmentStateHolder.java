package com.mixer.normalizer.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class EquipmentStateHolder {

    private final Map<Integer, EquipmentState> states = new ConcurrentHashMap<>();

    public record EquipmentState(boolean gateOpen, boolean tilt) {
        public static EquipmentState defaultState() {
            return new EquipmentState(true, false);
        }
    }

    public void update(int mixerId, boolean gateOpen, boolean tilt) {
        states.put(mixerId, new EquipmentState(gateOpen, tilt));
    }

    public EquipmentState getState(int mixerId) {
        return states.getOrDefault(mixerId, EquipmentState.defaultState());
    }
}
