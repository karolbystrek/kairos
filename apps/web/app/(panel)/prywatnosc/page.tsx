import type { Metadata } from "next";

import { LegalDocument } from "@/components/legal-document";
import { legalOperator } from "@/src/legal/operator";
import { paths } from "@/src/routes";
import { publicPageMetadata } from "@/src/seo/metadata";

export const metadata: Metadata = publicPageMetadata({
  title: "Polityka prywatności",
  description:
    "Jak Kairos przetwarza dane osobowe klientów restauracji i użytkowników panelu.",
  path: paths.privacy,
});

export default function PrivacyPolicyPage() {
  return (
    <LegalDocument title="Polityka prywatności">
      <p>
        Kairos prowadzi {legalOperator.name}, adres do korespondencji:{" "}
        {legalOperator.address}, e-mail:{" "}
        <a href={`mailto:${legalOperator.email}`}>{legalOperator.email}</a>.
        Kairos jest bezpłatnym projektem pracy dyplomowej udostępnianym w wersji
        testowej do dnia {legalOperator.testPeriodEnd}.
      </p>
      <p>
        Pierwsza część dotyczy{" "}
        <a href="#klienci">klientów restauracji, którzy śledzą zamówienia</a>.
        Druga dotyczy{" "}
        <a href="#panel">
          osób korzystających z panelu Kairos i jego strony głównej
        </a>
        .
      </p>

      <h2 id="klienci">Klienci restauracji</h2>
      <p>
        Kairos pozwala śledzić zamówienie w restauracji bez zakładania konta.
        Nie pytamy o imię, numer telefonu ani adres e-mail.
      </p>

      <h3>Kto odpowiada za Twoje dane</h3>
      <p>
        Administratorem danych związanych z Twoim zamówieniem jest restauracja,
        w której je złożono. Kairos dostarcza restauracji system śledzenia
        zamówień i przetwarza dane w jej imieniu jako podmiot przetwarzający, na
        zasadach opisanych w § 8 <a href={paths.terms}>Regulaminu</a>. W sprawie
        danych możesz skontaktować się z restauracją albo z twórcą Kairos:{" "}
        <a href={`mailto:${legalOperator.email}`}>{legalOperator.email}</a>.
      </p>

      <h3>Jakie dane przetwarzamy</h3>
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
        Kairos zlicza zdarzenia zamówień oraz kliknięcia linku do opinii i
        wyraźne zamknięcia zaproszeń. Statystyki przechowujemy łącznie dla
        restauracji, lokalu i godziny, bez identyfikatorów klientów, zamówień,
        urządzeń ani adresów IP. Nie korzystamy z narzędzi reklamowych i nie
        śledzimy Cię na innych stronach.
      </p>

      <h3>Co zapisujemy na Twoim urządzeniu</h3>
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

      <h3>Powiadomienia</h3>
      <p>
        Powiadomienia są dobrowolne. Wyłączysz je w dowolnej chwili ikoną
        dzwonka w aplikacji albo w ustawieniach przeglądarki. Powiadomienia
        przechodzą przez usługę push dostawcy Twojej przeglądarki (np. Google,
        Apple lub Mozilla); ich treść jest szyfrowana.
      </p>

      <h3>Opinie w Google</h3>
      <p>
        Jeśli restauracja to włączyła, po zakończeniu zamówienia aplikacja może
        wyświetlić prośbę o wystawienie opinii w Google. Prośba pojawia się
        tylko w otwartej aplikacji i nie jest wysyłana jako powiadomienie. Po
        przejściu do Google obowiązują zasady prywatności Google. Kairos nie
        wie, czy wystawiono opinię.
      </p>

      <h3>Odbiorcy danych</h3>
      <p>
        Dane są przechowywane na serwerze Amazon Web Services we Frankfurcie
        (UE). Ruch przechodzi przez Cloudflare, który może przetwarzać adresy IP
        także w USA na podstawie programu EU-US Data Privacy Framework lub
        standardowych klauzul umownych.
      </p>

      <h3>Jak długo przechowujemy dane</h3>
      <ul>
        <li>
          Subskrypcję powiadomień usuwamy po 30 dniach bez aktywnych zamówień
          albo od razu po wyłączeniu powiadomień.
        </li>
        <li>Historię wysyłki powiadomień usuwamy po 7–30 dniach.</li>
        <li>
          Zbiorcze statystyki zachowujemy do trwałego usunięcia danych
          restauracji, również po usunięciu lokalu.
        </li>
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

      <h3>Twoje prawa</h3>
      <p>
        Masz prawo dostępu do danych, ich sprostowania, usunięcia, ograniczenia
        przetwarzania i sprzeciwu. Ponieważ nie znamy Twojej tożsamości, do
        realizacji żądania może być potrzebny identyfikator zamówienia z kodu
        QR.
      </p>

      <h2 id="panel">Użytkownicy panelu i strony głównej</h2>

      <h3>Administrator danych</h3>
      <p>
        Administratorem danych osobowych Użytkowników panelu jest{" "}
        {legalOperator.name}.
      </p>

      <h3>Jakie dane przetwarzamy i dlaczego</h3>
      <ul>
        <li>
          <strong>Konto:</strong> adres e-mail, hasło (przechowywane wyłącznie w
          postaci skrótu przez usługę logowania), rola, przypisane lokale i
          status konta. Cel: założenie konta i świadczenie usług zgodnie z{" "}
          <a href={paths.terms}>Regulaminem</a> (art. 6 ust. 1 lit. b RODO).
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
        zlicza otwarcia kolejki, menu, ustawień integracji i formularza
        zamówienia. Statystyki przechowujemy łącznie dla restauracji, lokalu
        (gdy dotyczy) i godziny, bez identyfikatorów kont, sesji, urządzeń ani
        adresów IP. Nie korzystamy z narzędzi reklamowych, nie profilujemy
        Użytkowników i nie podejmujemy wobec nich zautomatyzowanych decyzji.
      </p>

      <h3>Pliki cookie i pamięć przeglądarki</h3>
      <p>
        Panel używa wyłącznie plików cookie niezbędnych do działania: cookie
        sesji, które utrzymuje zalogowanie, i cookie ochrony przed atakami CSRF.
        W pamięci przeglądarki zapisywany jest też wybrany motyw (jasny lub
        ciemny). Te informacje są niezbędne do świadczenia usługi, o którą
        prosisz, dlatego nie wymagają zgody (art. 399 ust. 3 ustawy – Prawo
        komunikacji elektronicznej). Możesz je usunąć w ustawieniach
        przeglądarki; usunięcie cookie sesji spowoduje wylogowanie.
      </p>

      <h3>Odbiorcy danych</h3>
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
          Resend – wysyłka kodów weryfikacyjnych i zaproszeń do konta. Dostawca
          otrzymuje adres odbiorcy i treść wiadomości potrzebne do jej
          dostarczenia.
        </li>
        <li>
          Administratorzy i kierownicy Restauracji, w której masz konto, widzą
          Twój adres e-mail, rolę i status konta.
        </li>
      </ul>
      <p>
        Hasła przechowujemy wyłącznie jako skróty kryptograficzne w bazie danych
        na tym samym serwerze i nie przekazujemy ich podmiotom zewnętrznym.
      </p>

      <h3>Jak długo przechowujemy dane</h3>
      <p>
        Dane konta przechowujemy przez czas korzystania z Kairos. Usunięcie
        konta od razu blokuje logowanie, a po 30 dniach trwale usuwamy konto,
        jego dane w usłudze logowania i związane z nim zaproszenia. Do tego
        czasu adres e-mail pozostaje zablokowany przed ponownym użyciem.
        Zaproszenia usuwamy 30 dni po ich wygaśnięciu lub odwołaniu.
        Niedokończoną rejestrację, wraz z danymi w usłudze logowania, usuwamy 30
        dni po wysłaniu ostatniego kodu, a zapisy wysyłki e-maili 30 dni po jej
        zakończeniu. Sesja wygasa po wylogowaniu lub 30 dniach braku aktywności.
        Logi serwera z adresami IP i zdarzeniami bezpieczeństwa są nadpisywane
        po osiągnięciu limitu rozmiaru. Pozostałe dane zostaną usunięte w ciągu
        30 dni po zakończeniu Okresu testowego. Zbiorcze statystyki zachowujemy
        do trwałego usunięcia danych restauracji, również po usunięciu lokalu.
      </p>

      <h3>Twoje prawa</h3>
      <p>
        Masz prawo dostępu do danych, ich sprostowania, usunięcia, ograniczenia
        przetwarzania, przenoszenia oraz sprzeciwu wobec przetwarzania opartego
        na prawnie uzasadnionym interesie. Napisz na{" "}
        <a href={`mailto:${legalOperator.email}`}>{legalOperator.email}</a>.
      </p>

      <h2>Skarga do organu nadzorczego</h2>
      <p>
        Możesz wnieść skargę do Prezesa Urzędu Ochrony Danych Osobowych (ul.
        Stawki 2, 00-193 Warszawa).
      </p>
    </LegalDocument>
  );
}
