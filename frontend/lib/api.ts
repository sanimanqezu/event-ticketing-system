import {
  BookingCheckoutRequest,
  BookingRequest,
  BookingResponse,
  CheckoutResponse,
  EventResponse,
} from "./types";

export class ApiError extends Error {
  status: number;
  constructor(message: string, status: number) {
    super(message);
    this.status = status;
    this.name = "ApiError";
  }
}

async function parse<T>(res: Response): Promise<T> {
  if (!res.ok) {
    let message = `Something went wrong (${res.status}).`;
    try {
      const body = await res.json();
      if (body && typeof body.message === "string") message = body.message;
    } catch {
      /* non-JSON error body */
    }
    if (res.status === 502 || res.status === 503) {
      message = "The events service is waking up or busy. Please try again in a moment.";
    }
    throw new ApiError(message, res.status);
  }
  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}

const JSON_HEADERS = { "Content-Type": "application/json" };

export const api = {
  listEvents: (): Promise<EventResponse[]> =>
    fetch("/api/events", { cache: "no-store" }).then((r) => parse<EventResponse[]>(r)),

  getEvent: (id: number): Promise<EventResponse> =>
    fetch(`/api/events/${id}`, { cache: "no-store" }).then((r) => parse<EventResponse>(r)),

  createBooking: (body: BookingRequest): Promise<BookingResponse> =>
    fetch("/api/bookings", { method: "POST", headers: JSON_HEADERS, body: JSON.stringify(body) }).then((r) =>
      parse<BookingResponse>(r),
    ),

  checkout: (body: BookingCheckoutRequest): Promise<CheckoutResponse> =>
    fetch("/api/bookings/checkout", { method: "POST", headers: JSON_HEADERS, body: JSON.stringify(body) }).then((r) =>
      parse<CheckoutResponse>(r),
    ),

  confirmBooking: (reference: string): Promise<BookingResponse> =>
    fetch(`/api/bookings/confirm?reference=${encodeURIComponent(reference)}`, { method: "POST" }).then((r) =>
      parse<BookingResponse>(r),
    ),

  bookingsByEmail: (email: string): Promise<BookingResponse[]> =>
    fetch(`/api/bookings/customer?email=${encodeURIComponent(email)}`, { cache: "no-store" }).then((r) =>
      parse<BookingResponse[]>(r),
    ),

  cancelBooking: (id: number): Promise<BookingResponse> =>
    fetch(`/api/bookings/${id}/cancel`, { method: "POST" }).then((r) => parse<BookingResponse>(r)),
};
