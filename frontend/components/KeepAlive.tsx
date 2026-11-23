"use client";

import { useEffect } from "react";

/**
 * Keeps the shared Render backend from cold-starting while someone has this app
 * open. Render's free tier spins the service down after ~15 min with no inbound
 * HTTP; a lightweight GET to the health endpoint resets that idle timer.
 *
 * Separate frontends can't talk to each other, so they take turns off the wall
 * clock instead of coordinating. Every minute an app pings only when
 *   floor(epochMinutes) % TOTAL === INDEX
 * so across every open tab the fleet makes ~one request per minute with no
 * duplicates, while a single open app still pings every TOTAL minutes. Keep
 * TOTAL <= 14 so even one lone tab never crosses the 15-min idle window.
 *
 * Per-app config (set in each Vercel project; sensible defaults if unset):
 *   NEXT_PUBLIC_API_URL          - backend base, reused from the API client
 *   NEXT_PUBLIC_KEEPALIVE_INDEX  - this app's turn, 0-based (default 0)
 *   NEXT_PUBLIC_KEEPALIVE_TOTAL  - number of apps sharing the rotation (default 1)
 *
 * `endpoint` prop: for apps that proxy the backend through their own origin
 * (Next rewrites) rather than exposing NEXT_PUBLIC_API_URL to the browser,
 * pass a same-origin path like "/api/health".
 */

const BASE = process.env.NEXT_PUBLIC_API_URL || "";
const INDEX = Math.max(0, Math.floor(Number(process.env.NEXT_PUBLIC_KEEPALIVE_INDEX ?? 0)) || 0);
const TOTAL = Math.max(1, Math.floor(Number(process.env.NEXT_PUBLIC_KEEPALIVE_TOTAL ?? 1)) || 1);

function derivedUrl(): string | null {
  if (!BASE) return null;
  try {
    // A leading-slash path resolves to the backend origin, so this works even
    // when NEXT_PUBLIC_API_URL carries a path prefix (e.g. /kafkaorder).
    return new URL("/actuator/health", BASE).toString();
  } catch {
    return null;
  }
}

export default function KeepAlive({ endpoint }: { endpoint?: string }) {
  useEffect(() => {
    // Only matters against the deployed backend.
    if (process.env.NODE_ENV !== "production") return;

    const url = endpoint ?? derivedUrl();
    if (!url) return;

    const sameOrigin = url.startsWith("/");
    const ping = () => {
      // Fire-and-forget. For a cross-origin backend we use no-cors: we never
      // read the body, we only need the request to reach Render and reset its
      // idle timer. Same-origin (proxied) pings are plain fetches.
      const opts: RequestInit = sameOrigin
        ? { cache: "no-store", keepalive: true }
        : { mode: "no-cors", cache: "no-store", keepalive: true };
      fetch(url, opts).catch(() => {});
    };

    // Wake the backend immediately when the app is opened.
    ping();

    const id = window.setInterval(() => {
      // Skip hidden tabs — browsers throttle/freeze their timers anyway.
      if (document.visibilityState !== "visible") return;
      const minute = Math.floor(Date.now() / 60_000);
      if (minute % TOTAL === INDEX) ping();
    }, 60_000);

    return () => window.clearInterval(id);
  }, [endpoint]);

  return null;
}
