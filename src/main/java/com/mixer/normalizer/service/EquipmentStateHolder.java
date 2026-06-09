package com.mixer.normalizer.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/*
 * Хранилище текущего состояния оборудования по mixerId.
 * Данные живут в памяти процесса и обновляются через HTTP или polling.
 */
@Component
public class EquipmentStateHolder {

    // Потокобезопасной map достаточно: каждое обновление целиком заменяет состояние.
    private final Map<Integer, EquipmentState> states = new ConcurrentHashMap<>();
    private final EquipmentState defaultState;

    public EquipmentStateHolder(@Value("${equipment.default-gate-open:true}") boolean defaultGateOpen,
                                @Value("${equipment.default-tilt:false}") boolean defaultTilt) {
        this.defaultState = new EquipmentState(defaultGateOpen, defaultTilt);
    }

    public record EquipmentState(boolean gateOpen, boolean tilt) {
    }

    // Запоминает актуальное состояние конкретного миксера.
    public void update(int mixerId, boolean gateOpen, boolean tilt) {
        states.put(mixerId, new EquipmentState(gateOpen, tilt));
    }

    public boolean hasState(int mixerId) {
        return states.containsKey(mixerId);
    }

    // Если по миксеру еще нет данных, возвращается состояние по умолчанию.
    public EquipmentState getState(int mixerId) {
        return states.getOrDefault(mixerId, defaultState);
    }

    public EquipmentState getDefaultState() {
        return defaultState;
    }
}
