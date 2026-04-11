package com.sanele.events.controller;

import com.sanele.events.dto.EventAvailabilityResponse;
import com.sanele.events.dto.EventRequest;
import com.sanele.events.dto.EventResponse;
import com.sanele.events.entity.EventCategory;
import com.sanele.events.entity.EventStatus;
import com.sanele.events.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Events", description = "Event management and seat inventory operations")
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@Validated
public class EventController {

    private final EventService eventService;

    @Operation(summary = "Create a new event", description = "Creates an event and automatically initialises its seat inventory.")
    @PostMapping
    public ResponseEntity<EventResponse> createEvent(@Valid @RequestBody EventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(eventService.createEvent(request));
    }

    @Operation(summary = "List all events", description = "Optionally filter by category and/or status.")
    @GetMapping
    public ResponseEntity<List<EventResponse>> getAllEvents(
            @Parameter(description = "Filter by event category")
            @RequestParam(required = false) EventCategory category,
            @Parameter(description = "Filter by event status")
            @RequestParam(required = false) EventStatus status) {
        return ResponseEntity.ok(eventService.getAllEvents(category, status));
    }

    @Operation(summary = "Get event by ID")
    @GetMapping("/{id}")
    public ResponseEntity<EventResponse> getEventById(@PathVariable Long id) {
        return ResponseEntity.ok(eventService.getEventById(id));
    }

    @Operation(summary = "Update an event")
    @PutMapping("/{id}")
    public ResponseEntity<EventResponse> updateEvent(
            @PathVariable Long id,
            @Valid @RequestBody EventRequest request) {
        return ResponseEntity.ok(eventService.updateEvent(id, request));
    }

    @Operation(summary = "Delete an event")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEvent(@PathVariable Long id) {
        eventService.deleteEvent(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Check seat availability for an event")
    @GetMapping("/{id}/availability")
    public ResponseEntity<EventAvailabilityResponse> getAvailability(@PathVariable Long id) {
        return ResponseEntity.ok(eventService.getAvailability(id));
    }

    @Operation(
            summary = "Reserve seats",
            description = "Atomically decrements available seats by the requested quantity. Uses pessimistic locking to prevent double-booking. Called internally by bookings-service."
    )
    @PostMapping("/{id}/reserve")
    public ResponseEntity<EventAvailabilityResponse> reserveSeats(
            @PathVariable Long id,
            @RequestParam @Positive(message = "Quantity must be positive") int quantity) {
        return ResponseEntity.ok(eventService.reserveSeats(id, quantity));
    }

    @Operation(
            summary = "Release seats",
            description = "Returns seats to the available pool. Called by bookings-service when a booking is cancelled."
    )
    @PostMapping("/{id}/release")
    public ResponseEntity<EventAvailabilityResponse> releaseSeats(
            @PathVariable Long id,
            @RequestParam @Positive(message = "Quantity must be positive") int quantity) {
        return ResponseEntity.ok(eventService.releaseSeats(id, quantity));
    }
}
