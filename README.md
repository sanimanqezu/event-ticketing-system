# Event Ticketing System

[![CI](https://github.com/sanimanqezu/event-ticketing-system/actions/workflows/ci.yml/badge.svg)](https://github.com/sanimanqezu/event-ticketing-system/actions/workflows/ci.yml)

A microservices application that models the core of a ticketing platform: creating events, tracking seat inventory, and booking tickets safely under concurrent load. It demonstrates synchronous inter-service communication with a declarative HTTP client, double-booking prevention through database-level locking, and fault-tolerant design (circuit breaker, retry, and a compensating transaction when a remote call succeeds but the local write fails).

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        Client (HTTP)                         │
└────────────────────┬──────────────────────┬─────────────────┘
                     │                      │
              port 8081                port 8082
                     ▼                      ▼
          ┌──────────────────┐   ┌──────────────────────┐
          │  events-service  │◄──│   bookings-service   │
          │                  │   │                      │
          │  - Events CRUD   │   │  - Bookings CRUD     │
          │  - Seat Inventory│   │  - OpenFeign client  │
          │  - Reserve seats │   │  - Resilience4j CB   │
          │  - Release seats │   │                      │
          └────────┬─────────┘   └──────────┬───────────┘
                   │                        │
                   ▼                        ▼
          ┌───────────────────────────────────────────┐
          │        Centralized PostgreSQL (5432)       │
          │     events_db          bookings_db         │
          └───────────────────────────────────────────┘
```

### Services

| Service | Port | Database | Responsibility |
|---------|------|----------|----------------|
| `events-service` | 8081 | `events_db` | Manages events and seat inventory |
| `bookings-service` | 8082 | `bookings_db` | Manages ticket bookings |

Both services run against a single **centralized PostgreSQL instance**, but each owns a **separate logical database** (`events_db` and `bookings_db`). There are no cross-database joins — `bookings-service` stores only the remote `event_id` and resolves everything else over HTTP. Connection details are supplied entirely through environment variables (`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`), so the same image runs locally and in Docker unchanged.

---

## Key Design Points

### Two-service split

- **events-service** is the source of truth for events and seat inventory. It exposes CRUD endpoints plus three inventory operations — `availability`, `reserve`, and `release` — the last two of which are called internally by `bookings-service`.
- **bookings-service** owns the booking lifecycle. It never touches the events database directly; it calls events-service over HTTP for every availability check and seat movement, keeping the two services fully decoupled at the data layer.

### How they interact

`bookings-service` calls `events-service` via **Spring Cloud OpenFeign**, a declarative HTTP client that turns REST calls into an annotated Java interface:

```java
@FeignClient(name = "events-service", url = "${events.service.url}")
public interface EventsServiceClient {
    @GetMapping("/api/events/{id}/availability")
    EventAvailabilityResponse getAvailability(@PathVariable("id") Long id);

    @PostMapping("/api/events/{id}/reserve")
    EventAvailabilityResponse reserveSeats(@PathVariable("id") Long id, @RequestParam("quantity") int quantity);

    @PostMapping("/api/events/{id}/release")
    EventAvailabilityResponse releaseSeats(@PathVariable("id") Long id, @RequestParam("quantity") int quantity);
}
```

When a booking is created, the service:
1. Calls `GET /api/events/{id}/availability` to verify seats exist.
2. Calls `POST /api/events/{id}/reserve?quantity=N` to atomically hold the seats.
3. Persists the booking record.
4. If step 3 fails, issues a compensating `POST /api/events/{id}/release` call so the held seats are not lost.

The Feign client is wrapped with **Resilience4j** — a circuit breaker and retry (configured in `bookings-service`'s `application.yml`) guard against a slow or unavailable events-service.

### Concurrency and seat reservation

Double-booking under concurrent load is prevented through two complementary strategies inside events-service:

**1. Pessimistic write lock (primary guard).** The `SeatInventory` row is locked for the duration of the reservation transaction:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT s FROM SeatInventory s WHERE s.event.id = :eventId")
Optional<SeatInventory> findByEventIdWithLock(@Param("eventId") Long eventId);
```

This issues a `SELECT ... FOR UPDATE` in PostgreSQL, serialising concurrent reservations for the same event.

**2. Optimistic locking (secondary guard).** Both `Event` and `SeatInventory` carry a `@Version` field, so a stale write surfaces as an `OptimisticLockException` rather than silent corruption. A database-level `CHECK (reserved_seats + available_seats = total_seats)` constraint backs this up.

### Cancellation compensation

On cancellation the booking is marked `CANCELLED` and committed first; the seat `release` call to events-service is then made on a **best-effort** basis. If that remote call fails, the cancellation still stands and the failure is logged as a critical warning for manual reconciliation.

### Consistent error handling

Each service has a `@RestControllerAdvice` that maps domain exceptions to HTTP status codes with a structured `ErrorResponse` body (status, error, message, path, timestamp, and validation errors) — e.g. not-found → `404`, insufficient seats → `409`, cancelled event → `422`, bean-validation failures → `400`, and (in bookings-service) events-service failures → `502`.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 25 |
| Framework | Spring Boot 3.5.16 |
| ORM | Spring Data JPA / Hibernate |
| Database | PostgreSQL (centralized instance, one database per service) |
| Migrations | Flyway (`ddl-auto: validate`) |
| Service Client | Spring Cloud OpenFeign (Spring Cloud 2025.0.0) |
| Resilience | Resilience4j (circuit breaker + retry) |
| API Docs | SpringDoc OpenAPI 2.3.0 (Swagger UI) |
| Testing | JUnit 5, Mockito, AssertJ |
| Test DB | H2 (in-memory, PostgreSQL-compatible mode) |
| Containers | Docker + Docker Compose |
| Build | Maven |

---

## Running Locally

### Prerequisites

- JDK 25
- Maven
- A running PostgreSQL instance (the centralized Postgres) reachable on `localhost:5432`

### 1. Start the centralized PostgreSQL and create both databases

The services expect **two logical databases on one Postgres instance**: `events_db` and `bookings_db`. Create them once:

```sql
CREATE DATABASE events_db;
CREATE DATABASE bookings_db;
```

Flyway then creates and migrates the schema inside each database automatically on first startup — no manual DDL required.

Each module ships an `.env.example` documenting the variables it reads:

```
DB_HOST=localhost
DB_PORT=5432
DB_NAME=events_db        # bookings_db for the bookings-service
DB_USERNAME=postgres
DB_PASSWORD=postgres
```

### 2. Start the services

```bash
# events-service (start this first — bookings-service depends on it)
cd events-service
mvn spring-boot:run

# bookings-service (in a second terminal)
cd bookings-service
mvn spring-boot:run
```

`events-service` comes up on `http://localhost:8081` and `bookings-service` on `http://localhost:8082`. `bookings-service` reaches events-service via `EVENTS_SERVICE_URL` (default `http://localhost:8081`).

### Running with Docker Compose

The repo-root `docker-compose.yml` builds and runs the two services only; it attaches them to the shared external network `portfolio-net` and expects the centralized Postgres to be reachable at host `postgres`. Start that network and database first (from the portfolio root), then:

```bash
docker compose up --build
```

---

## API Reference

### events-service (http://localhost:8081)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/events` | Create a new event |
| GET | `/api/events` | List events (filter: `?category=CONCERT&status=UPCOMING`) |
| GET | `/api/events/{id}` | Get event by ID |
| PUT | `/api/events/{id}` | Update event |
| DELETE | `/api/events/{id}` | Delete event |
| GET | `/api/events/{id}/availability` | Get seat availability |
| POST | `/api/events/{id}/reserve?quantity=N` | Reserve N seats (used by bookings-service) |
| POST | `/api/events/{id}/release?quantity=N` | Release N seats (used by bookings-service) |

**Swagger UI:** http://localhost:8081/swagger-ui.html

#### Create Event — example request body

```json
{
  "name": "Amapiano Night Live",
  "description": "An electrifying Amapiano concert under the Joburg stars.",
  "venue": "FNB Stadium, Johannesburg",
  "eventDate": "2026-12-31T20:00:00",
  "category": "CONCERT",
  "totalSeats": 5000,
  "pricePerSeat": 350.00
}
```

Categories: `CONCERT`, `SPORT`, `CONFERENCE`, `THEATRE`, `OTHER`

### bookings-service (http://localhost:8082)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/bookings` | Create a booking |
| GET | `/api/bookings` | List bookings (filter: `?eventId=1&status=CONFIRMED`) |
| GET | `/api/bookings/{id}` | Get booking by ID |
| POST | `/api/bookings/{id}/cancel` | Cancel a booking |
| GET | `/api/bookings/customer?email=...` | Get all bookings for a customer |

**Swagger UI:** http://localhost:8082/swagger-ui.html

#### Create Booking — example request body

```json
{
  "eventId": 1,
  "customerName": "Sanele Dube",
  "customerEmail": "sanele@example.com",
  "quantity": 2
}
```

---

## Tests

Tests are fully self-contained: they use an H2 in-memory database (PostgreSQL-compatible mode) and a Mockito-mocked Feign client, so no external database or network is required.

Two layers per service:

- **Service-layer unit tests** (`@ExtendWith(MockitoExtension.class)`, `@Mock`, `@InjectMocks`, AssertJ) cover happy paths, not-found, validation/edge cases (sold-out transitions, insufficient seats, over-release, seat-delta updates, compensating release, best-effort cancellation).
- **Controller slice tests** (`@WebMvcTest`, `@MockitoBean`, MockMvc) cover status codes, delegation to the service, bean-validation → `400`, and `@ControllerAdvice` exception→status mapping with the error body.

| Module | Service-layer unit | Controller slice | Total |
|--------|-------------------:|-----------------:|------:|
| events-service | 15 | 10 | 25 |
| bookings-service | 14 | 10 | 24 |
| **All** | **29** | **20** | **49** |

```bash
# events-service tests
cd events-service
mvn test

# bookings-service tests
cd ../bookings-service
mvn test
```

---

## Project Structure

```
event-ticketing-system/
├── docker-compose.yml
├── README.md
├── events-service/
│   ├── Dockerfile
│   ├── .env.example
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/sanele/events/
│       │   │   ├── EventsServiceApplication.java
│       │   │   ├── controller/EventController.java
│       │   │   ├── service/EventService.java
│       │   │   ├── entity/{Event, SeatInventory, EventCategory, EventStatus}.java
│       │   │   ├── repository/{EventRepository, SeatInventoryRepository}.java
│       │   │   ├── dto/{EventRequest, EventResponse, EventAvailabilityResponse, ErrorResponse}.java
│       │   │   └── exception/{EventNotFoundException, InsufficientSeatsException, EventCancelledException, GlobalExceptionHandler}.java
│       │   └── resources/
│       │       ├── application.yml
│       │       └── db/migration/V1__create_tables.sql
│       └── test/
│           ├── java/com/sanele/events/
│           │   ├── controller/EventControllerTest.java
│           │   └── service/EventServiceTest.java
│           └── resources/application.yml
└── bookings-service/
    ├── Dockerfile
    ├── .env.example
    ├── pom.xml
    └── src/
        ├── main/
        │   ├── java/com/sanele/bookings/
        │   │   ├── BookingsServiceApplication.java
        │   │   ├── client/EventsServiceClient.java
        │   │   ├── controller/BookingController.java
        │   │   ├── service/BookingService.java
        │   │   ├── entity/{Booking, BookingStatus}.java
        │   │   ├── repository/BookingRepository.java
        │   │   ├── dto/{BookingRequest, BookingResponse, EventAvailabilityResponse, ErrorResponse}.java
        │   │   └── exception/{BookingNotFoundException, BookingAlreadyCancelledException, EventServiceException, GlobalExceptionHandler}.java
        │   └── resources/
        │       ├── application.yml
        │       └── db/migration/V1__create_bookings.sql
        └── test/
            ├── java/com/sanele/bookings/
            │   ├── controller/BookingControllerTest.java
            │   └── service/BookingServiceTest.java
            └── resources/application.yml
```
