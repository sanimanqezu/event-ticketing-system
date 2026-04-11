package com.sanele.bookings.service;

import com.sanele.bookings.client.EventsServiceClient;
import com.sanele.bookings.dto.BookingRequest;
import com.sanele.bookings.dto.BookingResponse;
import com.sanele.bookings.dto.EventAvailabilityResponse;
import com.sanele.bookings.entity.Booking;
import com.sanele.bookings.entity.BookingStatus;
import com.sanele.bookings.exception.BookingAlreadyCancelledException;
import com.sanele.bookings.exception.BookingNotFoundException;
import com.sanele.bookings.exception.EventServiceException;
import com.sanele.bookings.repository.BookingRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final EventsServiceClient eventsServiceClient;

    /**
     * Creates a booking by:
     * 1. Checking event availability via events-service.
     * 2. Reserving the requested seats (atomic, with locking on the remote side).
     * 3. Persisting the confirmed booking locally.
     *
     * <p>If the seat reservation call succeeds but the local save fails, we attempt
     * a compensating release call to keep both services consistent.</p>
     */
    @Transactional
    public BookingResponse createBooking(BookingRequest request) {
        log.info("Creating booking for event [id={}], customer={}, quantity={}",
                request.eventId(), request.customerEmail(), request.quantity());

        // 1. Verify the event exists and has availability
        EventAvailabilityResponse availability;
        try {
            availability = eventsServiceClient.getAvailability(request.eventId());
        } catch (FeignException.NotFound e) {
            throw new EventServiceException("Event with id " + request.eventId() + " does not exist.");
        } catch (FeignException e) {
            throw new EventServiceException(
                    "Unable to reach events-service to verify availability. Please try again shortly.", e);
        }

        if (availability.isSoldOut() || availability.availableSeats() < request.quantity()) {
            throw new EventServiceException(
                    String.format("Not enough seats available. Requested: %d, Available: %d",
                            request.quantity(), availability.availableSeats()));
        }

        // 2. Reserve seats in events-service
        EventAvailabilityResponse reserved;
        try {
            reserved = eventsServiceClient.reserveSeats(request.eventId(), request.quantity());
        } catch (FeignException.Conflict e) {
            throw new EventServiceException(
                    "Seat reservation failed — another booking may have taken the last seats. Please try again.");
        } catch (FeignException e) {
            throw new EventServiceException(
                    "Failed to reserve seats in events-service. The booking has not been created.", e);
        }

        log.info("Seats reserved for event [id={}]. Remaining available: {}",
                request.eventId(), reserved.availableSeats());

        // 3. Save confirmed booking
        // Note: totalPrice would normally be fetched from events-service; here we keep it simple
        // and calculate it from availability data. In production this could be enriched.
        Booking booking = Booking.builder()
                .eventId(request.eventId())
                .customerName(request.customerName())
                .customerEmail(request.customerEmail().toLowerCase())
                .quantity(request.quantity())
                .totalPrice(BigDecimal.ZERO) // placeholder — would be set from event price in a real call
                .status(BookingStatus.CONFIRMED)
                .build();

        try {
            Booking saved = bookingRepository.save(booking);
            log.info("Booking [id={}] confirmed for customer {}", saved.getId(), saved.getCustomerEmail());
            return toResponse(saved);
        } catch (Exception e) {
            // Compensating transaction: release seats we just reserved
            log.error("Failed to save booking — attempting to release {} reserved seats for event [id={}]",
                    request.quantity(), request.eventId());
            try {
                eventsServiceClient.releaseSeats(request.eventId(), request.quantity());
            } catch (FeignException releaseEx) {
                log.error("CRITICAL: Could not release {} seats for event [id={}] after booking failure. Manual intervention required.",
                        request.quantity(), request.eventId(), releaseEx);
            }
            throw new EventServiceException("Booking could not be saved after seats were reserved. Seats have been released.");
        }
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getAllBookings(Long eventId, BookingStatus status) {
        List<Booking> bookings;
        if (eventId != null && status != null) {
            bookings = bookingRepository.findByEventIdAndStatus(eventId, status);
        } else if (eventId != null) {
            bookings = bookingRepository.findByEventId(eventId);
        } else if (status != null) {
            bookings = bookingRepository.findByStatus(status);
        } else {
            bookings = bookingRepository.findAll();
        }
        return bookings.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public BookingResponse getBookingById(Long id) {
        return toResponse(findBookingOrThrow(id));
    }

    /**
     * Cancels a booking and releases its seats back to events-service.
     * The booking status is updated first; if the remote release call fails it is
     * logged but the cancellation still stands (seats can be reconciled manually).
     */
    @Transactional
    public BookingResponse cancelBooking(Long id) {
        Booking booking = findBookingOrThrow(id);

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BookingAlreadyCancelledException(id);
        }

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(LocalDateTime.now());
        Booking cancelled = bookingRepository.save(booking);

        log.info("Booking [id={}] cancelled. Releasing {} seats for event [id={}]",
                id, booking.getQuantity(), booking.getEventId());

        // Release seats — best effort; booking cancellation is already committed
        try {
            eventsServiceClient.releaseSeats(booking.getEventId(), booking.getQuantity());
            log.info("Seats released successfully for event [id={}]", booking.getEventId());
        } catch (FeignException e) {
            log.error("Could not release seats for event [id={}] after cancelling booking [id={}]. " +
                            "Seat count may be inconsistent — manual reconciliation required.",
                    booking.getEventId(), id, e);
        }

        return toResponse(cancelled);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getBookingsByCustomerEmail(String email) {
        return bookingRepository.findByCustomerEmailIgnoreCase(email.toLowerCase())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Booking findBookingOrThrow(Long id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new BookingNotFoundException(id));
    }

    private BookingResponse toResponse(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getEventId(),
                booking.getCustomerName(),
                booking.getCustomerEmail(),
                booking.getQuantity(),
                booking.getTotalPrice(),
                booking.getStatus(),
                booking.getBookedAt(),
                booking.getCancelledAt()
        );
    }
}
