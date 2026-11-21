export type EventCategory = "CONCERT" | "SPORT" | "CONFERENCE" | "THEATRE" | "OTHER";
export type EventStatus = "UPCOMING" | "ONGOING" | "COMPLETED" | "CANCELLED" | "SOLD_OUT";
export type BookingStatus = "CONFIRMED" | "CANCELLED";

// Spring serialises LocalDateTime as a numeric array: [year, month, day, hour, minute, second?, nano?]
export type LdtArray = number[];

export interface EventResponse {
  id: number;
  name: string;
  description: string;
  venue: string;
  eventDate: LdtArray;
  category: EventCategory;
  totalSeats: number;
  availableSeats: number;
  pricePerSeat: number;
  status: EventStatus;
  createdAt: LdtArray;
}

export interface BookingRequest {
  eventId: number;
  customerName: string;
  customerEmail: string;
  quantity: number;
}

export interface BookingCheckoutRequest extends BookingRequest {
  // Where Paystack returns the customer after paying (the frontend's own page).
  callbackUrl: string;
}

export interface CheckoutResponse {
  authorizationUrl: string;
  reference: string;
  amount: number;
}

export interface BookingResponse {
  id: number;
  eventId: number;
  customerName: string;
  customerEmail: string;
  quantity: number;
  totalPrice: number;
  status: BookingStatus;
  bookedAt: LdtArray;
  cancelledAt: LdtArray | null;
}
