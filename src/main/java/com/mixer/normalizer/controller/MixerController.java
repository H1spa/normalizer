package com.mixer.normalizer.controller;

import com.mixer.normalizer.dto.EquipmentRequest;
import com.mixer.normalizer.dto.EventRequest;
import com.mixer.normalizer.service.EventNormalizer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class MixerController {

    private final EventNormalizer normalizer;

    public MixerController(EventNormalizer normalizer) {
        this.normalizer = normalizer;
    }

    @PostMapping("/scoop")
    @Operation(summary = "Принять событие scoop")
    @ApiResponse(responseCode = "202", description = "Событие принято")
    @ApiResponse(responseCode = "400", description = "Ошибка валидации")
    @ApiResponse(responseCode = "409", description = "Нарушение бизнес-правил")
    public ResponseEntity<Void> handleScoop(@Valid @RequestBody EventRequest request) {
        normalizer.handleEvent(request, EventNormalizer.OpType.SCOOP, "/scoop");
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/table")
    public ResponseEntity<Void> handleTable(@Valid @RequestBody EventRequest request) {
        normalizer.handleEvent(request, EventNormalizer.OpType.INGOTS, "/table");
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/shovel_mixer")
    public ResponseEntity<Void> handleShovelMixer(@Valid @RequestBody EventRequest request) {
        normalizer.handleEvent(request, EventNormalizer.OpType.FLUX, "/shovel_mixer");
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/shovel_slag")
    public ResponseEntity<Void> handleShovelSlag(@Valid @RequestBody EventRequest request) {
        normalizer.handleEvent(request, EventNormalizer.OpType.DISLAY, "/shovel_slag");
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/sampling")
    public ResponseEntity<Void> handleSampling(@Valid @RequestBody EventRequest request) {
        normalizer.handleEvent(request, EventNormalizer.OpType.PROBA, "/sampling");
        return ResponseEntity.accepted().build();
    }

    @PutMapping("/equipment/{mixer_id}")
    public ResponseEntity<Void> updateEquipment(@PathVariable("mixer_id") int mixerId, @Valid @RequestBody EquipmentRequest eq) {
        boolean gateOpen = "OPEN".equalsIgnoreCase(eq.getGate());
        boolean tilt = Boolean.TRUE.equals(eq.getTilt());
        normalizer.updateEquipment(mixerId, gateOpen, tilt);
        return ResponseEntity.ok().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleBadRequest(IllegalArgumentException e) {
        return e.getMessage();
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleConflict(IllegalStateException e) {
        return e.getMessage();
    }
}