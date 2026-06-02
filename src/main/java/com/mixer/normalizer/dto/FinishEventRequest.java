package com.mixer.normalizer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "Запрос на завершение события по event_id")
public class FinishEventRequest {

    @NotBlank(message = "time_stamp is required")
    @Pattern(
            regexp = "\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}z?",
            message = "time_stamp must match yyyy-MM-dd_HH-mm-ss or yyyy-MM-dd_HH-mm-ssz"
    )
    @Schema(description = "Время завершения в формате yyyy-MM-dd_HH-mm-ss, допускается суффикс z", example = "2026-05-30_12-00-00")
    @JsonProperty("time_stamp")
    private String timeStamp;

    public String getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(String timeStamp) {
        this.timeStamp = timeStamp;
    }
}
