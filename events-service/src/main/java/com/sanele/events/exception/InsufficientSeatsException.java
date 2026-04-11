package com.sanele.events.exception;

public class InsufficientSeatsException extends RuntimeException {

    public InsufficientSeatsException(int requested, int available) {
        super(String.format(
                "Insufficient seats available. Requested: %d, Available: %d",
                requested, available
        ));
    }
}
