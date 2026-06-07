package com.mixer.normalizer.controller;

import com.mixer.normalizer.config.NormalizerProperties;
import com.mixer.normalizer.dto.EquipmentRequest;
import com.mixer.normalizer.dto.EventRequest;
import com.mixer.normalizer.service.EventNormalizer;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class MixerController {

    private final EventNormalizer normalizer;
    private final NormalizerProperties properties;

    public MixerController(EventNormalizer normalizer, NormalizerProperties properties) {
        this.normalizer = normalizer;
        this.properties = properties;
    }

    @PostMapping("${normalizer.endpoints.scoop}")
    public ResponseEntity<Void> handleScoop(@Valid @RequestBody EventRequest request) {
        if (isBegin(request)) {
            normalizer.handleBegin(request, EventNormalizer.OpType.SCOOP);
        } else if (isFinish(request)) {
            normalizer.handleFinish(request, EventNormalizer.OpType.SCOOP);
        } else {
            throw new IllegalArgumentException("Invalid status");
        }
        return ResponseEntity.accepted().build();
    }

    @PostMapping("${normalizer.endpoints.table}")
    public ResponseEntity<Void> handleTable(@Valid @RequestBody EventRequest request) {
        if (isBegin(request)) {
            normalizer.handleBegin(request, EventNormalizer.OpType.INGOTS);
        } else if (isFinish(request)) {
            normalizer.handleFinish(request, EventNormalizer.OpType.INGOTS);
        } else {
            throw new IllegalArgumentException("Invalid status");
        }
        return ResponseEntity.accepted().build();
    }

    @PostMapping("${normalizer.endpoints.shovel-mixer}")
    public ResponseEntity<Void> handleShovelMixer(@Valid @RequestBody EventRequest request) {
        if (isBegin(request)) {
            normalizer.handleBegin(request, EventNormalizer.OpType.FLUX);
        } else if (isFinish(request)) {
            normalizer.handleFinish(request, EventNormalizer.OpType.FLUX);
        } else {
            throw new IllegalArgumentException("Invalid status");
        }
        return ResponseEntity.accepted().build();
    }

    @PostMapping("${normalizer.endpoints.shovel-slag}")
    public ResponseEntity<Void> handleShovelSlag(@Valid @RequestBody EventRequest request) {
        if (isBegin(request)) {
            normalizer.handleBegin(request, EventNormalizer.OpType.DISLAY);
        } else if (isFinish(request)) {
            normalizer.handleFinish(request, EventNormalizer.OpType.DISLAY);
        } else {
            throw new IllegalArgumentException("Invalid status");
        }
        return ResponseEntity.accepted().build();
    }

    @PostMapping("${normalizer.endpoints.sampling}")
    public ResponseEntity<Void> handleSampling(@Valid @RequestBody EventRequest request) {
        if (isBegin(request)) {
            normalizer.handleBegin(request, EventNormalizer.OpType.PROBA);
        } else if (isFinish(request)) {
            normalizer.handleFinish(request, EventNormalizer.OpType.PROBA);
        } else {
            throw new IllegalArgumentException("Invalid status");
        }
        return ResponseEntity.accepted().build();
    }

    @PutMapping("${normalizer.endpoints.equipment}")
    public ResponseEntity<Void> updateEquipment(@PathVariable("mixer_id") int mixerId, @Valid @RequestBody EquipmentRequest eq) {
        boolean gateOpen = properties.getGateOpenValue().equalsIgnoreCase(eq.getGate());
        boolean tilt = Boolean.TRUE.equals(eq.getTilt());
        normalizer.updateEquipment(mixerId, gateOpen, tilt);
        return ResponseEntity.ok().build();
    }

    private boolean isBegin(EventRequest request) {
        return properties.getBeginStatus().equalsIgnoreCase(request.getStatus());
    }

    private boolean isFinish(EventRequest request) {
        return properties.getFinishStatus().equalsIgnoreCase(request.getStatus());
    }
}
