package com.sanele.bookings.dto;

import jakarta.validation.constraints.*;

public record BookingRequest(
        @NotNull(message = "Event ID is required")
        Long eventId,

        @NotBlank(message = "Customer name is required")
        @Size(max = 150, message = "Customer name must not exceed 150 characters")
        String customerName,

        @NotBlank(message = "Customer email is required")
        @Email(message = "Customer email must be a valid email address")
        @Size(max = 254, message = "Email must not exceed 254 characters")
        String customerEmail,

        @Positive(message = "Quantity must be at least 1")
        @Max(value = 20, message = "Maximum 20 tickets per booking")
        int quantity
) {}
