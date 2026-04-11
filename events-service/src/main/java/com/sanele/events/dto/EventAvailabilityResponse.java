package com.sanele.events.dto;

public record EventAvailabilityResponse(
        Long eventId,
        int availableSeats,
        int totalSeats,
        boolean isSoldOut
) {}
