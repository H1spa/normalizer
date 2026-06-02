package com.mixer.normalizer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Ответ при создании события begin")
public class CreateEventResponse {

    @JsonProperty("event_id")
    @Schema(description = "Локальный идентификатор события", example = "8edfb5ab-4c5b-4b9e-a18b-2a0f9a6ed3d3")
    private final String eventId;

    public CreateEventResponse(String eventId) {
        this.eventId = eventId;
    }

    public String getEventId() {
        return eventId;
    }
}
