"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { api, ApiError } from "@/lib/api";
import { EventCategory, EventResponse } from "@/lib/types";
import { Navbar } from "@/components/Navbar";
import { BookingModal } from "@/components/BookingModal";
import { MyTickets } from "@/components/MyTickets";
import { formatDate, formatTime, zar } from "@/lib/format";

const CAT_LABEL: Record<EventCategory, string> = {
  CONCERT: "Concert",
  SPORT: "Sport",
  CONFERENCE: "Conference",
  THEATRE: "Theatre",
  OTHER: "Other",
};
const CAT_VAR: Record<EventCategory, string> = {
  CONCERT: "var(--cat-concert)",
  SPORT: "var(--cat-sport)",
  CONFERENCE: "var(--cat-conference)",
  THEATRE: "var(--cat-theatre)",
  OTHER: "var(--cat-other)",
};

export default function EventDetail() {
  const params = useParams<{ id: string }>();
  const id = Number(params.id);

  const [events, setEvents] = useState<EventResponse[]>([]);
  const [event, setEvent] = useState<EventResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [booking, setBooking] = useState(false);
  const [ticketsOpen, setTicketsOpen] = useState(false);

  const load = useCallback(async () => {
    if (!Number.isFinite(id)) {
      setError("That event link is not valid.");
      setLoading(false);
      return;
    }
    try {
      const list = await api.listEvents();
      setEvents(list);
      const found = list.find((e) => e.id === id) ?? (await api.getEvent(id));
      setEvent(found);
      setError(null);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not load this event.");
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  const eventsById = useMemo(() => new Map(events.map((e) => [e.id, e])), [events]);

  const cancelled = event?.status === "CANCELLED";
  const soldOut = !!event && (cancelled || event.availableSeats <= 0 || event.status === "SOLD_OUT");
  const ratio = event && event.totalSeats > 0 ? event.availableSeats / event.totalSeats : 0;
  const low = ratio <= 0.2 && !soldOut;

  return (
    <>
      <Navbar onMyTickets={() => setTicketsOpen(true)} />

      <main className="wrap detail">
        <Link href="/" className="back">
          &larr; All events
        </Link>

        {loading ? (
          <div className="skeleton detail-skel" />
        ) : error || !event ? (
          <div className="state">
            <h3>Event not found</h3>
            <p style={{ marginBottom: 18 }}>{error ?? "This event may have been removed."}</p>
            <Link className="btn btn-accent" href="/">
              Back to events
            </Link>
          </div>
        ) : (
          <article className="detail-card">
            <div className="detail-body">
              <span className="cat-tag" style={{ background: CAT_VAR[event.category] }}>
                {CAT_LABEL[event.category]}
              </span>
              <h1>{event.name}</h1>

              <div className="detail-facts">
                <div>
                  <span className="k">When</span>
                  <span className="v">
                    {formatDate(event.eventDate)}
                    {formatTime(event.eventDate) ? ` · ${formatTime(event.eventDate)}` : ""}
                  </span>
                </div>
                <div>
                  <span className="k">Where</span>
                  <span className="v">{event.venue}</span>
                </div>
              </div>

              {event.description && <p className="detail-desc">{event.description}</p>}
            </div>

            <aside className="detail-buy">
              <div className="price-lg">
                <span className="label">From</span>
                <span className="amt">{zar(event.pricePerSeat)}</span>
                <span className="per">per ticket</span>
              </div>
              <div className={`seats${low ? " low" : ""}`}>
                {soldOut
                  ? cancelled
                    ? "This event is cancelled"
                    : "Sold out"
                  : `${event.availableSeats} of ${event.totalSeats} seats left`}
                {!soldOut && (
                  <div className="bar">
                    <div className="fill" style={{ width: `${Math.max(4, ratio * 100)}%` }} />
                  </div>
                )}
              </div>
              <button className="btn btn-accent btn-block" disabled={soldOut} onClick={() => setBooking(true)}>
                {soldOut ? "Unavailable" : "Get tickets"}
              </button>
              <p className="buy-note">No account needed. Seats are held the moment you confirm.</p>
            </aside>
          </article>
        )}
      </main>

      {booking && event && <BookingModal event={event} onClose={() => setBooking(false)} />}
      {ticketsOpen && <MyTickets onClose={() => setTicketsOpen(false)} eventsById={eventsById} onChanged={load} />}
    </>
  );
}
