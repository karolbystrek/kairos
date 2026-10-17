import type { Metadata } from "next";

import { Accordion, Link } from "@heroui/react";
import { buttonVariants } from "@heroui/styles";
import { CheckIcon } from "@phosphor-icons/react/dist/ssr";
import Image from "next/image";

import { LandingDemo } from "@/components/landing-demo";
import { LandingSessionRedirect } from "@/components/landing-session-redirect";
import { legalOperator } from "@/src/legal/operator";
import { publicPageMetadata } from "@/src/seo/metadata";

const TITLE = "Kairos — Wirtualne pagery dla restauracji";
const CONTACT_EMAIL = legalOperator.email;

export const metadata: Metadata = {
  ...publicPageMetadata({
    title: TITLE,
    description:
      "Gość skanuje kod QR, śledzi zamówienie i dostaje powiadomienie, gdy jest gotowe. Bez pagerów i bez aplikacji do pobrania.",
    path: "/",
  }),
  title: { absolute: TITLE },
};

const steps = [
  {
    title: "Dodajesz zamówienie",
    text: "Przy kasie wpisujesz zamówienie w panelu. Kairos nadaje mu numer i pokazuje kod QR na ekranie.",
  },
  {
    title: "Gość skanuje kod",
    text: "Wystarczy aparat w telefonie. Strona z zamówieniem otwiera się od razu, bez aplikacji i bez zakładania konta.",
  },
  {
    title: "Klikasz „Gotowe”",
    text: "Telefon gościa od razu pokazuje, że zamówienie czeka przy ladzie. Jeśli gość włączył powiadomienia, dostaje też powiadomienie.",
  },
];

const benefits = [
  {
    title: "Dla Ciebie",
    items: [
      [
        "Nie kupujesz pagerów.",
        "Nic się nie gubi, nie psuje i nie trzeba niczego ładować.",
      ],
      [
        "Mniej wołania i pytań.",
        "Goście nie dopytują, czy ich zamówienie jest już gotowe.",
      ],
      [
        "Działa na tym, co masz.",
        "Panel otwierasz w przeglądarce na tablecie, laptopie lub telefonie.",
      ],
      [
        "Cały zespół w jednym miejscu.",
        "Zapraszasz pracowników i decydujesz, do których lokali mają dostęp.",
      ],
    ],
  },
  {
    title: "Dla Twoich gości",
    items: [
      ["Nie muszą stać przy ladzie.", "Mogą usiąść i spokojnie poczekać."],
      [
        "Nic nie instalują.",
        "Wystarczy aparat i przeglądarka, które już mają.",
      ],
      [
        "Widzą status na bieżąco.",
        "Strona sama się aktualizuje, gdy zamówienie jest gotowe.",
      ],
      [
        "Zostają anonimowi.",
        "Nie podają imienia, numeru telefonu ani adresu e-mail.",
      ],
    ],
  },
];

const questions = [
  {
    question: "Czy gość musi pobrać aplikację?",
    answer:
      "Nie. Po zeskanowaniu kodu strona zamówienia otwiera się w przeglądarce telefonu. Gość może dodać Kairos do ekranu głównego, ale nie musi.",
  },
  {
    question: "Czy powiadomienia działają na iPhonie?",
    answer:
      "Tak, ale Apple wymaga, żeby gość najpierw dodał Kairos do ekranu głównego. Kairos pokazuje, jak to zrobić w trzech krokach. Bez tego gość nadal widzi aktualny status na otwartej stronie. Na Androidzie w Chrome powiadomienia można włączyć od razu.",
  },
  {
    question: "Co, jeśli gość nie włączy powiadomień?",
    answer:
      "Strona zamówienia sama pokazuje każdą zmianę statusu. Jeśli przeglądarka na to pozwala, telefon krótko zawibruje, gdy zamówienie będzie gotowe.",
  },
  {
    question: "Jakiego sprzętu potrzebuję?",
    answer:
      "Wystarczy urządzenie z przeglądarką i internetem, na przykład tablet przy kasie. Gość skanuje kod QR wyświetlony na ekranie.",
  },
  {
    question: "Mam własny system sprzedaży. Czy mogę go połączyć?",
    answer:
      "Tak. Kairos może przyjmować zamówienia z Twojego systemu automatycznie. Napisz do nas, a pomożemy to ustawić.",
  },
  {
    question: "Jak zacząć?",
    answer: "Załóż konto, dodaj swój lokal i wydaj pierwsze zamówienie.",
  },
];

const primaryAction = buttonVariants({
  size: "lg",
  className: "landing-action",
});
const secondaryAction = buttonVariants({
  size: "lg",
  variant: "ghost",
  className: "landing-action",
});

