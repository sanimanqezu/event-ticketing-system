"use client";

import { useState } from "react";
import { api, ApiError } from "@/lib/api";
import { BookingResponse, EventResponse } from "@/lib/types";
import { formatDate, zar } from "@/lib/format";

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function MyTickets({
  onClose,
  eventsById,
  onChanged,
}: {
  onClose: () => void;
  eventsById: Map<number, EventResponse>;
  onChanged: () => void;
}) {
  const [email, setEmail] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [bookings, setBookings] = useState<BookingResponse[] | null>(null);
  const [cancelling, setCancelling] = useState<number | null>(null);

  async function search() {
    setError(null);
    if (!EMAIL_RE.test(email.trim())) return setError("Enter the email you booked with.");
    setLoading(true);
    try {
      const list = await api.bookingsByEmail(email.trim());
      list.sort((a, b) => b.id - a.id);
      setBookings(list);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not load your bookings.");
    } finally {
      setLoading(false);
    }
  }

  async function cancel(id: number) {
    setCancelling(id);
    setError(null);
    try {
      const updated = await api.cancelBooking(id);
      setBookings((prev) => (prev ? prev.map((b) => (b.id === id ? updated : b)) : prev));
      onChanged();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not cancel that booking.");
    } finally {
      setCancelling(null);
    }
  }

  return (
    <div className="overlay" onClick={onClose}>
      <div className="drawer" onClick={(e) => e.stopPropagation()}>
        <div className="panel-head">
          <h3>My tickets</h3>
          <button className="x" onClick={onClose} aria-label="Close">
            &times;
          </button>
        </div>
        <div className="panel-body">
          <div className="field">
            <label htmlFor="mt-email">Find bookings by email</label>
            <input
              id="mt-email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && search()}
              placeholder="jane@example.com"
            />
          </div>
          <button className="btn btn-ink btn-block" onClick={search} disabled={loading}>
            {loading ? "Looking..." : "Find my tickets"}
          </button>

          {error && (
            <div className="alert" style={{ marginTop: 16 }}>
              {error}
            </div>
          )}

          {bookings && (
            <div style={{ marginTop: 22 }}>
              {bookings.length === 0 ? (
                <div className="state">
                  <h3>Nothing yet</h3>
                  <p>No bookings found for that email.</p>
                </div>
              ) : (
                bookings.map((b) => {
                  const ev = eventsById.get(b.eventId);
                  const confirmed = b.status === "CONFIRMED";
                  return (
                    <div className="bk" key={b.id}>
                      <div className="top">
                        <h4>{ev ? ev.name : `Event #${b.eventId}`}</h4>
                        <span className={`badge ${confirmed ? "confirmed" : "cancelled"}`}>{b.status}</span>
                      </div>
                      <div className="meta">
                        <span>
                          Ref <b>#{b.id}</b>
                        </span>
                        <span>
                          <b>{b.quantity}</b> {b.quantity === 1 ? "ticket" : "tickets"}
                        </span>
                        <span>
                          <b>{zar(b.totalPrice)}</b>
                        </span>
                        {ev && <span>{formatDate(ev.eventDate)}</span>}
                      </div>
                      {confirmed && (
                        <button
                          className="btn btn-sm"
                          style={{ marginTop: 12 }}
                          onClick={() => cancel(b.id)}
                          disabled={cancelling === b.id}
                        >
                          {cancelling === b.id ? "Cancelling..." : "Cancel booking"}
                        </button>
                      )}
                    </div>
                  );
                })
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
