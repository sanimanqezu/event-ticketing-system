package com.sanele.bookings.client;

import com.sanele.bookings.dto.EventAvailabilityResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Feign client for communicating with events-service.
 *
 * <p>The URL is resolved at startup from the {@code events.service.url} property,
 * which is set to {@code http://localhost:8081} locally and
 * {@code http://events-service:8081} inside Docker Compose.</p>
 */
@FeignClient(name = "events-service", url = "${events.service.url}")
public interface EventsServiceClient {

    @GetMapping("/api/events/{id}/availability")
    EventAvailabilityResponse getAvailability(@PathVariable("id") Long id);

    @PostMapping("/api/events/{id}/reserve")
    EventAvailabilityResponse reserveSeats(
            @PathVariable("id") Long id,
            @RequestParam("quantity") int quantity
    );

    @PostMapping("/api/events/{id}/release")
    EventAvailabilityResponse releaseSeats(
            @PathVariable("id") Long id,
            @RequestParam("quantity") int quantity
    );
}
