import type { Metadata } from "next";

import { Link } from "@heroui/react";
import { buttonVariants } from "@heroui/styles";

import { LandingSessionRedirect } from "@/components/landing-session-redirect";

export const metadata: Metadata = {
  title: { absolute: "Kairos — Wirtualne pagery dla restauracji" },
  description:
    "Klienci skanują kod QR i śledzą swoje zamówienia. Zarządzaj kolejką w restauracji bez fizycznych pagerów i pobierania aplikacji.",
};

export default function Welcome() {
  return (
    <div className="welcome-page mx-auto flex min-h-dvh max-w-5xl flex-col">
      <LandingSessionRedirect />
      <header className="flex items-center justify-between gap-6 py-6">
        <p className="app-name text-2xl">Kairos</p>
        <nav aria-label="Konto restauracji">
          <Link className="min-h-11 px-2" href="/login">
            Zaloguj się
          </Link>
        </nav>
      </header>

      <section
        aria-labelledby="welcome-title"
        className="flex flex-1 flex-col items-center justify-center py-20 text-center sm:py-28"
      >
        <h1 className="welcome-title max-w-3xl" id="welcome-title">
          <span className="block">Telefony Twoich gości.</span>
          <span className="block">Pagery Twojej restauracji.</span>
        </h1>
        <p className="secondary-text mt-6 max-w-lg text-lg leading-relaxed">
          Klienci skanują kod QR i śledzą swoje zamówienia. Zarządzaj kolejką w
          jednym prostym panelu, bez fizycznych pagerów i pobierania aplikacji.
        </p>
        <Link
          className={buttonVariants({
            size: "lg",
            className: "mt-8 max-w-full whitespace-normal text-center",
          })}
          href="/registration"
        >
          Zacznij teraz
        </Link>
      </section>

      <footer className="secondary-text flex flex-wrap items-center justify-center gap-x-2 gap-y-1 py-6 text-sm">
        <span>Masz pytania?</span>
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
