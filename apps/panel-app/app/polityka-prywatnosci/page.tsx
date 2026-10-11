import type { Metadata } from "next";

import { LegalDocument } from "@/components/legal-document";
import { customerAppUrl } from "@/src/config/public-environment";
import { legalOperator } from "@/src/legal/operator";
import { publicPageMetadata } from "@/src/seo/metadata";

export const metadata: Metadata = publicPageMetadata({
  title: "Polityka prywatności",
  description: "Jak Kairos przetwarza dane osobowe użytkowników panelu.",
  path: "/polityka-prywatnosci",
});

export default function PrivacyPolicyPage() {
  return (
    <LegalDocument title="Polityka prywatności">
      <p>
        Ta informacja dotyczy osób korzystających z panelu Kairos i jego strony
        głównej. Klienci restauracji, którzy śledzą zamówienia, znajdą osobną
        informację w{" "}
        <a href={new URL("/prywatnosc", customerAppUrl).toString()}>
          aplikacji klienta
        </a>
        .
      </p>

      <h2>Administrator danych</h2>
      <p>
        Administratorem danych osobowych Użytkowników panelu jest{" "}
        {legalOperator.name}, adres do korespondencji: {legalOperator.address},
        e-mail:{" "}
        <a href={`mailto:${legalOperator.email}`}>{legalOperator.email}</a>.
        Kairos jest bezpłatnym projektem pracy dyplomowej udostępnianym w wersji
        testowej do dnia {legalOperator.testPeriodEnd}.
      </p>

      <h2>Jakie dane przetwarzamy i dlaczego</h2>
      <ul>
        <li>
          <strong>Konto:</strong> adres e-mail, hasło (przechowywane wyłącznie w
          postaci skrótu przez usługę logowania), rola, przypisane lokale i
          status konta. Cel: założenie konta i świadczenie usług zgodnie z{" "}
          <a href="/regulamin">Regulaminem</a> (art. 6 ust. 1 lit. b RODO).
        </li>
        <li>
          <strong>Zaproszenia:</strong> adres e-mail osoby zapraszanej, rola i
          lokal. Cel: umożliwienie Restauracji dołączenia członków zespołu (art.
          6 ust. 1 lit. b i f RODO).
        </li>
        <li>
          <strong>Sesja i bezpieczeństwo:</strong> pliki cookie sesji i ochrony
          CSRF, adres IP, informacje o przeglądarce i zdarzeniach bezpieczeństwa
          (np. logowania). Cel: utrzymanie zalogowania, ochrona przed
          nadużyciami i dochodzenie roszczeń (art. 6 ust. 1 lit. b i f RODO).
        </li>
        <li>
          <strong>Kontakt:</strong> treść wiadomości i adres e-mail, gdy piszesz
          do nas. Cel: odpowiedź i obsługa reklamacji (art. 6 ust. 1 lit. f
          RODO).
        </li>
      </ul>
      <p>
        Podanie adresu e-mail i hasła jest niezbędne do założenia konta. Kairos
        nie korzysta z narzędzi analitycznych ani reklamowych, nie profiluje
        Użytkowników i nie podejmuje wobec nich zautomatyzowanych decyzji.
      </p>

      <h2>Pliki cookie i pamięć przeglądarki</h2>
      <p>
        Panel używa wyłącznie plików cookie niezbędnych do działania: cookie
        sesji, które utrzymuje zalogowanie, i cookie ochrony przed atakami CSRF.
        W pamięci przeglądarki zapisywany jest też wybrany motyw (jasny lub
        ciemny). Te informacje są niezbędne do świadczenia usługi, o którą
        prosisz, dlatego nie wymagają zgody (art. 399 ust. 3 ustawy – Prawo
        komunikacji elektronicznej). Możesz je usunąć w ustawieniach
        przeglądarki; usunięcie cookie sesji spowoduje wylogowanie.
      </p>

      <h2>Odbiorcy danych</h2>
      <ul>
        <li>
          Amazon Web Services EMEA SARL – hosting serwera w regionie Frankfurt
          (UE).
        </li>
        <li>
          Cloudflare, Inc. – sieć dostarczania treści i ochrona przed atakami;
          przetwarza m.in. adresy IP i może przekazywać dane do USA na podstawie
          programu EU-US Data Privacy Framework lub standardowych klauzul
          umownych.
        </li>
        <li>
          Administratorzy i kierownicy Restauracji, w której masz konto, widzą
          Twój adres e-mail, rolę i status konta.
        </li>
      </ul>
      <p>
        Usługa logowania (ZITADEL) działa na tym samym serwerze i nie przekazuje
        danych podmiotom zewnętrznym.
      </p>

      <h2>Jak długo przechowujemy dane</h2>
      <p>
        Dane konta przechowujemy przez czas korzystania z Kairos. Usunięcie
        konta od razu blokuje logowanie, a po 30 dniach trwale usuwamy konto,
        jego dane w usłudze logowania i związane z nim zaproszenia. Do tego
        czasu adres e-mail pozostaje zablokowany przed ponownym użyciem.
        Zaproszenia usuwamy 30 dni po ich wygaśnięciu lub odwołaniu. Sesja
        wygasa po wylogowaniu lub 30 dniach braku aktywności. Logi serwera z
        adresami IP i zdarzeniami bezpieczeństwa są nadpisywane po osiągnięciu
        limitu rozmiaru. Pozostałe dane zostaną usunięte w ciągu 30 dni po
        zakończeniu Okresu testowego.
      </p>

      <h2>Twoje prawa</h2>
      <p>
        Masz prawo dostępu do danych, ich sprostowania, usunięcia, ograniczenia
        przetwarzania, przenoszenia oraz sprzeciwu wobec przetwarzania opartego
        na prawnie uzasadnionym interesie. Napisz na{" "}
        <a href={`mailto:${legalOperator.email}`}>{legalOperator.email}</a>.
        Możesz też wnieść skargę do Prezesa Urzędu Ochrony Danych Osobowych (ul.
        Stawki 2, 00-193 Warszawa).
      </p>

      <h2>Dane klientów restauracji</h2>
      <p>
        Dane związane ze śledzeniem zamówień przez klientów Kairos przetwarza w
        imieniu Restauracji jako podmiot przetwarzający, na zasadach opisanych w
        § 8 <a href="/regulamin">Regulaminu</a>.
      </p>
    </LegalDocument>
  );
}