export default function Landing() {
  return (
    <div className="landing-page">
      <LandingSessionRedirect />
      <header className="landing-header">
        <div className="landing-container flex h-14 items-center justify-between gap-4">
          <Link
            className="flex items-center gap-2 text-foreground no-underline"
            href="/"
          >
            <Image
              alt=""
              height={28}
              src="/icons/kairos-app-icon.svg"
              width={28}
            />
            <span className="app-name text-xl">Kairos</span>
          </Link>
          <nav
            aria-label="Strona główna"
            className="flex items-center gap-1 sm:gap-4"
          >
            <Link
              className="landing-nav-link max-sm:hidden"
              href="#jak-to-dziala"
            >
              Jak to działa
            </Link>
            <Link className="landing-nav-link max-sm:hidden" href="#korzysci">
              Korzyści
            </Link>
            <Link className="landing-nav-link max-sm:hidden" href="#pytania">
              Pytania
            </Link>
            <Link className="landing-nav-link" href="/login">
              Zaloguj się
            </Link>
            <Link
              className={buttonVariants({ size: "sm" })}
              href="/registration"
            >
              Załóż konto
            </Link>
          </nav>
        </div>
      </header>

      <div className="landing-container">
        <section aria-labelledby="landing-title" className="landing-hero">
          <h1 className="landing-title" id="landing-title">
            Pager, który gość{" "}
            <span className="text-accent">ma już w kieszeni.</span>
          </h1>
          <p className="landing-lead">
            Gość skanuje kod QR, widzi, co dzieje się z jego zamówieniem, i
            dostaje powiadomienie, gdy jest gotowe. Bez pobierania aplikacji.
          </p>
          <div className="landing-actions">
            <Link className={primaryAction} href="/registration">
              Załóż konto
            </Link>
            <Link className={secondaryAction} href="#demo">
              Zobacz, jak to działa
            </Link>
          </div>
        </section>

        <section aria-label="Demo" className="landing-demo-stage" id="demo">
          <p className="secondary-text mb-7 text-center">
            Wypróbuj: dodaj zamówienie, zeskanuj je telefonem, a potem oznacz
            jako gotowe.
          </p>
          <LandingDemo />
        </section>

        <section
          aria-labelledby="jak-to-dziala-title"
          className="landing-section"
          id="jak-to-dziala"
        >
          <p className="landing-eyebrow text-accent">Jak to działa</p>
          <h2 className="landing-heading" id="jak-to-dziala-title">
            Trzy kroki. Zero pagerów.
          </h2>
          <ol className="landing-steps">
            {steps.map((step, index) => (
              <li key={step.title} className="landing-step">
                <span aria-hidden="true" className="landing-step-number">
                  {index + 1}
                </span>
                <h3 className="text-xl font-semibold">{step.title}</h3>
                <p className="secondary-text mt-2">{step.text}</p>
              </li>
            ))}
          </ol>
        </section>

        <section
          aria-labelledby="korzysci-title"
          className="landing-section"
          id="korzysci"
        >
          <p className="landing-eyebrow text-accent">Korzyści</p>
          <h2 className="landing-heading" id="korzysci-title">
            Spokojniej przy ladzie. Wygodniej przy stoliku.
          </h2>
          <div className="landing-benefits">
            {benefits.map((group) => (
              <div key={group.title} className="landing-benefit-group">
                <h3 className="mb-5 text-2xl font-semibold">{group.title}</h3>
                <ul className="grid gap-4">
                  {group.items.map(([title, text]) => (
                    <li key={title} className="flex gap-3">
                      <CheckIcon
                        aria-hidden="true"
                        className="mt-1 flex-none text-accent"
                        size={18}
                        weight="bold"
                      />
                      <p>
                        <span className="block font-semibold">{title}</span>
                        <span className="secondary-text">{text}</span>
                      </p>
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </div>
        </section>

        <section
          aria-labelledby="pytania-title"
          className="landing-section"
          id="pytania"
        >
          <p className="landing-eyebrow text-accent">Pytania</p>
          <h2 className="landing-heading" id="pytania-title">
            Najczęstsze pytania
          </h2>
          <Accordion className="landing-faq">
            {questions.map((item) => (
              <Accordion.Item key={item.question} id={item.question}>
                <Accordion.Heading>
                  <Accordion.Trigger>
                    {item.question}
                    <Accordion.Indicator />
                  </Accordion.Trigger>
                </Accordion.Heading>
                <Accordion.Panel>
                  <Accordion.Body className="secondary-text">
                    {item.answer}
                  </Accordion.Body>
                </Accordion.Panel>
              </Accordion.Item>
            ))}
          </Accordion>
        </section>

        <section aria-labelledby="cta-title" className="landing-cta">
          <h2 className="landing-heading mx-auto" id="cta-title">
            Wypróbuj Kairos w swojej restauracji.
          </h2>
          <p className="landing-lead">
            Załóż konto i wydaj pierwsze zamówienie jeszcze dziś.
          </p>
          <div className="landing-actions">
            <Link className={primaryAction} href="/registration">
              Załóż konto
            </Link>
            <Link className={secondaryAction} href={`mailto:${CONTACT_EMAIL}`}>
              Napisz do nas
            </Link>
          </div>
        </section>
      </div>

      <footer className="landing-footer">
        <div className="landing-container secondary-text flex flex-wrap items-center justify-between gap-x-6 gap-y-1 text-sm">
          <p className="flex flex-wrap items-center gap-x-2">
            <span>Masz pytania?</span>
            <Link
              className="secondary-text min-h-11 break-all"
              href={`mailto:${CONTACT_EMAIL}`}
            >
              {CONTACT_EMAIL}
            </Link>
          </p>
          <nav
            aria-label="Informacje prawne"
            className="flex flex-wrap items-center gap-x-4"
          >
            <Link className="secondary-text min-h-11" href="/regulamin">
              Regulamin
            </Link>
            <Link
              className="secondary-text min-h-11"
              href="/polityka-prywatnosci"
            >
              Polityka prywatności
            </Link>
          </nav>
        </div>
      </footer>
    </div>
  );
}
