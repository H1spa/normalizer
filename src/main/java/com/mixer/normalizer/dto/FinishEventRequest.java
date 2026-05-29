package com.mixer.normalizer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Запрос на завершение события")
public class FinishEventRequest {
    @JsonProperty("event_id")
    private String eventId;

    @NotNull
    @Schema(description = "Время завершения в формате yyyy-MM-dd_HH-mm-ss (опционально z)", example = "2026-05-30_12-00-00")
    @JsonProperty("time_stamp")
    private String timeStamp;

    // getters and setters
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getTimeStamp() { return timeStamp; }
    public void setTimeStamp(String timeStamp) { this.timeStamp = timeStamp; }
}