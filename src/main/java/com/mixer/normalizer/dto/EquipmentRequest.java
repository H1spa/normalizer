package com.mixer.normalizer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Состояние оборудования АСУ ТП")
public class EquipmentRequest {

    @NotNull
    @Schema(description = "Состояние шторки: OPEN или CLOSED", example = "OPEN")
    private String gate;

    @NotNull
    @Schema(description = "Наклон: true - наклонён, false - вертикально", example = "false")
    private Boolean tilt;

    // getters, setters
    public String getGate() { return gate; }
    public void setGate(String gate) { this.gate = gate; }
    public Boolean getTilt() { return tilt; }
    public void setTilt(Boolean tilt) { this.tilt = tilt; }
}