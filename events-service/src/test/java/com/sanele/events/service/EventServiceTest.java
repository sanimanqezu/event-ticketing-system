package com.sanele.events.service;

import com.sanele.events.dto.EventAvailabilityResponse;
import com.sanele.events.dto.EventRequest;
import com.sanele.events.dto.EventResponse;
import com.sanele.events.entity.*;
import com.sanele.events.exception.EventCancelledException;
import com.sanele.events.exception.EventNotFoundException;
import com.sanele.events.exception.InsufficientSeatsException;
import com.sanele.events.repository.EventRepository;
import com.sanele.events.repository.SeatInventoryRepository;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EventService Unit Tests")
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private SeatInventoryRepository seatInventoryRepository;

    @InjectMocks
    private EventService eventService;

    private Event sampleEvent;
    private SeatInventory sampleInventory;
    private EventRequest sampleRequest;

    @BeforeEach
    void setUp() {
        sampleEvent = Event.builder()
                .id(1L)
                .name("Rock Concert 2026")
                .description("Epic rock night")
                .venue("FNB Stadium, Johannesburg")
                .eventDate(LocalDateTime.now().plusMonths(2))
                .category(EventCategory.CONCERT)
                .totalSeats(500)
                .availableSeats(500)
                .pricePerSeat(new BigDecimal("350.00"))
                .status(EventStatus.UPCOMING)
                .createdAt(LocalDateTime.now())
                .build();

        sampleInventory = SeatInventory.builder()
                .id(1L)
                .event(sampleEvent)
                .totalSeats(500)
                .reservedSeats(0)
                .availableSeats(500)
                .build();

        sampleRequest = new EventRequest(
                "Rock Concert 2026",
                "Epic rock night",
                "FNB Stadium, Johannesburg",
                LocalDateTime.now().plusMonths(2),
                EventCategory.CONCERT,
                500,
                new BigDecimal("350.00")
        );
    }

    // -----------------------------------------------------------------------
    // Test 1: Create event
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("createEvent — should create event and seat inventory")
    void createEvent_shouldCreateEventAndSeatInventory() {
        when(eventRepository.save(any(Event.class))).thenReturn(sampleEvent);
        when(seatInventoryRepository.save(any(SeatInventory.class))).thenReturn(sampleInventory);

        EventResponse response = eventService.createEvent(sampleRequest);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Rock Concert 2026");
        assertThat(response.totalSeats()).isEqualTo(500);
        assertThat(response.availableSeats()).isEqualTo(500);
        assertThat(response.status()).isEqualTo(EventStatus.UPCOMING);

        verify(eventRepository).save(any(Event.class));
        verify(seatInventoryRepository).save(any(SeatInventory.class));
    }

    // -----------------------------------------------------------------------
    // Test 2: Get event by ID — found
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("getEventById — should return event when found")
    void getEventById_shouldReturnEvent_whenFound() {
        when(eventRepository.findById(1L)).thenReturn(Optional.of(sampleEvent));

        EventResponse response = eventService.getEventById(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Rock Concert 2026");
    }

    // -----------------------------------------------------------------------
    // Test 3: Get event by ID — not found
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("getEventById — should throw EventNotFoundException when not found")
    void getEventById_shouldThrowException_whenNotFound() {
        when(eventRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.getEventById(99L))
                .isInstanceOf(EventNotFoundException.class)
                .hasMessageContaining("99");
    }

    // -----------------------------------------------------------------------
    // Test 4: Get all events — with filters
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("getAllEvents — should filter by category and status")
    void getAllEvents_shouldFilterByCategoryAndStatus() {
        when(eventRepository.findByCategoryAndStatus(EventCategory.CONCERT, EventStatus.UPCOMING))
                .thenReturn(List.of(sampleEvent));

        List<EventResponse> results = eventService.getAllEvents(EventCategory.CONCERT, EventStatus.UPCOMING);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).category()).isEqualTo(EventCategory.CONCERT);
        assertThat(results.get(0).status()).isEqualTo(EventStatus.UPCOMING);
    }

    // -----------------------------------------------------------------------
    // Test 5: Reserve seats — success
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("reserveSeats — should decrement available seats and return updated availability")
    void reserveSeats_shouldDecrementAvailableSeats() {
        when(eventRepository.findById(1L)).thenReturn(Optional.of(sampleEvent));
        when(seatInventoryRepository.findByEventIdWithLock(1L)).thenReturn(Optional.of(sampleInventory));
        when(seatInventoryRepository.save(any())).thenReturn(sampleInventory);
        when(eventRepository.save(any())).thenReturn(sampleEvent);

        EventAvailabilityResponse response = eventService.reserveSeats(1L, 10);

        assertThat(response.eventId()).isEqualTo(1L);
        assertThat(response.availableSeats()).isEqualTo(490);
        assertThat(response.totalSeats()).isEqualTo(500);
        assertThat(response.isSoldOut()).isFalse();
    }

    // -----------------------------------------------------------------------
    // Test 6: Reserve seats — insufficient seats
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("reserveSeats — should throw InsufficientSeatsException when not enough seats")
    void reserveSeats_shouldThrowException_whenInsufficientSeats() {
        sampleInventory.setAvailableSeats(3);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(sampleEvent));
        when(seatInventoryRepository.findByEventIdWithLock(1L)).thenReturn(Optional.of(sampleInventory));

        assertThatThrownBy(() -> eventService.reserveSeats(1L, 10))
                .isInstanceOf(InsufficientSeatsException.class)
                .hasMessageContaining("Requested: 10")
                .hasMessageContaining("Available: 3");
    }

    // -----------------------------------------------------------------------
    // Test 7: Reserve seats — cancelled event
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("reserveSeats — should throw EventCancelledException for cancelled events")
    void reserveSeats_shouldThrowException_whenEventIsCancelled() {
        sampleEvent.setStatus(EventStatus.CANCELLED);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(sampleEvent));

        assertThatThrownBy(() -> eventService.reserveSeats(1L, 2))
                .isInstanceOf(EventCancelledException.class)
                .hasMessageContaining("cancelled");
    }

    // -----------------------------------------------------------------------
    // Test 8: Release seats — success and SOLD_OUT -> UPCOMING transition
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("releaseSeats — should restore available seats and revert SOLD_OUT status")
    void releaseSeats_shouldRestoreSeatsAndRevertSoldOutStatus() {
        sampleEvent.setStatus(EventStatus.SOLD_OUT);
        sampleEvent.setAvailableSeats(0);
        sampleInventory.setAvailableSeats(0);
        sampleInventory.setReservedSeats(500);

        when(eventRepository.findById(1L)).thenReturn(Optional.of(sampleEvent));
        when(seatInventoryRepository.findByEventIdWithLock(1L)).thenReturn(Optional.of(sampleInventory));
        when(seatInventoryRepository.save(any())).thenReturn(sampleInventory);
        when(eventRepository.save(any())).thenReturn(sampleEvent);

        EventAvailabilityResponse response = eventService.releaseSeats(1L, 50);

        assertThat(response.availableSeats()).isEqualTo(50);
        assertThat(response.isSoldOut()).isFalse();
        // Verify the event status was flipped back to UPCOMING
        assertThat(sampleEvent.getStatus()).isEqualTo(EventStatus.UPCOMING);
    }

    @Test
    @DisplayName("reserveSeats — should mark event SOLD_OUT when the last seats are reserved")
    void reserveSeats_shouldMarkSoldOut_whenLastSeatsReserved() {
        sampleInventory.setAvailableSeats(10);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(sampleEvent));
        when(seatInventoryRepository.findByEventIdWithLock(1L)).thenReturn(Optional.of(sampleInventory));
        when(seatInventoryRepository.save(any())).thenReturn(sampleInventory);
        when(eventRepository.save(any())).thenReturn(sampleEvent);

        EventAvailabilityResponse response = eventService.reserveSeats(1L, 10);

        assertThat(response.availableSeats()).isZero();
        assertThat(response.isSoldOut()).isTrue();
        assertThat(sampleEvent.getStatus()).isEqualTo(EventStatus.SOLD_OUT);
    }

    @Test
    @DisplayName("reserveSeats — should throw EventNotFoundException when event does not exist")
    void reserveSeats_shouldThrowNotFound_whenEventMissing() {
        when(eventRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.reserveSeats(99L, 1))
                .isInstanceOf(EventNotFoundException.class)
                .hasMessageContaining("99");

        verify(seatInventoryRepository, never()).findByEventIdWithLock(anyLong());
    }

    @Test
    @DisplayName("releaseSeats — should throw IllegalArgumentException when releasing more than reserved")
    void releaseSeats_shouldThrowIllegalArgument_whenReleasingMoreThanTotal() {
        sampleInventory.setAvailableSeats(480);
        sampleInventory.setReservedSeats(20);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(sampleEvent));
        when(seatInventoryRepository.findByEventIdWithLock(1L)).thenReturn(Optional.of(sampleInventory));

        assertThatThrownBy(() -> eventService.releaseSeats(1L, 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot release more seats");

        verify(seatInventoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateEvent — should apply seat delta to event and inventory when capacity grows")
    void updateEvent_shouldApplySeatDelta_whenCapacityIncreases() {
        sampleEvent.setTotalSeats(500);
        sampleEvent.setAvailableSeats(400);
        sampleInventory.setTotalSeats(500);
        sampleInventory.setAvailableSeats(400);
        EventRequest grown = new EventRequest(
                "Rock Concert 2026", "Epic rock night", "FNB Stadium, Johannesburg",
                LocalDateTime.now().plusMonths(2), EventCategory.CONCERT, 600, new BigDecimal("350.00"));

        when(eventRepository.findById(1L)).thenReturn(Optional.of(sampleEvent));
        when(seatInventoryRepository.findByEventId(1L)).thenReturn(Optional.of(sampleInventory));
        when(eventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EventResponse response = eventService.updateEvent(1L, grown);

        assertThat(response.totalSeats()).isEqualTo(600);
        assertThat(response.availableSeats()).isEqualTo(500);
        assertThat(sampleInventory.getTotalSeats()).isEqualTo(600);
        assertThat(sampleInventory.getAvailableSeats()).isEqualTo(500);
        verify(seatInventoryRepository).save(sampleInventory);
    }

    @Test
    @DisplayName("deleteEvent — should delete the event and its seat inventory")
    void deleteEvent_shouldDeleteEventAndInventory() {
        when(eventRepository.findById(1L)).thenReturn(Optional.of(sampleEvent));
        when(seatInventoryRepository.findByEventId(1L)).thenReturn(Optional.of(sampleInventory));

        eventService.deleteEvent(1L);

        verify(seatInventoryRepository).delete(sampleInventory);
        verify(eventRepository).delete(sampleEvent);
    }

    @Test
    @DisplayName("getAvailability — should throw EventNotFoundException when inventory is missing")
    void getAvailability_shouldThrowNotFound_whenInventoryMissing() {
        when(eventRepository.findById(1L)).thenReturn(Optional.of(sampleEvent));
        when(seatInventoryRepository.findByEventId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.getAvailability(1L))
                .isInstanceOf(EventNotFoundException.class);
    }

    @Test
    @DisplayName("getAllEvents — should return all events when no filters are supplied")
    void getAllEvents_shouldReturnAll_whenNoFilters() {
        when(eventRepository.findAll()).thenReturn(List.of(sampleEvent));

        List<EventResponse> results = eventService.getAllEvents(null, null);

        assertThat(results).hasSize(1);
        verify(eventRepository).findAll();
        verify(eventRepository, never()).findByCategory(any());
        verify(eventRepository, never()).findByStatus(any());
    }
}
