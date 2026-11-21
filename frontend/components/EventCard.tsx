"use client";

import Link from "next/link";
import { EventCategory, EventResponse } from "@/lib/types";
import { dayParts, formatTime, zar } from "@/lib/format";

const CAT: Record<EventCategory, { bg: string; label: string }> = {
  CONCERT: { bg: "var(--cat-concert)", label: "Concert" },
  SPORT: { bg: "var(--cat-sport)", label: "Sport" },
  CONFERENCE: { bg: "var(--cat-conference)", label: "Conference" },
  THEATRE: { bg: "var(--cat-theatre)", label: "Theatre" },
  OTHER: { bg: "var(--cat-other)", label: "Other" },
};

export function EventCard({ event, onBook }: { event: EventResponse; onBook: (e: EventResponse) => void }) {
  const cat = CAT[event.category] ?? CAT.OTHER;
  const cancelled = event.status === "CANCELLED";
  const soldOut = cancelled || event.availableSeats <= 0 || event.status === "SOLD_OUT";
  const { day, mon } = dayParts(event.eventDate);
  const ratio = event.totalSeats > 0 ? event.availableSeats / event.totalSeats : 0;
  const low = ratio <= 0.2 && !soldOut;
  const time = formatTime(event.eventDate);

  return (
    <article className={`ticket${soldOut ? " sold" : ""}`}>
      {soldOut && <div className="stamp">{cancelled ? "Cancelled" : "Sold Out"}</div>}

      <div className="ticket-top">
        <div className="datebox" aria-hidden>
          <div className="d">{day}</div>
          <div className="m">{mon}</div>
        </div>
        <div className="headline">
          <span className="cat-tag" style={{ background: cat.bg }}>
            {cat.label}
          </span>
          <h3>
            <Link href={`/events/${event.id}`} className="ev-title-link">
              {event.name}
            </Link>
          </h3>
          <div className="venue">
            <span aria-hidden>&#9679;</span>
            {event.venue}
            {time ? ` · ${time}` : ""}
          </div>
        </div>
      </div>

      <div className="perf" />

      <div className="ticket-bottom">
        <div className="price">
          <div className="label">From</div>
          <div className="amt">{zar(event.pricePerSeat)}</div>
          <div className={`seats${low ? " low" : ""}`} style={{ marginTop: 6 }}>
            {soldOut ? "No seats left" : `${event.availableSeats} of ${event.totalSeats} left`}
            {!soldOut && (
              <div className="bar">
                <div className="fill" style={{ width: `${Math.max(4, ratio * 100)}%` }} />
              </div>
            )}
          </div>
        </div>
        <button className="btn btn-accent" disabled={soldOut} onClick={() => onBook(event)}>
          {soldOut ? "Unavailable" : "Get tickets"}
        </button>
      </div>
    </article>
  );
}
