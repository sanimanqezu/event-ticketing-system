package com.sanele.bookings.controller;

import com.sanele.bookings.dto.BookingRequest;
import com.sanele.bookings.dto.BookingResponse;
import com.sanele.bookings.entity.BookingStatus;
import com.sanele.bookings.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Bookings", description = "Ticket booking operations")
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Validated
public class BookingController {

    private final BookingService bookingService;

    @Operation(
            summary = "Create a booking",
            description = "Checks availability in events-service, reserves seats atomically, and persists a confirmed booking."
    )
    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(@Valid @RequestBody BookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.createBooking(request));
    }

    @Operation(summary = "List all bookings", description = "Optionally filter by eventId and/or status.")
    @GetMapping
    public ResponseEntity<List<BookingResponse>> getAllBookings(
            @Parameter(description = "Filter by event ID")
            @RequestParam(required = false) Long eventId,
            @Parameter(description = "Filter by booking status")
            @RequestParam(required = false) BookingStatus status) {
        return ResponseEntity.ok(bookingService.getAllBookings(eventId, status));
    }

    @Operation(summary = "Get booking by ID")
    @GetMapping("/{id}")
    public ResponseEntity<BookingResponse> getBookingById(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.getBookingById(id));
    }

    @Operation(
            summary = "Cancel a booking",
            description = "Cancels a confirmed booking and releases the seats back to events-service."
    )
    @PostMapping("/{id}/cancel")
    public ResponseEntity<BookingResponse> cancelBooking(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.cancelBooking(id));
    }

    @Operation(summary = "Get all bookings for a customer by email")
    @GetMapping("/customer")
    public ResponseEntity<List<BookingResponse>> getBookingsByEmail(
            @Parameter(description = "Customer email address", required = true)
            @RequestParam @Email(message = "Must be a valid email") String email) {
        return ResponseEntity.ok(bookingService.getBookingsByCustomerEmail(email));
    }
}
