import { LdtArray } from "./types";

export function parseLdt(a?: LdtArray | null): Date | null {
  if (!Array.isArray(a) || a.length < 3) return null;
  const [y, mo, d, h = 0, mi = 0, s = 0] = a;
  return new Date(y, (mo || 1) - 1, d, h, mi, s);
}

export function formatDate(a?: LdtArray | null): string {
  const d = parseLdt(a);
  if (!d) return "Date TBA";
  return d.toLocaleDateString("en-ZA", { weekday: "short", day: "numeric", month: "short", year: "numeric" });
}

export function formatTime(a?: LdtArray | null): string {
  const d = parseLdt(a);
  if (!d) return "";
  return d.toLocaleTimeString("en-ZA", { hour: "2-digit", minute: "2-digit" });
}

export function dayParts(a?: LdtArray | null): { day: string; mon: string } {
  const d = parseLdt(a);
  if (!d) return { day: "--", mon: "TBA" };
  return {
    day: d.toLocaleDateString("en-ZA", { day: "2-digit" }),
    mon: d.toLocaleDateString("en-ZA", { month: "short" }).toUpperCase(),
  };
}

export function zar(n: number): string {
  return new Intl.NumberFormat("en-ZA", {
    style: "currency",
    currency: "ZAR",
    minimumFractionDigits: 2,
  }).format(n);
}
