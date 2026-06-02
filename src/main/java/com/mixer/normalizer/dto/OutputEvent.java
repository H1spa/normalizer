package com.mixer.normalizer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Выходное нормализованное событие для целевой системы")
public record OutputEvent(
        @JsonProperty("mixer_id")
        Integer mixerId,

        String type,
        String begin,
        String finish,
        String folder
) {
}
