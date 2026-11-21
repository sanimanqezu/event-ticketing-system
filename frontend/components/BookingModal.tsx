"use client";

import { useState } from "react";
import { api, ApiError } from "@/lib/api";
import { BookingResponse, EventResponse } from "@/lib/types";
import { formatDate, formatTime, zar } from "@/lib/format";

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function BookingModal({
  event,
  onClose,
  onBooked,
}: {
  event: EventResponse;
  onClose: () => void;
  onBooked: (b: BookingResponse) => void;
}) {
  const max = Math.min(20, event.availableSeats);
  const [qty, setQty] = useState(1);
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<BookingResponse | null>(null);

  const total = qty * event.pricePerSeat;

  async function submit() {
    setError(null);
    if (!name.trim()) return setError("Please enter the name for the booking.");
    if (!EMAIL_RE.test(email.trim())) return setError("Please enter a valid email address.");
    setSubmitting(true);
    try {
      const booking = await api.createBooking({
        eventId: event.id,
        customerName: name.trim(),
        customerEmail: email.trim(),
        quantity: qty,
      });
      setResult(booking);
      onBooked(booking);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not complete the booking. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="overlay" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <div className="panel-head">
          <h3>{result ? "You're in" : "Book tickets"}</h3>
          <button className="x" onClick={onClose} aria-label="Close">
            &times;
          </button>
        </div>

        <div className="panel-body">
          {result ? (
            <div className="confirm">
              <div className="tick" aria-hidden>
                &#10003;
              </div>
              <h3>Booking confirmed</h3>
              <p style={{ color: "var(--ink-soft)", margin: "0 0 16px" }}>
                {result.quantity} {result.quantity === 1 ? "ticket" : "tickets"} to {event.name}.
              </p>
              <div className="summary" style={{ textAlign: "left" }}>
                <div className="row">
                  <span>Booking reference</span>
                  <b style={{ color: "var(--ink)" }}>#{result.id}</b>
                </div>
                <div className="row">
                  <span>When</span>
                  <span>
                    {formatDate(event.eventDate)} {formatTime(event.eventDate)}
                  </span>
                </div>
                <div className="row">
                  <span>Where</span>
                  <span>{event.venue}</span>
                </div>
                <div className="row total">
                  <span>Paid</span>
                  <span>{zar(result.totalPrice)}</span>
                </div>
              </div>
              <p style={{ fontSize: 12.5, color: "var(--muted)", margin: "0 0 16px" }}>
                Look it up any time under "My tickets" using {result.customerEmail}.
              </p>
              <button className="btn btn-ink btn-block" onClick={onClose}>
                Done
              </button>
            </div>
          ) : (
            <>
              <div className="summary">
                <div className="row">
                  <span>{event.name}</span>
                </div>
                <div className="row">
                  <span>
                    {formatDate(event.eventDate)} {formatTime(event.eventDate)}
                  </span>
                </div>
                <div className="row">
                  <span>{event.venue}</span>
                  <span>{zar(event.pricePerSeat)} each</span>
                </div>
              </div>

              {error && <div className="alert">{error}</div>}

              <div className="field">
                <label>Tickets ({event.availableSeats} available)</label>
                <div className="stepper">
                  <button onClick={() => setQty((q) => Math.max(1, q - 1))} disabled={qty <= 1} aria-label="Fewer">
                    &minus;
                  </button>
                  <div className="val">{qty}</div>
                  <button onClick={() => setQty((q) => Math.min(max, q + 1))} disabled={qty >= max} aria-label="More">
                    +
                  </button>
                </div>
                {max < 20 && <span style={{ marginLeft: 12, fontSize: 12.5, color: "var(--muted)" }}>Max {max}</span>}
              </div>

              <div className="field">
                <label htmlFor="bk-name">Full name</label>
                <input id="bk-name" value={name} onChange={(e) => setName(e.target.value)} placeholder="Jane Dube" />
              </div>
              <div className="field">
                <label htmlFor="bk-email">Email</label>
                <input
                  id="bk-email"
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="jane@example.com"
                />
              </div>

              <div className="summary">
                <div className="row total">
                  <span>
                    Total ({qty} x {zar(event.pricePerSeat)})
                  </span>
                  <span>{zar(total)}</span>
                </div>
              </div>

              <button className="btn btn-accent btn-block" onClick={submit} disabled={submitting}>
                {submitting ? "Reserving your seats..." : `Confirm booking - ${zar(total)}`}
              </button>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
