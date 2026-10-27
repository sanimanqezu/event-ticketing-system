package com.sanele.events.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanele.events.dto.EventAvailabilityResponse;
import com.sanele.events.dto.EventRequest;
import com.sanele.events.dto.EventResponse;
import com.sanele.events.entity.EventCategory;
import com.sanele.events.entity.EventStatus;
import com.sanele.events.exception.EventCancelledException;
import com.sanele.events.exception.EventNotFoundException;
import com.sanele.events.exception.InsufficientSeatsException;
import com.sanele.events.service.EventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventController.class)
@DisplayName("EventController Slice Tests")
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EventService eventService;

    private EventResponse sampleResponse;
    private EventRequest sampleRequest;

    @BeforeEach
    void setUp() {
        sampleRequest = new EventRequest(
                "Rock Concert 2026",
                "Epic rock night",
                "FNB Stadium, Johannesburg",
                LocalDateTime.now().plusMonths(2),
                EventCategory.CONCERT,
                500,
                new BigDecimal("350.00"));

        sampleResponse = new EventResponse(
                1L,
                "Rock Concert 2026",
                "Epic rock night",
                "FNB Stadium, Johannesburg",
                LocalDateTime.now().plusMonths(2),
                EventCategory.CONCERT,
                500,
                500,
                new BigDecimal("350.00"),
                EventStatus.UPCOMING,
                LocalDateTime.now());
    }

    @Test
    @DisplayName("POST /api/events — should return 201 and delegate to the service")
    void createEvent_shouldReturn201() throws Exception {
        when(eventService.createEvent(any(EventRequest.class))).thenReturn(sampleResponse);

        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Rock Concert 2026"));

        verify(eventService).createEvent(any(EventRequest.class));
    }

    @Test
    @DisplayName("POST /api/events — should return 400 with validation errors for an invalid body")
    void createEvent_shouldReturn400_whenBodyInvalid() throws Exception {
        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.validationErrors").isArray())
                .andExpect(jsonPath("$.validationErrors").isNotEmpty());
    }

    @Test
    @DisplayName("GET /api/events/{id} — should return 200 with the event")
    void getEventById_shouldReturn200() throws Exception {
        when(eventService.getEventById(1L)).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/events/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("UPCOMING"));
    }

    @Test
    @DisplayName("GET /api/events/{id} — should return 404 when the event is not found")
    void getEventById_shouldReturn404_whenNotFound() throws Exception {
        when(eventService.getEventById(99L)).thenThrow(new EventNotFoundException(99L));

        mockMvc.perform(get("/api/events/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Event not found with id: 99"));
    }

    @Test
    @DisplayName("GET /api/events — should return 200 with the list of events")
    void getAllEvents_shouldReturn200() throws Exception {
        when(eventService.getAllEvents(null, null)).thenReturn(List.of(sampleResponse));

        mockMvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    @DisplayName("POST /api/events/{id}/reserve — should return 200 with updated availability")
    void reserveSeats_shouldReturn200() throws Exception {
        when(eventService.reserveSeats(1L, 2))
                .thenReturn(new EventAvailabilityResponse(1L, 498, 500, false));

        mockMvc.perform(post("/api/events/1/reserve").param("quantity", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(1))
                .andExpect(jsonPath("$.availableSeats").value(498));

        verify(eventService).reserveSeats(1L, 2);
    }

    @Test
    @DisplayName("POST /api/events/{id}/reserve — should return 409 when seats are insufficient")
    void reserveSeats_shouldReturn409_whenInsufficient() throws Exception {
        when(eventService.reserveSeats(eq(1L), anyInt()))
                .thenThrow(new InsufficientSeatsException(10, 3));

        mockMvc.perform(post("/api/events/1/reserve").param("quantity", "10"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    @DisplayName("POST /api/events/{id}/reserve — should return 422 when the event is cancelled")
    void reserveSeats_shouldReturn422_whenCancelled() throws Exception {
        when(eventService.reserveSeats(eq(1L), anyInt()))
                .thenThrow(new EventCancelledException(1L));

        mockMvc.perform(post("/api/events/1/reserve").param("quantity", "2"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.error").value("Unprocessable Entity"));
    }

    @Test
    @DisplayName("POST /api/events/{id}/reserve — should return 400 for a non-positive quantity")
    void reserveSeats_shouldReturn400_whenQuantityNotPositive() throws Exception {
        mockMvc.perform(post("/api/events/1/reserve").param("quantity", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors").isNotEmpty());
    }

    @Test
    @DisplayName("DELETE /api/events/{id} — should return 204")
    void deleteEvent_shouldReturn204() throws Exception {
        mockMvc.perform(delete("/api/events/1"))
                .andExpect(status().isNoContent());

        verify(eventService).deleteEvent(1L);
    }
}
