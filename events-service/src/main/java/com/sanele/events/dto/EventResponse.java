package com.sanele.events.dto;

import com.sanele.events.entity.EventCategory;
import com.sanele.events.entity.EventStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record EventResponse(
        Long id,
        String name,
        String description,
        String venue,
        LocalDateTime eventDate,
        EventCategory category,
        int totalSeats,
        int availableSeats,
        BigDecimal pricePerSeat,
        EventStatus status,
        LocalDateTime createdAt
) {}
