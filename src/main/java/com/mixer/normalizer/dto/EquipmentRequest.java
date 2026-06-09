package com.mixer.normalizer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Состояние оборудования АСУ ТП")
public class EquipmentRequest {

    /*
     * Этот DTO приходит в PUT /equipment/{mixer_id}.
     * Если писать на Java не приходилось: геттеры/сеттеры ниже нужны Jackson,
     * чтобы связать JSON-поля с приватными полями объекта.
     */
    @NotNull(message = "gate is required")
    @Schema(description = "Состояние шторки: OPEN или CLOSED", example = "OPEN")
    private String gate;

    @NotNull(message = "tilt is required")
    @Schema(description = "Наклон: true - наклонён, false - вертикально", example = "false")
    private Boolean tilt;

    public String getGate() {
        return gate;
    }

    public void setGate(String gate) {
        this.gate = gate;
    }

    public Boolean getTilt() {
        return tilt;
    }

    public void setTilt(Boolean tilt) {
        this.tilt = tilt;
    }
}
