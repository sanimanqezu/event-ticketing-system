package com.sanele.bookings.dto;

/**
 * Mirror of the DTO returned by events-service's availability and reserve/release endpoints.
 * Kept in its own package so the bookings-service has no compile-time dependency
 * on events-service source code — communication is purely over HTTP.
 */
public record EventAvailabilityResponse(
        Long eventId,
        int availableSeats,
        int totalSeats,
        boolean isSoldOut
) {}
