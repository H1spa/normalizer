package com.mixer.normalizer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Выходное событие для целевой системы")
public class OutputEvent {

    @JsonProperty("mixer_id")
    private Integer mixerId;
    private String type;
    private String begin;
    private String finish;
    private String folder;

    public OutputEvent(Integer mixerId, String type, String begin, String finish, String folder) {
        this.mixerId = mixerId;
        this.type = type;
        this.begin = begin;
        this.finish = finish;
        this.folder = folder;
    }

    // getters
    public Integer getMixerId() { return mixerId; }
    public String getType() { return type; }
    public String getBegin() { return begin; }
    public String getFinish() { return finish; }
    public String getFolder() { return folder; }
}