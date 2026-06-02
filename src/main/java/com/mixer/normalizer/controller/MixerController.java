package com.mixer.normalizer.controller;

import com.mixer.normalizer.dto.CreateEventResponse;
import com.mixer.normalizer.dto.EquipmentRequest;
import com.mixer.normalizer.dto.EventRequest;
import com.mixer.normalizer.dto.FinishEventRequest;
import com.mixer.normalizer.service.EventNormalizer;
import jakarta.validation.Valid;
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
        return handleBegin(request, EventNormalizer.OpType.SCOOP);
    }

    @PostMapping("/table")
    public ResponseEntity<CreateEventResponse> handleTable(@Valid @RequestBody EventRequest request) {
        return handleBegin(request, EventNormalizer.OpType.INGOTS);
    }

    @PostMapping("/shovel_mixer")
    public ResponseEntity<CreateEventResponse> handleShovelMixer(@Valid @RequestBody EventRequest request) {
        return handleBegin(request, EventNormalizer.OpType.FLUX);
    }

    @PostMapping("/shovel_slag")
    public ResponseEntity<CreateEventResponse> handleShovelSlag(@Valid @RequestBody EventRequest request) {
        return handleBegin(request, EventNormalizer.OpType.DISLAY);
    }

    @PostMapping("/sampling")
    public ResponseEntity<CreateEventResponse> handleSampling(@Valid @RequestBody EventRequest request) {
        return handleBegin(request, EventNormalizer.OpType.PROBA);
    }

    @PutMapping("/event/{event_id}")
    public ResponseEntity<Void> finishByEventId(
            @PathVariable("event_id") String eventId,
            @Valid @RequestBody FinishEventRequest request
    ) {
        normalizer.finishByEventId(eventId, request.getTimeStamp());
        return ResponseEntity.accepted().build();
    }

    @PutMapping("/equipment/{mixer_id}")
    public ResponseEntity<Void> updateEquipment(
            @PathVariable("mixer_id") int mixerId,
            @Valid @RequestBody EquipmentRequest request
    ) {
        boolean gateOpen = "OPEN".equalsIgnoreCase(request.getGate());
        boolean tilt = Boolean.TRUE.equals(request.getTilt());

        normalizer.updateEquipment(mixerId, gateOpen, tilt);
        return ResponseEntity.ok().build();
    }

    private ResponseEntity<CreateEventResponse> handleBegin(EventRequest request, EventNormalizer.OpType opType) {
        if (!"begin".equalsIgnoreCase(request.getStatus())) {
            throw new IllegalArgumentException("Finish must be sent to PUT /event/{event_id}");
        }

        String eventId = normalizer.handleBegin(request, opType);

        // Для событий, проигнорированных бизнес-правилами, например flux/dislay во время active ingots.
        if (eventId == null) {
            return ResponseEntity.accepted().build();
        }

        return ResponseEntity.accepted().body(new CreateEventResponse(eventId));
    }
}
