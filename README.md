# Event Ticketing System

A production-grade microservices application demonstrating synchronous inter-service communication, concurrent seat reservation with database-level locking, and fault-tolerant design patterns.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        Client (HTTP)                        │
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
         ┌──────────────────┐    ┌──────────────────────┐
         │ postgres-events  │    │  postgres-bookings   │
         │  (port 5433)     │    │    (port 5434)       │
         │  events_db       │    │    bookings_db       │
         └──────────────────┘    └──────────────────────┘
```

### Services

| Service | Port | Database | Responsibility |
|---------|------|----------|----------------|
| `events-service` | 8081 | `events_db` | Manages events and seat inventory |
| `bookings-service` | 8082 | `bookings_db` | Manages ticket bookings |

Each service has its own PostgreSQL database — a core microservices principle. There are no cross-database joins; services communicate exclusively over HTTP.

---

## Inter-Service Communication

`bookings-service` calls `events-service` via **Spring Cloud OpenFeign**, a declarative HTTP client that turns REST calls into annotated Java interfaces.

```java
@FeignClient(name = "events-service", url = "${events.service.url}")
public interface EventsServiceClient {
    @GetMapping("/api/events/{id}/availability")
    EventAvailabilityResponse getAvailability(@PathVariable Long id);

    @PostMapping("/api/events/{id}/reserve")
    EventAvailabilityResponse reserveSeats(@PathVariable Long id, @RequestParam int quantity);

    @PostMapping("/api/events/{id}/release")
    EventAvailabilityResponse releaseSeats(@PathVariable Long id, @RequestParam int quantity);
}
```

When a booking is created, the service:
1. Calls `GET /api/events/{id}/availability` to verify seats exist.
2. Calls `POST /api/events/{id}/reserve?quantity=N` to atomically hold the seats.
3. Persists the booking record.
4. If step 3 fails, issues a compensating `POST /api/events/{id}/release` call to avoid phantom seat loss.

---

## Concurrency and Seat Reservation

Double-booking under concurrent load is prevented through two complementary strategies:

### 1. Pessimistic Write Lock (primary guard)

The `SeatInventory` row is locked for the duration of the reservation transaction using JPA `@Lock(LockModeType.PESSIMISTIC_WRITE)`:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT s FROM SeatInventory s WHERE s.event.id = :eventId")
Optional<SeatInventory> findByEventIdWithLock(@Param("eventId") Long eventId);
```

This issues a `SELECT ... FOR UPDATE` in PostgreSQL, serialising concurrent reservation requests for the same event.

### 2. Optimistic Locking (secondary guard)

The `Event` entity carries a `@Version Long version` field. If two transactions somehow slip past the pessimistic lock and both attempt to flush a stale entity, JPA throws an `OptimisticLockException`, guaranteeing no silent data corruption.

### Cancellation Compensation

Because seat release happens after the booking is committed to the database, release is "best effort" — the cancellation persists even if the Feign call to `events-service` fails. This is logged as a critical warning so an operator can manually reconcile if needed.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 17 |
| Framework | Spring Boot 3.2.5 |
| ORM | Spring Data JPA / Hibernate |
| Database | PostgreSQL 15 |
| Migrations | Flyway |
| Service Client | Spring Cloud OpenFeign 4.1.0 (SC 2023.0.0) |
| Resilience | Resilience4j (circuit breaker + retry) |
| API Docs | SpringDoc OpenAPI 2.3.0 (Swagger UI) |
| Testing | JUnit 5, Mockito, AssertJ |
| Test DB | H2 (in-memory, PostgreSQL-compatible mode) |
| Containers | Docker + Docker Compose |
| Build | Maven |

---

## Running the Application

### Prerequisites

- Docker Desktop (or Docker Engine + Compose plugin)
- Port availability: 8081, 8082, 5433, 5434

### Start everything with Docker Compose

```bash
cd event-ticketing-system
docker compose up --build
```

Docker Compose will:
1. Start both PostgreSQL databases and wait until they are healthy.
2. Build and start `events-service` (waits for `postgres-events`).
3. Build and start `bookings-service` (waits for `postgres-bookings` AND `events-service`).

Flyway migrations run automatically on startup — no manual SQL required.

### Stop and remove containers

```bash
docker compose down          # stop containers
docker compose down -v       # stop containers AND delete volumes (wipes data)
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

---

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

## Running Tests Locally

Requires Java 17 and Maven installed.

```bash
# events-service tests
cd events-service
mvn test

# bookings-service tests
cd ../bookings-service
mvn test
```

Tests use an H2 in-memory database and Mockito mocks for the Feign client — no external services or databases required.

---

## Project Structure

```
event-ticketing-system/
├── docker-compose.yml
├── README.md
├── events-service/
│   ├── Dockerfile
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
│       │   │   └── exception/{EventNotFoundException, InsufficientSeatsException, ...}.java
│       │   └── resources/
│       │       ├── application.yml
│       │       └── db/migration/V1__create_tables.sql
│       └── test/
│           ├── java/com/sanele/events/service/EventServiceTest.java
│           └── resources/application.yml
└── bookings-service/
    ├── Dockerfile
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
        │   │   └── exception/{BookingNotFoundException, BookingAlreadyCancelledException, ...}.java
        │   └── resources/
        │       ├── application.yml
        │       └── db/migration/V1__create_bookings.sql
        └── test/
            ├── java/com/sanele/bookings/service/BookingServiceTest.java
            └── resources/application.yml
```
