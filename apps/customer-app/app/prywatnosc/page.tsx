import type { Metadata } from "next";

import { LegalDocument } from "@/components/legal-document";
import { legalOperator } from "@/src/legal/operator";

export const metadata: Metadata = {
  title: "Prywatność",
  description: "Jakie dane przetwarza Kairos, gdy śledzisz zamówienie.",
};

export default function PrivacyNoticePage() {
  return (
    <LegalDocument title="Prywatność">
      <p>
        Kairos pozwala śledzić zamówienie w restauracji bez zakładania konta.
        Nie pytamy o imię, numer telefonu ani adres e-mail.
      </p>

      <h2>Kto odpowiada za Twoje dane</h2>
      <p>
        Administratorem danych związanych z Twoim zamówieniem jest restauracja,
        w której je złożono. Kairos dostarcza restauracji system śledzenia
        zamówień i przetwarza dane w jej imieniu. W sprawie danych możesz
        skontaktować się z restauracją albo z twórcą Kairos:{" "}
        {legalOperator.name},{" "}
        <a href={`mailto:${legalOperator.email}`}>{legalOperator.email}</a>.
        Kairos jest bezpłatnym projektem pracy dyplomowej udostępnianym w wersji
        testowej do dnia {legalOperator.testPeriodEnd}.
      </p>

      <h2>Jakie dane przetwarzamy</h2>
      <ul>
        <li>
          <strong>Zamówienie:</strong> losowy identyfikator z kodu QR, numer lub
          oznaczenie zamówienia i jego status. Służą do pokazania Ci aktualnego
          statusu.
        </li>
        <li>
          <strong>Powiadomienia (opcjonalnie):</strong> jeśli je włączysz,
          zapisujemy zaszyfrowany adres subskrypcji powiadomień Twojej
          przeglądarki i to, których zamówień dotyczą. Powiadomienia informują
          wyłącznie o zmianie statusu zamówienia.
        </li>
        <li>
          <strong>Dane techniczne:</strong> adres IP i informacje o
          przeglądarce, niezbędne do nawiązania połączenia i ochrony przed
          nadużyciami.
        </li>
      </ul>
      <p>
        Kairos nie korzysta z narzędzi analitycznych ani reklamowych i nie
        śledzi Cię na innych stronach.
      </p>

      <h2>Co zapisujemy na Twoim urządzeniu</h2>
      <p>
        Aplikacja zapisuje w pamięci przeglądarki listę śledzonych zamówień,
        stan aplikacji (np. ostatnio otwarty ekran), ustawienia powiadomień,
        wybrany motyw i informację, czy zamknięto zaproszenie do opinii.
        Instaluje też skrypt (service worker), który pozwala działać offline i
        odbierać powiadomienia. Te informacje są niezbędne do działania funkcji,
        z których korzystasz, dlatego nie wymagają zgody (art. 399 ust. 3 ustawy
        – Prawo komunikacji elektronicznej). Możesz je usunąć, czyszcząc dane
        witryny w ustawieniach przeglądarki.
      </p>

      <h2>Powiadomienia</h2>
      <p>
        Powiadomienia są dobrowolne. Wyłączysz je w dowolnej chwili ikoną
        dzwonka w aplikacji albo w ustawieniach przeglądarki. Powiadomienia
        przechodzą przez usługę push dostawcy Twojej przeglądarki (np. Google,
        Apple lub Mozilla); ich treść jest szyfrowana.
      </p>

      <h2>Opinie w Google</h2>
      <p>
        Jeśli restauracja to włączyła, po zakończeniu zamówienia aplikacja może
        wyświetlić prośbę o wystawienie opinii w Google. Prośba pojawia się
        tylko w otwartej aplikacji i nie jest wysyłana jako powiadomienie. Po
        przejściu do Google obowiązują zasady prywatności Google. Kairos nie
        wie, czy wystawiono opinię.
      </p>

      <h2>Odbiorcy danych</h2>
      <p>
        Dane są przechowywane na serwerze Amazon Web Services we Frankfurcie
        (UE). Ruch przechodzi przez Cloudflare, który może przetwarzać adresy IP
        także w USA na podstawie programu EU-US Data Privacy Framework lub
        standardowych klauzul umownych.
      </p>

      <h2>Jak długo przechowujemy dane</h2>
      <ul>
        <li>
          Subskrypcję powiadomień usuwamy po 30 dniach bez aktywnych zamówień
          albo od razu po wyłączeniu powiadomień.
        </li>
        <li>Historię wysyłki powiadomień usuwamy po 7–30 dniach.</li>
        <li>
          Logi serwera z adresem IP są nadpisywane po osiągnięciu limitu
          rozmiaru.
        </li>
        <li>
          Zamówienia lokalu usuniętego przez restaurację usuwamy po 30 dniach.
          Pozostałe zamówienia przechowujemy do końca okresu testowego;
          wszystkie dane zostaną usunięte w ciągu 30 dni po jego zakończeniu.
        </li>
      </ul>

      <h2>Twoje prawa</h2>
      <p>
        Masz prawo dostępu do danych, ich sprostowania, usunięcia, ograniczenia
        przetwarzania i sprzeciwu. Ponieważ nie znamy Twojej tożsamości, do
        realizacji żądania może być potrzebny identyfikator zamówienia z kodu
        QR. Możesz też wnieść skargę do Prezesa Urzędu Ochrony Danych Osobowych
        (ul. Stawki 2, 00-193 Warszawa).
      </p>
    </LegalDocument>
  );
}
