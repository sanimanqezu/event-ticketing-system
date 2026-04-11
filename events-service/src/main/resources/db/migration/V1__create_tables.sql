-- Events table
CREATE TABLE IF NOT EXISTS events (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(200)   NOT NULL,
    description     TEXT,
    venue           VARCHAR(300)   NOT NULL,
    event_date      TIMESTAMP      NOT NULL,
    category        VARCHAR(20)    NOT NULL,
    total_seats     INTEGER        NOT NULL CHECK (total_seats > 0),
    available_seats INTEGER        NOT NULL CHECK (available_seats >= 0),
    price_per_seat  NUMERIC(10, 2) NOT NULL,
    status          VARCHAR(20)    NOT NULL DEFAULT 'UPCOMING',
    created_at      TIMESTAMP      NOT NULL DEFAULT NOW(),
    version         BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT chk_available_seats CHECK (available_seats <= total_seats),
    CONSTRAINT chk_category CHECK (category IN ('CONCERT', 'SPORT', 'CONFERENCE', 'THEATRE', 'OTHER')),
    CONSTRAINT chk_status  CHECK (status  IN ('UPCOMING', 'ONGOING', 'CANCELLED', 'SOLD_OUT'))
);

-- Seat inventory table (one-to-one with events)
CREATE TABLE IF NOT EXISTS seat_inventory (
    id              BIGSERIAL PRIMARY KEY,
    event_id        BIGINT NOT NULL UNIQUE REFERENCES events(id) ON DELETE CASCADE,
    total_seats     INTEGER NOT NULL CHECK (total_seats > 0),
    reserved_seats  INTEGER NOT NULL DEFAULT 0 CHECK (reserved_seats >= 0),
    available_seats INTEGER NOT NULL CHECK (available_seats >= 0),
    version         BIGINT  NOT NULL DEFAULT 0,

    CONSTRAINT chk_inv_seats CHECK (reserved_seats + available_seats = total_seats)
);

-- Indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_events_category ON events(category);
CREATE INDEX IF NOT EXISTS idx_events_status   ON events(status);
CREATE INDEX IF NOT EXISTS idx_events_date     ON events(event_date);
CREATE INDEX IF NOT EXISTS idx_seat_inv_event  ON seat_inventory(event_id);
