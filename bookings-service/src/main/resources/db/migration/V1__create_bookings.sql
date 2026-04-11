-- Bookings table
-- Note: eventId is a plain foreign key value — there is no database-level FK
-- to events_db because each microservice owns its own database.
-- Referential integrity between services is enforced at the application layer
-- via the Feign client call to events-service.

CREATE TABLE IF NOT EXISTS bookings (
    id              BIGSERIAL PRIMARY KEY,
    event_id        BIGINT          NOT NULL,
    customer_name   VARCHAR(150)    NOT NULL,
    customer_email  VARCHAR(254)    NOT NULL,
    quantity        INTEGER         NOT NULL CHECK (quantity > 0 AND quantity <= 20),
    total_price     NUMERIC(12, 2)  NOT NULL DEFAULT 0,
    status          VARCHAR(20)     NOT NULL DEFAULT 'CONFIRMED',
    booked_at       TIMESTAMP       NOT NULL DEFAULT NOW(),
    cancelled_at    TIMESTAMP,

    CONSTRAINT chk_status CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED'))
);

-- Indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_bookings_event_id      ON bookings(event_id);
CREATE INDEX IF NOT EXISTS idx_bookings_customer_email ON bookings(customer_email);
CREATE INDEX IF NOT EXISTS idx_bookings_status         ON bookings(status);
CREATE INDEX IF NOT EXISTS idx_bookings_event_status   ON bookings(event_id, status);
