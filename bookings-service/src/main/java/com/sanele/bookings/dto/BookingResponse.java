package com.sanele.bookings.dto;

import com.sanele.bookings.entity.BookingStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BookingResponse(
        Long id,
        Long eventId,
        String customerName,
        String customerEmail,
        int quantity,
        BigDecimal totalPrice,
        BookingStatus status,
        LocalDateTime bookedAt,
        LocalDateTime cancelledAt
) {}
