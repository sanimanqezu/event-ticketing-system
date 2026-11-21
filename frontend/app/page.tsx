"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { api, ApiError } from "@/lib/api";
import { BookingResponse, EventCategory, EventResponse } from "@/lib/types";
import { parseLdt } from "@/lib/format";
import { Navbar } from "@/components/Navbar";
import { EventCard } from "@/components/EventCard";
import { BookingModal } from "@/components/BookingModal";
import { MyTickets } from "@/components/MyTickets";

type SortKey = "soonest" | "price-asc" | "price-desc";

const SORTS: { key: SortKey; label: string }[] = [
  { key: "soonest", label: "Soonest" },
  { key: "price-asc", label: "Price: low to high" },
  { key: "price-desc", label: "Price: high to low" },
];

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
  const [query, setQuery] = useState("");
  const [sort, setSort] = useState<SortKey>("soonest");

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
  const visible = useMemo(() => {
    let list = filter === "ALL" ? events : events.filter((e) => e.category === filter);
    const q = query.trim().toLowerCase();
    if (q) {
      list = list.filter(
        (e) => e.name.toLowerCase().includes(q) || e.venue.toLowerCase().includes(q),
      );
    }
    const sorted = [...list];
    if (sort === "price-asc") sorted.sort((a, b) => a.pricePerSeat - b.pricePerSeat);
    else if (sort === "price-desc") sorted.sort((a, b) => b.pricePerSeat - a.pricePerSeat);
    else
      sorted.sort(
        (a, b) => (parseLdt(a.eventDate)?.getTime() ?? 0) - (parseLdt(b.eventDate)?.getTime() ?? 0),
      );
    return sorted;
  }, [events, filter, query, sort]);
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
          <>
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

            <div className="toolbar">
              <input
                className="search"
                type="search"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Search events or venues"
                aria-label="Search events"
              />
              <label className="sort">
                <span>Sort</span>
                <select value={sort} onChange={(e) => setSort(e.target.value as SortKey)}>
                  {SORTS.map((s) => (
                    <option key={s.key} value={s.key}>
                      {s.label}
                    </option>
                  ))}
                </select>
              </label>
            </div>
          </>
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
            <h3>Nothing to show</h3>
            <p>
              {query.trim()
                ? "No events match your search. Try a different name, venue or category."
                : "Try a different category - new shows are added all the time."}
            </p>
            {query.trim() && (
              <button className="btn btn-sm" style={{ marginTop: 16 }} onClick={() => setQuery("")}>
                Clear search
              </button>
            )}
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
              Tickets to the concerts, sport and theatre you want to see. Pick an event, choose how many seats
              and book in seconds - no account needed. Your seats are held the moment you confirm.
            </p>
          </div>
          <div className="foot-meta">
            <span>&copy; {new Date().getFullYear()} Front Row</span>
            <span>Secure seat holds · Instant confirmation</span>
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
