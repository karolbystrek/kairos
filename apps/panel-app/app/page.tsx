import type { Metadata } from "next";

import { Link } from "@heroui/react";
import { buttonVariants } from "@heroui/styles";

import { LandingSessionRedirect } from "@/components/landing-session-redirect";

export const metadata: Metadata = {
  title: { absolute: "Kairos — Virtual pagers for restaurants" },
  description:
    "Let guests scan a QR code and follow their order. Manage your restaurant queue without physical pagers or an app download.",
};

export default function Welcome() {
  return (
    <div className="welcome-page mx-auto flex min-h-dvh max-w-5xl flex-col">
      <LandingSessionRedirect />
      <header className="flex items-center justify-between gap-6 py-6">
        <p className="app-name text-2xl">Kairos</p>
        <nav aria-label="Restaurant account">
          <Link className="min-h-11 px-2" href="/login">
            Sign in
          </Link>
        </nav>
      </header>

      <section
        aria-labelledby="welcome-title"
        className="flex flex-1 flex-col items-center justify-center py-20 text-center sm:py-28"
      >
        <h1 className="welcome-title max-w-3xl" id="welcome-title">
          <span className="block">Your guests’ phones.</span>
          <span className="block">Your restaurant’s pagers.</span>
        </h1>
        <p className="secondary-text mt-6 max-w-lg text-lg leading-relaxed">
          Let guests scan a QR code and follow their order. Manage your queue in
          one simple panel—no physical pagers, no app download.
        </p>
        <Link
          className={buttonVariants({
            size: "lg",
            className: "mt-8 max-w-full whitespace-normal text-center",
          })}
          href="/registration"
        >
          Get started
        </Link>
      </section>

      <footer className="secondary-text flex flex-wrap items-center justify-center gap-x-2 gap-y-1 py-6 text-sm">
        <span>Questions?</span>
        <Link
          className="secondary-text min-h-11 break-all"
          href="mailto:karbystrek@gmail.com"
        >
          karbystrek@gmail.com
        </Link>
      </footer>
    </div>
  );
}
