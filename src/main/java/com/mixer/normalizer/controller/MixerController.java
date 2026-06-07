package com.mixer.normalizer.controller;

import com.mixer.normalizer.dto.EquipmentRequest;
import com.mixer.normalizer.dto.EventRequest;
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
    public ResponseEntity<Void> handleScoop(@Valid @RequestBody EventRequest request) {
        if ("begin".equalsIgnoreCase(request.getStatus())) {
            normalizer.handleBegin(request, EventNormalizer.OpType.SCOOP);
        } else if ("finish".equalsIgnoreCase(request.getStatus())) {
            normalizer.handleFinish(request, EventNormalizer.OpType.SCOOP);
        } else {
            throw new IllegalArgumentException("Invalid status");
        }
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/table")
    public ResponseEntity<Void> handleTable(@Valid @RequestBody EventRequest request) {
        if ("begin".equalsIgnoreCase(request.getStatus())) {
            normalizer.handleBegin(request, EventNormalizer.OpType.INGOTS);
        } else if ("finish".equalsIgnoreCase(request.getStatus())) {
            normalizer.handleFinish(request, EventNormalizer.OpType.INGOTS);
        } else {
            throw new IllegalArgumentException("Invalid status");
        }
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/shovel_mixer")
    public ResponseEntity<Void> handleShovelMixer(@Valid @RequestBody EventRequest request) {
        if ("begin".equalsIgnoreCase(request.getStatus())) {
            normalizer.handleBegin(request, EventNormalizer.OpType.FLUX);
        } else if ("finish".equalsIgnoreCase(request.getStatus())) {
            normalizer.handleFinish(request, EventNormalizer.OpType.FLUX);
        } else {
            throw new IllegalArgumentException("Invalid status");
        }
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/shovel_slag")
    public ResponseEntity<Void> handleShovelSlag(@Valid @RequestBody EventRequest request) {
        if ("begin".equalsIgnoreCase(request.getStatus())) {
            normalizer.handleBegin(request, EventNormalizer.OpType.DISLAY);
        } else if ("finish".equalsIgnoreCase(request.getStatus())) {
            normalizer.handleFinish(request, EventNormalizer.OpType.DISLAY);
        } else {
            throw new IllegalArgumentException("Invalid status");
        }
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/sampling")
    public ResponseEntity<Void> handleSampling(@Valid @RequestBody EventRequest request) {
        if ("begin".equalsIgnoreCase(request.getStatus())) {
            normalizer.handleBegin(request, EventNormalizer.OpType.PROBA);
        } else if ("finish".equalsIgnoreCase(request.getStatus())) {
            normalizer.handleFinish(request, EventNormalizer.OpType.PROBA);
        } else {
            throw new IllegalArgumentException("Invalid status");
        }
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
    public String handleBad(IllegalArgumentException e) {
        return e.getMessage();
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleConflict(IllegalStateException e) {
        return e.getMessage();
    }
}