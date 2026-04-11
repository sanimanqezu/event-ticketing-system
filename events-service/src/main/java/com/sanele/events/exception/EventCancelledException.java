package com.sanele.events.exception;

public class EventCancelledException extends RuntimeException {

    public EventCancelledException(Long eventId) {
        super("Cannot reserve seats for cancelled event with id: " + eventId);
    }
}
