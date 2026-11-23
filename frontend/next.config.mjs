/**
 * The browser only ever talks to this app's own origin. Every /api/* call is
 * proxied server-side to the portfolio-api monolith, so there is no CORS to
 * configure and no backend change needed to put this frontend online.
 *
 * Override the backend with PORTFOLIO_API_URL if it ever moves.
 */
const API = process.env.PORTFOLIO_API_URL || "https://portfolio-api-dmjl.onrender.com";

/** @type {import('next').NextConfig} */
const nextConfig = {
  async rewrites() {
    return [
      { source: "/api/events", destination: `${API}/events/api/events` },
      { source: "/api/events/:path*", destination: `${API}/events/api/events/:path*` },
      { source: "/api/bookings", destination: `${API}/bookings/api/bookings` },
      { source: "/api/bookings/:path*", destination: `${API}/bookings/api/bookings/:path*` },
      // Keep-alive ping target: proxied to the backend health endpoint so the
      // browser can warm Render without a cross-origin request.
      { source: "/api/health", destination: `${API}/actuator/health` },
    ];
  },
};

export default nextConfig;
