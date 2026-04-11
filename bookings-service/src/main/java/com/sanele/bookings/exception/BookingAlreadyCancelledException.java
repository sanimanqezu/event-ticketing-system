package com.sanele.bookings.exception;

public class BookingAlreadyCancelledException extends RuntimeException {

    public BookingAlreadyCancelledException(Long id) {
        super("Booking with id " + id + " is already cancelled.");
    }
}
