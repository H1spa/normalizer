package com.mixer.normalizer.controller;

import com.mixer.normalizer.dto.*;
import com.mixer.normalizer.service.EventNormalizer;
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
    public ResponseEntity<CreateEventResponse> handleScoop(@Valid @RequestBody EventRequest request) {
        if ("begin".equalsIgnoreCase(request.getStatus())) {
            return ResponseEntity.accepted().body(normalizer.handleBegin(request, EventNormalizer.OpType.SCOOP));
        } else {
            throw new IllegalArgumentException("Use POST /event/finish for finish");
        }
    }

    @PostMapping("/table")
    public ResponseEntity<CreateEventResponse> handleTable(@Valid @RequestBody EventRequest request) {
        if ("begin".equalsIgnoreCase(request.getStatus())) {
            return ResponseEntity.accepted().body(normalizer.handleBegin(request, EventNormalizer.OpType.INGOTS));
        } else {
            throw new IllegalArgumentException("Use POST /event/finish for finish");
        }
    }

    @PostMapping("/shovel_mixer")
    public ResponseEntity<CreateEventResponse> handleShovelMixer(@Valid @RequestBody EventRequest request) {
        if ("begin".equalsIgnoreCase(request.getStatus())) {
            return ResponseEntity.accepted().body(normalizer.handleBegin(request, EventNormalizer.OpType.FLUX));
        } else {
            throw new IllegalArgumentException("Use POST /event/finish for finish");
        }
    }

    @PostMapping("/shovel_slag")
    public ResponseEntity<CreateEventResponse> handleShovelSlag(@Valid @RequestBody EventRequest request) {
        if ("begin".equalsIgnoreCase(request.getStatus())) {
            return ResponseEntity.accepted().body(normalizer.handleBegin(request, EventNormalizer.OpType.DISLAY));
        } else {
            throw new IllegalArgumentException("Use POST /event/finish for finish");
        }
    }

    @PostMapping("/sampling")
    public ResponseEntity<CreateEventResponse> handleSampling(@Valid @RequestBody EventRequest request) {
        if ("begin".equalsIgnoreCase(request.getStatus())) {
            return ResponseEntity.accepted().body(normalizer.handleBegin(request, EventNormalizer.OpType.PROBA));
        } else {
            throw new IllegalArgumentException("Use POST /event/finish for finish");
        }
    }

    @PostMapping("/event/finish")
    public ResponseEntity<Void> finishEvent(@Valid @RequestBody FinishEventRequest finishRequest) {
        normalizer.handleFinish(finishRequest);
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