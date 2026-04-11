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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BookingService Unit Tests")
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private EventsServiceClient eventsServiceClient;

    @InjectMocks
    private BookingService bookingService;

    private BookingRequest sampleRequest;
    private Booking sampleBooking;
    private EventAvailabilityResponse availabilityWithSeats;
    private EventAvailabilityResponse availabilityAfterReserve;
    private EventAvailabilityResponse soldOutAvailability;

    @BeforeEach
    void setUp() {
        sampleRequest = new BookingRequest(1L, "Sanele Dube", "sanele@example.com", 2);

        sampleBooking = Booking.builder()
                .id(10L)
                .eventId(1L)
                .customerName("Sanele Dube")
                .customerEmail("sanele@example.com")
                .quantity(2)
                .totalPrice(BigDecimal.ZERO)
                .status(BookingStatus.CONFIRMED)
                .bookedAt(LocalDateTime.now())
                .build();

        availabilityWithSeats = new EventAvailabilityResponse(1L, 100, 500, false);
        availabilityAfterReserve = new EventAvailabilityResponse(1L, 98, 500, false);
        soldOutAvailability = new EventAvailabilityResponse(1L, 0, 500, true);
    }

    // -----------------------------------------------------------------------
    // Test 1: Create booking — happy path
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("createBooking — should call Feign client, reserve seats, and persist booking")
    void createBooking_shouldReserveSeatsAndSaveBooking() {
        when(eventsServiceClient.getAvailability(1L)).thenReturn(availabilityWithSeats);
        when(eventsServiceClient.reserveSeats(1L, 2)).thenReturn(availabilityAfterReserve);
        when(bookingRepository.save(any(Booking.class))).thenReturn(sampleBooking);

        BookingResponse response = bookingService.createBooking(sampleRequest);

        assertThat(response).isNotNull();
        assertThat(response.eventId()).isEqualTo(1L);
        assertThat(response.customerEmail()).isEqualTo("sanele@example.com");
        assertThat(response.quantity()).isEqualTo(2);
        assertThat(response.status()).isEqualTo(BookingStatus.CONFIRMED);

        verify(eventsServiceClient).getAvailability(1L);
        verify(eventsServiceClient).reserveSeats(1L, 2);
        verify(bookingRepository).save(any(Booking.class));
    }

    // -----------------------------------------------------------------------
    // Test 2: Create booking — sold out event
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("createBooking — should throw EventServiceException when event is sold out")
    void createBooking_shouldThrowException_whenEventSoldOut() {
        when(eventsServiceClient.getAvailability(1L)).thenReturn(soldOutAvailability);

        assertThatThrownBy(() -> bookingService.createBooking(sampleRequest))
                .isInstanceOf(EventServiceException.class)
                .hasMessageContaining("Not enough seats");

        verify(eventsServiceClient, never()).reserveSeats(anyLong(), anyInt());
        verify(bookingRepository, never()).save(any());
    }

    // -----------------------------------------------------------------------
    // Test 3: Create booking — events-service unreachable
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("createBooking — should throw EventServiceException when events-service is unavailable")
    void createBooking_shouldThrowException_whenEventsServiceUnavailable() {
        when(eventsServiceClient.getAvailability(1L))
                .thenThrow(mock(FeignException.class));

        assertThatThrownBy(() -> bookingService.createBooking(sampleRequest))
                .isInstanceOf(EventServiceException.class)
                .hasMessageContaining("Unable to reach events-service");

        verify(bookingRepository, never()).save(any());
    }

    // -----------------------------------------------------------------------
    // Test 4: Get booking by ID — found
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("getBookingById — should return booking when found")
    void getBookingById_shouldReturnBooking_whenFound() {
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(sampleBooking));

        BookingResponse response = bookingService.getBookingById(10L);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.customerName()).isEqualTo("Sanele Dube");
    }

    // -----------------------------------------------------------------------
    // Test 5: Get booking by ID — not found
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("getBookingById — should throw BookingNotFoundException when not found")
    void getBookingById_shouldThrowException_whenNotFound() {
        when(bookingRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.getBookingById(999L))
                .isInstanceOf(BookingNotFoundException.class)
                .hasMessageContaining("999");
    }

    // -----------------------------------------------------------------------
    // Test 6: Cancel booking — happy path
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("cancelBooking — should update status, set cancelledAt, and release seats")
    void cancelBooking_shouldCancelAndReleaseSeats() {
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(sampleBooking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        when(eventsServiceClient.releaseSeats(1L, 2)).thenReturn(availabilityWithSeats);

        BookingResponse response = bookingService.cancelBooking(10L);

        assertThat(response.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(response.cancelledAt()).isNotNull();
        verify(eventsServiceClient).releaseSeats(1L, 2);
    }

    // -----------------------------------------------------------------------
    // Test 7: Cancel booking — already cancelled
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("cancelBooking — should throw BookingAlreadyCancelledException for already-cancelled bookings")
    void cancelBooking_shouldThrowException_whenAlreadyCancelled() {
        sampleBooking.setStatus(BookingStatus.CANCELLED);
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(sampleBooking));

        assertThatThrownBy(() -> bookingService.cancelBooking(10L))
                .isInstanceOf(BookingAlreadyCancelledException.class)
                .hasMessageContaining("already cancelled");

        verify(eventsServiceClient, never()).releaseSeats(anyLong(), anyInt());
    }

    // -----------------------------------------------------------------------
    // Test 8: Get bookings by customer email
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("getBookingsByCustomerEmail — should return all bookings matching email (case insensitive)")
    void getBookingsByCustomerEmail_shouldReturnMatchingBookings() {
        when(bookingRepository.findByCustomerEmailIgnoreCase("sanele@example.com"))
                .thenReturn(List.of(sampleBooking));

        List<BookingResponse> results = bookingService.getBookingsByCustomerEmail("SANELE@EXAMPLE.COM");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).customerEmail()).isEqualTo("sanele@example.com");
    }
}
