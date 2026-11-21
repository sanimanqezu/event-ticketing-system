"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { api, ApiError } from "@/lib/api";
import { BookingResponse, EventCategory, EventResponse } from "@/lib/types";
import { Navbar } from "@/components/Navbar";
import { EventCard } from "@/components/EventCard";
import { BookingModal } from "@/components/BookingModal";
import { MyTickets } from "@/components/MyTickets";

const FILTERS: { key: EventCategory | "ALL"; label: string }[] = [
  { key: "ALL", label: "All" },
  { key: "CONCERT", label: "Concerts" },
  { key: "SPORT", label: "Sport" },
  { key: "CONFERENCE", label: "Conferences" },
  { key: "THEATRE", label: "Theatre" },
  { key: "OTHER", label: "Other" },
];

export default function Home() {
  const [events, setEvents] = useState<EventResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState<EventCategory | "ALL">("ALL");

  const [bookingEvent, setBookingEvent] = useState<EventResponse | null>(null);
  const [ticketsOpen, setTicketsOpen] = useState(false);
  const [toast, setToast] = useState<{ msg: string; err?: boolean } | null>(null);

  const load = useCallback(async () => {
    try {
      const list = await api.listEvents();
      setEvents(list);
      setError(null);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not load events. The API may be waking up.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    if (!toast) return;
    const t = setTimeout(() => setToast(null), 4200);
    return () => clearTimeout(t);
  }, [toast]);

  const eventsById = useMemo(() => new Map(events.map((e) => [e.id, e])), [events]);
  const visible = useMemo(
    () => (filter === "ALL" ? events : events.filter((e) => e.category === filter)),
    [events, filter],
  );
  const available = useMemo(() => events.filter((e) => e.availableSeats > 0).length, [events]);
  const presentCategories = useMemo(() => new Set(events.map((e) => e.category)), [events]);

  function onBooked(b: BookingResponse) {
    setToast({ msg: `Booked ${b.quantity} ${b.quantity === 1 ? "ticket" : "tickets"} - ref #${b.id}.` });
    load();
  }

  return (
    <>
      <Navbar onMyTickets={() => setTicketsOpen(true)} />

      <section className="hero">
        <div className="wrap">
          <span className="eyebrow">Live events, booked in seconds</span>
          <h1>
            Grab your
            <br />
            <span className="mark">front row</span> seat
          </h1>
          <p>
            Concerts, sport, theatre and more. Pick an event, choose your seats and book instantly - no account
            needed. Seats are held the moment you confirm.
          </p>
          <div className="hero-meta">
            <span>
              <b>{loading ? "-" : events.length}</b> events listed
            </span>
            <span>
              <b>{loading ? "-" : available}</b> on sale now
            </span>
            <span>Secure seat holds, instant confirmation</span>
          </div>
        </div>
      </section>

      <main className="wrap">
        <div className="sec-head">
          <h2>What&rsquo;s on</h2>
          <span className="count">{loading ? "Loading..." : `${visible.length} shown`}</span>
        </div>

        {!loading && !error && events.length > 0 && (
          <div className="chips">
            {FILTERS.filter((f) => f.key === "ALL" || presentCategories.has(f.key as EventCategory)).map((f) => (
              <button
                key={f.key}
                className="chip"
                aria-pressed={filter === f.key}
                onClick={() => setFilter(f.key)}
              >
                {f.label}
              </button>
            ))}
          </div>
        )}

        {loading ? (
          <div className="grid">
            {Array.from({ length: 6 }).map((_, i) => (
              <div className="skeleton" key={i} />
            ))}
          </div>
        ) : error ? (
          <div className="state">
            <h3>Could not load events</h3>
            <p style={{ marginBottom: 18 }}>{error}</p>
            <button
              className="btn btn-accent"
              onClick={() => {
                setLoading(true);
                load();
              }}
            >
              Try again
            </button>
          </div>
        ) : visible.length === 0 ? (
          <div className="state">
            <h3>No events here yet</h3>
            <p>Try a different category - new shows are added all the time.</p>
          </div>
        ) : (
          <div className="grid">
            {visible.map((e) => (
              <EventCard key={e.id} event={e} onBook={setBookingEvent} />
            ))}
          </div>
        )}
      </main>

      <footer className="footer">
        <div className="wrap">
          <div>
            <h3>Front Row</h3>
            <p>
              A demo ticketing storefront by Sanele Khanyile. The backend is a Spring Boot microservices system -
              an events service and a bookings service talking over OpenFeign, guarded by a Resilience4j circuit
              breaker, with pessimistic database locking to stop double-booking under load.
            </p>
          </div>
          <div className="stack">
            <div>Java 25 · Spring Boot 3</div>
            <div>OpenFeign · Resilience4j</div>
            <div>PostgreSQL · Flyway</div>
            <div>Next.js · React</div>
            <div style={{ marginTop: 10 }}>
              <a href="https://github.com/sanimanqezu/event-ticketing-system" target="_blank" rel="noreferrer">
                View the code on GitHub
              </a>
            </div>
          </div>
        </div>
      </footer>

      {bookingEvent && (
        <BookingModal event={bookingEvent} onClose={() => setBookingEvent(null)} onBooked={onBooked} />
      )}
      {ticketsOpen && (
        <MyTickets onClose={() => setTicketsOpen(false)} eventsById={eventsById} onChanged={load} />
      )}

      {toast && (
        <div className="toast-wrap">
          <div className={`toast${toast.err ? " err" : ""}`}>{toast.msg}</div>
        </div>
      )}
    </>
  );
}
