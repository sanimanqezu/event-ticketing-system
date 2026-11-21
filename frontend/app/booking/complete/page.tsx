"use client";

import { Suspense, useEffect, useRef, useState } from "react";
import { useSearchParams } from "next/navigation";
import Link from "next/link";
import { api, ApiError } from "@/lib/api";
import { BookingResponse, EventResponse } from "@/lib/types";
import { Navbar } from "@/components/Navbar";
import { formatDate, formatTime, zar } from "@/lib/format";

type Status = "verifying" | "done" | "error";

function Complete() {
  const params = useSearchParams();
  const reference = params.get("reference") ?? params.get("trxref");

  const [status, setStatus] = useState<Status>("verifying");
  const [booking, setBooking] = useState<BookingResponse | null>(null);
  const [event, setEvent] = useState<EventResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const ran = useRef(false);

  useEffect(() => {
    if (ran.current) return;
    ran.current = true;

    if (!reference) {
      setError("No payment reference was provided.");
      setStatus("error");
      return;
    }

    (async () => {
      try {
        const b = await api.confirmBooking(reference);
        setBooking(b);
        setStatus("done");
        try {
          setEvent(await api.getEvent(b.eventId));
        } catch {
          /* event detail is a nice-to-have on this page */
        }
      } catch (e) {
        setError(e instanceof ApiError ? e.message : "We could not confirm your booking.");
        setStatus("error");
      }
    })();
  }, [reference]);

  if (status === "verifying") {
    return (
      <div className="detail-buy" style={{ maxWidth: 460, margin: "0 auto", textAlign: "center" }}>
        <div className="skeleton" style={{ height: 60, width: 60, borderRadius: "50%", margin: "4px auto 16px" }} />
        <h3 style={{ textTransform: "uppercase" }}>Confirming your payment</h3>
        <p style={{ color: "var(--ink-soft)", marginTop: 6 }}>This takes just a moment...</p>
      </div>
    );
  }

  if (status === "error" || !booking) {
    return (
      <div className="detail-buy" style={{ maxWidth: 460, margin: "0 auto", textAlign: "center" }}>
        <h3 style={{ textTransform: "uppercase" }}>Payment not confirmed</h3>
        <p style={{ color: "var(--ink-soft)", margin: "8px 0 18px" }}>
          {error ?? "We could not confirm your booking."}
        </p>
        <p style={{ fontSize: 12.5, color: "var(--muted)", marginBottom: 18 }}>
          If you were charged, nothing is lost - your booking can be found under &quot;My tickets&quot; once it clears,
          or contact support with your payment reference.
        </p>
        <Link className="btn btn-accent btn-block" href="/">
          Back to events
        </Link>
      </div>
    );
  }

  return (
    <div className="detail-buy" style={{ maxWidth: 460, margin: "0 auto" }}>
      <div className="confirm">
        <div className="tick" aria-hidden>
          &#10003;
        </div>
        <h3>Booking confirmed</h3>
        <p style={{ color: "var(--ink-soft)", margin: "0 0 16px" }}>
          {booking.quantity} {booking.quantity === 1 ? "ticket" : "tickets"} to{" "}
          {event ? event.name : `event #${booking.eventId}`}.
        </p>
      </div>

      <div className="summary" style={{ textAlign: "left" }}>
        <div className="row">
          <span>Booking reference</span>
          <b style={{ color: "var(--ink)" }}>#{booking.id}</b>
        </div>
        {event && (
          <div className="row">
            <span>When</span>
            <span>
              {formatDate(event.eventDate)} {formatTime(event.eventDate)}
            </span>
          </div>
        )}
        {event && (
          <div className="row">
            <span>Where</span>
            <span>{event.venue}</span>
          </div>
        )}
        <div className="row">
          <span>Tickets</span>
          <span>{booking.quantity}</span>
        </div>
        <div className="row total">
          <span>Paid</span>
          <span>{zar(booking.totalPrice)}</span>
        </div>
      </div>

      <p style={{ fontSize: 12.5, color: "var(--muted)", margin: "14px 0 16px" }}>
        Look it up any time under &quot;My tickets&quot; using {booking.customerEmail}.
      </p>
      <Link className="btn btn-ink btn-block" href="/">
        Back to events
      </Link>
    </div>
  );
}

export default function BookingCompletePage() {
  return (
    <>
      <Navbar onMyTickets={() => (window.location.href = "/")} />
      <main className="wrap detail">
        <Link href="/" className="back">
          &larr; All events
        </Link>
        <Suspense
          fallback={
            <div className="detail-buy" style={{ maxWidth: 460, margin: "0 auto", textAlign: "center" }}>
              <h3 style={{ textTransform: "uppercase" }}>Loading...</h3>
            </div>
          }
        >
          <Complete />
        </Suspense>
      </main>
    </>
  );
}
