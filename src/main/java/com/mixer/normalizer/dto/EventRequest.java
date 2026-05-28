package com.mixer.normalizer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Входящее событие от ИИ")
public class EventRequest {

    @NotNull
    @Schema(description = "Идентификатор миксера", example = "123")
    @JsonProperty("mixer_id")
    private Integer mixerId;

    @NotNull
    @Schema(description = "Статус: begin или finish", example = "begin")
    private String status;

    @NotNull
    @Schema(description = "Время в формате yyyy-MM-dd_HH-mm-ss, у finish может быть суффикс z", example = "2026-05-25_10-30-00")
    @JsonProperty("time_stamp")
    private String timeStamp;

    @NotNull
    @Schema(description = "Путь к папке с изображениями", example = "/images/path")
    private String folder;

    // getters, setters, конструкторы
    public Integer getMixerId() { return mixerId; }
    public void setMixerId(Integer mixerId) { this.mixerId = mixerId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getTimeStamp() { return timeStamp; }
    public void setTimeStamp(String timeStamp) { this.timeStamp = timeStamp; }
    public String getFolder() { return folder; }
    public void setFolder(String folder) { this.folder = folder; }
}