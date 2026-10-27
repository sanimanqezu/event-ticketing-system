package com.sanele.bookings.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanele.bookings.client.EventsServiceClient;
import com.sanele.bookings.dto.BookingRequest;
import com.sanele.bookings.dto.BookingResponse;
import com.sanele.bookings.entity.BookingStatus;
import com.sanele.bookings.exception.BookingAlreadyCancelledException;
import com.sanele.bookings.exception.BookingNotFoundException;
import com.sanele.bookings.exception.EventServiceException;
import com.sanele.bookings.service.BookingService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookingController.class)
@DisplayName("BookingController Slice Tests")
class BookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private BookingService bookingService;

    @MockitoBean
    private EventsServiceClient eventsServiceClient;

    private BookingRequest sampleRequest;
    private BookingResponse sampleResponse;

    @BeforeEach
    void setUp() {
        sampleRequest = new BookingRequest(1L, "Sanele Dube", "sanele@example.com", 2);

        sampleResponse = new BookingResponse(
                10L,
                1L,
                "Sanele Dube",
                "sanele@example.com",
                2,
                BigDecimal.ZERO,
                BookingStatus.CONFIRMED,
                LocalDateTime.now(),
                null);
    }

    @Test
    @DisplayName("POST /api/bookings — should return 201 and delegate to the service")
    void createBooking_shouldReturn201() throws Exception {
        when(bookingService.createBooking(any(BookingRequest.class))).thenReturn(sampleResponse);

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        verify(bookingService).createBooking(any(BookingRequest.class));
    }

    @Test
    @DisplayName("POST /api/bookings — should return 400 with validation errors for an invalid body")
    void createBooking_shouldReturn400_whenBodyInvalid() throws Exception {
        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.validationErrors").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/bookings — should return 502 when events-service cannot be reached")
    void createBooking_shouldReturn502_whenEventServiceFails() throws Exception {
        when(bookingService.createBooking(any(BookingRequest.class)))
                .thenThrow(new EventServiceException("events-service down"));

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.error").value("Bad Gateway"));
    }

    @Test
    @DisplayName("GET /api/bookings/{id} — should return 200 with the booking")
    void getBookingById_shouldReturn200() throws Exception {
        when(bookingService.getBookingById(10L)).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/bookings/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.customerName").value("Sanele Dube"));
    }

    @Test
    @DisplayName("GET /api/bookings/{id} — should return 404 when the booking is not found")
    void getBookingById_shouldReturn404_whenNotFound() throws Exception {
        when(bookingService.getBookingById(999L)).thenThrow(new BookingNotFoundException(999L));

        mockMvc.perform(get("/api/bookings/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Booking not found with id: 999"));
    }

    @Test
    @DisplayName("GET /api/bookings — should return 200 with the list of bookings")
    void getAllBookings_shouldReturn200() throws Exception {
        when(bookingService.getAllBookings(null, null)).thenReturn(List.of(sampleResponse));

        mockMvc.perform(get("/api/bookings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(10));
    }

    @Test
    @DisplayName("POST /api/bookings/{id}/cancel — should return 200 with the cancelled booking")
    void cancelBooking_shouldReturn200() throws Exception {
        BookingResponse cancelled = new BookingResponse(
                10L, 1L, "Sanele Dube", "sanele@example.com", 2, BigDecimal.ZERO,
                BookingStatus.CANCELLED, LocalDateTime.now(), LocalDateTime.now());
        when(bookingService.cancelBooking(10L)).thenReturn(cancelled);

        mockMvc.perform(post("/api/bookings/10/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledAt").isNotEmpty());

        verify(bookingService).cancelBooking(10L);
    }

    @Test
    @DisplayName("POST /api/bookings/{id}/cancel — should return 409 when already cancelled")
    void cancelBooking_shouldReturn409_whenAlreadyCancelled() throws Exception {
        when(bookingService.cancelBooking(10L))
                .thenThrow(new BookingAlreadyCancelledException(10L));

        mockMvc.perform(post("/api/bookings/10/cancel"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    @DisplayName("GET /api/bookings/customer — should return 200 for a valid email")
    void getBookingsByEmail_shouldReturn200() throws Exception {
        when(bookingService.getBookingsByCustomerEmail("sanele@example.com"))
                .thenReturn(List.of(sampleResponse));

        mockMvc.perform(get("/api/bookings/customer").param("email", "sanele@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].customerEmail").value("sanele@example.com"));
    }

    @Test
    @DisplayName("GET /api/bookings/customer — should return 400 for an invalid email param")
    void getBookingsByEmail_shouldReturn400_whenEmailInvalid() throws Exception {
        mockMvc.perform(get("/api/bookings/customer").param("email", "not-an-email"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors").isNotEmpty());
    }
}
