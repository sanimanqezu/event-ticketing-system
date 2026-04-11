package com.sanele.events.dto;

import com.sanele.events.entity.EventCategory;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record EventRequest(
        @NotBlank(message = "Event name is required")
        @Size(max = 200, message = "Event name must not exceed 200 characters")
        String name,

        String description,

        @NotBlank(message = "Venue is required")
        @Size(max = 300, message = "Venue must not exceed 300 characters")
        String venue,

        @NotNull(message = "Event date is required")
        @Future(message = "Event date must be in the future")
        LocalDateTime eventDate,

        @NotNull(message = "Category is required")
        EventCategory category,

        @Positive(message = "Total seats must be a positive number")
        int totalSeats,

        @NotNull(message = "Price per seat is required")
        @DecimalMin(value = "0.00", message = "Price per seat must be zero or greater")
        BigDecimal pricePerSeat
) {}
