"use client";

import { useState } from "react";
import { api, ApiError } from "@/lib/api";
import { EventResponse } from "@/lib/types";
import { formatDate, formatTime, zar } from "@/lib/format";

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function BookingModal({ event, onClose }: { event: EventResponse; onClose: () => void }) {
  const max = Math.min(20, event.availableSeats);
  const [qty, setQty] = useState(1);
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const total = qty * event.pricePerSeat;

  async function pay() {
    setError(null);
    if (!name.trim()) return setError("Please enter the name for the booking.");
    if (!EMAIL_RE.test(email.trim())) return setError("Please enter a valid email address.");
    setSubmitting(true);
    try {
      const { authorizationUrl } = await api.checkout({
        eventId: event.id,
        customerName: name.trim(),
        customerEmail: email.trim(),
        quantity: qty,
        callbackUrl: `${window.location.origin}/booking/complete`,
      });
      window.location.assign(authorizationUrl);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not start payment. Please try again.");
      setSubmitting(false);
    }
  }

  return (
    <div className="overlay" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <div className="panel-head">
          <h3>Book tickets</h3>
          <button className="x" onClick={onClose} aria-label="Close">
            &times;
          </button>
        </div>

        <div className="panel-body">
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

          <button className="btn btn-accent btn-block" onClick={pay} disabled={submitting}>
            {submitting ? "Redirecting to Paystack..." : `Pay ${zar(total)} securely`}
          </button>
          <p style={{ fontSize: 12, color: "var(--muted)", margin: "12px 0 0", textAlign: "center" }}>
            You will be redirected to Paystack to pay. Your seats are confirmed once payment succeeds.
          </p>
        </div>
      </div>
    </div>
  );
}
