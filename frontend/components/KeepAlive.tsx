"use client";

import { useEffect, useState } from "react";

/**
 * Two jobs:
 *
 * 1. Keep-alive — while a tab is open, ping the backend health endpoint so the
 *    free-tier Render service doesn't cold-start. Separate frontends can't talk
 *    to each other, so they take turns off the wall clock:
 *      floor(epochMinutes) % TOTAL === INDEX
 *    meaning across every open tab the fleet makes ~one request per minute with
 *    no duplicates, while a lone tab still pings every TOTAL minutes. Keep
 *    TOTAL <= 14 so one tab never crosses the 15-min idle window.
 *
 * 2. Waking-up state — on load, probe health. If the backend isn't confirmed up
 *    quickly (a cold start or a post-deploy restart returns 503), show a small
 *    overlay and poll until it's back, then disappear. A warm backend (the
 *    normal case) resolves in well under the grace delay, so nothing flashes.
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

async function isUp(url: string, timeoutMs = 8000): Promise<boolean> {
  const ctrl = new AbortController();
  const t = setTimeout(() => ctrl.abort(), timeoutMs);
  try {
    const res = await fetch(url, { cache: "no-store", signal: ctrl.signal });
    return res.ok;
  } catch {
    return false;
  } finally {
    clearTimeout(t);
  }
}

export default function KeepAlive({ endpoint }: { endpoint?: string }) {
  const [waking, setWaking] = useState(false);

  useEffect(() => {
    // Only matters against the deployed backend.
    if (process.env.NODE_ENV !== "production") return;

    const url = endpoint ?? derivedUrl();
    if (!url) return;

    let cancelled = false;
    let grace: ReturnType<typeof setTimeout> | undefined;
    let poll: ReturnType<typeof setTimeout> | undefined;

    // Only reveal the overlay if the backend isn't confirmed up quickly, so a
    // warm load never flashes it.
    grace = setTimeout(() => {
      if (!cancelled) setWaking(true);
    }, 2500);

    const settle = (up: boolean) => {
      if (cancelled) return;
      if (up) {
        if (grace) clearTimeout(grace);
        setWaking(false);
      } else {
        setWaking(true);
        poll = setTimeout(run, 3000);
      }
    };
    const run = () => {
      isUp(url).then(settle);
    };
    run();

    // Turn-taking keep-alive warming ping (fire-and-forget).
    const warm = () => {
      fetch(url, { cache: "no-store", keepalive: true }).catch(() => {});
    };
    const interval = setInterval(() => {
      if (document.visibilityState !== "visible") return;
      const minute = Math.floor(Date.now() / 60_000);
      if (minute % TOTAL === INDEX) warm();
    }, 60_000);

    return () => {
      cancelled = true;
      if (grace) clearTimeout(grace);
      if (poll) clearTimeout(poll);
      clearInterval(interval);
    };
  }, [endpoint]);

  if (!waking) return null;

  return (
    <div role="status" aria-live="polite" style={OVERLAY}>
      <div style={CARD}>
        <div style={SPINNER} />
        <div style={TITLE}>Waking up the server</div>
        <div style={SUBTITLE}>
          The free-tier backend was asleep. This can take up to a minute on the
          first visit — it&rsquo;ll load automatically.
        </div>
      </div>
      <style>{"@keyframes kaSpin{to{transform:rotate(360deg)}}"}</style>
    </div>
  );
}

const OVERLAY: React.CSSProperties = {
  position: "fixed",
  inset: 0,
  zIndex: 2147483000,
  display: "flex",
  alignItems: "center",
  justifyContent: "center",
  padding: "24px",
  background: "rgba(8,10,14,0.72)",
  backdropFilter: "blur(6px)",
  WebkitBackdropFilter: "blur(6px)",
};

const CARD: React.CSSProperties = {
  maxWidth: "360px",
  width: "100%",
  textAlign: "center",
  borderRadius: "20px",
  padding: "32px 28px",
  background: "#15181f",
  border: "1px solid rgba(255,255,255,0.08)",
  boxShadow: "0 20px 60px rgba(0,0,0,0.45)",
  color: "#f5f6f8",
  fontFamily: "system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif",
};

const SPINNER: React.CSSProperties = {
  width: "40px",
  height: "40px",
  margin: "0 auto 20px",
  borderRadius: "50%",
  border: "3px solid rgba(255,255,255,0.15)",
  borderTopColor: "#6ea8fe",
  animation: "kaSpin 0.8s linear infinite",
};

const TITLE: React.CSSProperties = {
  fontSize: "17px",
  fontWeight: 700,
  marginBottom: "8px",
  letterSpacing: "-0.01em",
};

const SUBTITLE: React.CSSProperties = {
  fontSize: "13.5px",
  lineHeight: 1.5,
  color: "rgba(245,246,248,0.62)",
};
