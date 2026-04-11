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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final SeatInventoryRepository seatInventoryRepository;

    @Transactional
    public EventResponse createEvent(EventRequest request) {
        Event event = Event.builder()
                .name(request.name())
                .description(request.description())
                .venue(request.venue())
                .eventDate(request.eventDate())
                .category(request.category())
                .totalSeats(request.totalSeats())
                .availableSeats(request.totalSeats())
                .pricePerSeat(request.pricePerSeat())
                .status(EventStatus.UPCOMING)
                .build();

        Event saved = eventRepository.save(event);

        // Auto-create seat inventory
        SeatInventory inventory = SeatInventory.builder()
                .event(saved)
                .totalSeats(saved.getTotalSeats())
                .reservedSeats(0)
                .availableSeats(saved.getTotalSeats())
                .build();
        seatInventoryRepository.save(inventory);

        log.info("Created event [id={}] '{}' with {} seats", saved.getId(), saved.getName(), saved.getTotalSeats());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<EventResponse> getAllEvents(EventCategory category, EventStatus status) {
        List<Event> events;
        if (category != null && status != null) {
            events = eventRepository.findByCategoryAndStatus(category, status);
        } else if (category != null) {
            events = eventRepository.findByCategory(category);
        } else if (status != null) {
            events = eventRepository.findByStatus(status);
        } else {
            events = eventRepository.findAll();
        }
        return events.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public EventResponse getEventById(Long id) {
        return toResponse(findEventOrThrow(id));
    }

    @Transactional
    public EventResponse updateEvent(Long id, EventRequest request) {
        Event event = findEventOrThrow(id);

        int seatDelta = request.totalSeats() - event.getTotalSeats();

        event.setName(request.name());
        event.setDescription(request.description());
        event.setVenue(request.venue());
        event.setEventDate(request.eventDate());
        event.setCategory(request.category());
        event.setTotalSeats(request.totalSeats());
        event.setAvailableSeats(Math.max(0, event.getAvailableSeats() + seatDelta));
        event.setPricePerSeat(request.pricePerSeat());

        // Sync seat inventory
        seatInventoryRepository.findByEventId(id).ifPresent(inv -> {
            inv.setTotalSeats(request.totalSeats());
            inv.setAvailableSeats(Math.max(0, inv.getAvailableSeats() + seatDelta));
            seatInventoryRepository.save(inv);
        });

        Event updated = eventRepository.save(event);
        log.info("Updated event [id={}]", id);
        return toResponse(updated);
    }

    @Transactional
    public void deleteEvent(Long id) {
        Event event = findEventOrThrow(id);
        seatInventoryRepository.findByEventId(id).ifPresent(seatInventoryRepository::delete);
        eventRepository.delete(event);
        log.info("Deleted event [id={}]", id);
    }

    @Transactional(readOnly = true)
    public EventAvailabilityResponse getAvailability(Long eventId) {
        Event event = findEventOrThrow(eventId);
        SeatInventory inventory = seatInventoryRepository.findByEventId(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));

        return new EventAvailabilityResponse(
                eventId,
                inventory.getAvailableSeats(),
                inventory.getTotalSeats(),
                inventory.getAvailableSeats() == 0
        );
    }

    /**
     * Reserves seats for a given event. Uses a pessimistic write lock on the
     * SeatInventory row to prevent double-booking under concurrent load.
     * The Event entity uses optimistic locking (@Version) as a second line of defence.
     */
    @Transactional
    public EventAvailabilityResponse reserveSeats(Long eventId, int quantity) {
        Event event = findEventOrThrow(eventId);

        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new EventCancelledException(eventId);
        }

        SeatInventory inventory = seatInventoryRepository.findByEventIdWithLock(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));

        if (inventory.getAvailableSeats() < quantity) {
            throw new InsufficientSeatsException(quantity, inventory.getAvailableSeats());
        }

        inventory.setAvailableSeats(inventory.getAvailableSeats() - quantity);
        inventory.setReservedSeats(inventory.getReservedSeats() + quantity);
        seatInventoryRepository.save(inventory);

        // Keep the event entity in sync
        event.setAvailableSeats(inventory.getAvailableSeats());
        if (inventory.getAvailableSeats() == 0) {
            event.setStatus(EventStatus.SOLD_OUT);
        }
        eventRepository.save(event);

        log.info("Reserved {} seats for event [id={}]. Remaining: {}", quantity, eventId, inventory.getAvailableSeats());

        return new EventAvailabilityResponse(
                eventId,
                inventory.getAvailableSeats(),
                inventory.getTotalSeats(),
                inventory.getAvailableSeats() == 0
        );
    }

    /**
     * Releases previously reserved seats back to the pool (e.g. on booking cancellation).
     */
    @Transactional
    public EventAvailabilityResponse releaseSeats(Long eventId, int quantity) {
        Event event = findEventOrThrow(eventId);

        SeatInventory inventory = seatInventoryRepository.findByEventIdWithLock(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));

        int newAvailable = inventory.getAvailableSeats() + quantity;
        int newReserved = Math.max(0, inventory.getReservedSeats() - quantity);

        if (newAvailable > inventory.getTotalSeats()) {
            throw new IllegalArgumentException(
                    "Cannot release more seats than originally reserved. Attempted release: " + quantity);
        }

        inventory.setAvailableSeats(newAvailable);
        inventory.setReservedSeats(newReserved);
        seatInventoryRepository.save(inventory);

        event.setAvailableSeats(newAvailable);
        if (event.getStatus() == EventStatus.SOLD_OUT && newAvailable > 0) {
            event.setStatus(EventStatus.UPCOMING);
        }
        eventRepository.save(event);

        log.info("Released {} seats for event [id={}]. Available: {}", quantity, eventId, newAvailable);

        return new EventAvailabilityResponse(
                eventId,
                inventory.getAvailableSeats(),
                inventory.getTotalSeats(),
                inventory.getAvailableSeats() == 0
        );
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Event findEventOrThrow(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new EventNotFoundException(id));
    }

    private EventResponse toResponse(Event event) {
        return new EventResponse(
                event.getId(),
                event.getName(),
                event.getDescription(),
                event.getVenue(),
                event.getEventDate(),
                event.getCategory(),
                event.getTotalSeats(),
                event.getAvailableSeats(),
                event.getPricePerSeat(),
                event.getStatus(),
                event.getCreatedAt()
        );
    }
}
