import type { Metadata } from "next";
import { Bricolage_Grotesque, Hanken_Grotesk } from "next/font/google";
import "./globals.css";
import KeepAlive from "@/components/KeepAlive";

const display = Bricolage_Grotesque({
  subsets: ["latin"],
  weight: ["600", "700", "800"],
  variable: "--font-display",
  display: "swap",
});

const body = Hanken_Grotesk({
  subsets: ["latin"],
  weight: ["400", "500", "600", "700", "800"],
  variable: "--font-body",
  display: "swap",
});

export const metadata: Metadata = {
  title: "Front Row - Live Event Tickets",
  description:
    "Browse live events and book tickets. A demo storefront over a Spring Boot microservices ticketing backend (OpenFeign + Resilience4j, pessimistic seat locking).",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en" className={`${display.variable} ${body.variable}`}>
      <body>
        <KeepAlive endpoint="/api/health" />
        {children}
      </body>
    </html>
  );
}
