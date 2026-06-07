package com.mixer.normalizer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "Входящее событие от ИИ")
public class EventRequest {

    @NotNull(message = "mixer_id is required")
    @Positive(message = "mixer_id must be positive")
    @Schema(description = "Идентификатор миксера", example = "123")
    @JsonProperty("mixer_id")
    private Integer mixerId;

    @NotBlank(message = "status is required")
    @Schema(description = "Статус. В новой схеме POST-эндпоинты принимают только begin", example = "begin")
    private String status;

    @NotBlank(message = "time_stamp is required")
    @Schema(description = "Время в формате yyyy-MM-dd_HH-mm-ss, допускается суффикс z", example = "2026-05-25_10-30-00")
    @JsonProperty("time_stamp")
    private String timeStamp;

    @NotBlank(message = "folder is required")
    @Schema(description = "Путь к папке с изображениями", example = "/images/path")
    private String folder;

    public Integer getMixerId() {
        return mixerId;
    }

    public void setMixerId(Integer mixerId) {
        this.mixerId = mixerId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(String timeStamp) {
        this.timeStamp = timeStamp;
    }

    public String getFolder() {
        return folder;
    }

    public void setFolder(String folder) {
        this.folder = folder;
    }
}
