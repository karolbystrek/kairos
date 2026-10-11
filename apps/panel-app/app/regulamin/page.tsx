import type { Metadata } from "next";

import { LegalDocument } from "@/components/legal-document";
import { legalOperator } from "@/src/legal/operator";

export const metadata: Metadata = {
  title: "Regulamin",
  description: "Regulamin świadczenia usług drogą elektroniczną w Kairos.",
};

export default function TermsPage() {
  return (
    <LegalDocument title="Regulamin Kairos">
      <h2>§ 1. Postanowienia ogólne</h2>
      <ol>
        <li>
          Regulamin określa zasady korzystania z Kairos, systemu wirtualnych
          pagerów dla restauracji, i stanowi regulamin, o którym mowa w art. 8
          ustawy z dnia 18 lipca 2002 r. o świadczeniu usług drogą
          elektroniczną.
        </li>
        <li>
          Usługodawcą jest {legalOperator.name}, adres do korespondencji:{" "}
          {legalOperator.address}, e-mail:{" "}
          <a href={`mailto:${legalOperator.email}`}>{legalOperator.email}</a>{" "}
          (dalej: „Usługodawca”).
        </li>
        <li>
          Kairos powstaje jako projekt pracy dyplomowej (inżynierskiej) i jest
          udostępniany bezpłatnie w wersji testowej do dnia{" "}
          {legalOperator.testPeriodEnd} (dalej: „Okres testowy”). Usługodawca
          nie prowadzi w związku z Kairos działalności gospodarczej.
        </li>
      </ol>

      <h2>§ 2. Definicje</h2>
      <ul>
        <li>
          <strong>Restauracja</strong> – osoba lub organizacja, która
          zarejestrowała się w Kairos, aby obsługiwać zamówienia swoich
          klientów.
        </li>
        <li>
          <strong>Konto</strong> – konto w panelu Kairos: konto administratora
          utworzone przy rejestracji albo konto kierownika lub pracownika
          utworzone z zaproszenia.
        </li>
        <li>
          <strong>Użytkownik</strong> – osoba korzystająca z Konta.
        </li>
        <li>
          <strong>Klient</strong> – osoba, która śledzi swoje zamówienie w
          aplikacji klienta Kairos po zeskanowaniu kodu QR, bez zakładania
          konta.
        </li>
        <li>
          <strong>Lokal</strong> – miejsce prowadzenia działalności Restauracji
          skonfigurowane w panelu.
        </li>
      </ul>

      <h2>§ 3. Zakres usług</h2>
      <ol>
        <li>
          Usługodawca udostępnia Restauracji panel do zarządzania lokalami,
          zespołem i zamówieniami, a także interfejs REST API oraz webhooki dla
          zewnętrznych systemów, takich jak systemy POS.
        </li>
        <li>
          Klienci Restauracji mogą bezpłatnie śledzić status zamówienia w
          aplikacji klienta i opcjonalnie włączyć powiadomienia push o zmianie
          statusu.
        </li>
        <li>
          Restauracja może włączyć dla Lokalu zaproszenie do wystawienia opinii
          w Google. Zaproszenie jest wyświetlane Klientowi wyłącznie w
          aplikacji, po zakończeniu zamówienia; Kairos nie wysyła go jako
          powiadomienia push.
        </li>
      </ol>

      <h2>§ 4. Wymagania techniczne</h2>
      <ol>
        <li>
          Do korzystania z Kairos potrzebne są urządzenie z dostępem do
          Internetu i aktualna przeglądarka internetowa z włączoną obsługą
          JavaScript i plików cookie.
        </li>
        <li>
          Powiadomienia push wymagają przeglądarki, która je obsługuje, a na
          iPhonie i iPadzie dodania aplikacji klienta do ekranu początkowego.
        </li>
        <li>
          Szczególne zagrożenia związane z korzystaniem z usług elektronicznych
          to m.in. przejęcie hasła, phishing i złośliwe oprogramowanie.
          Użytkownik powinien chronić swoje hasło i klucze API oraz korzystać z
          zaufanych urządzeń.
        </li>
      </ol>

      <h2>§ 5. Zawarcie i rozwiązanie umowy</h2>
      <ol>
        <li>
          Umowa o świadczenie usług zostaje zawarta z chwilą rejestracji, po
          akceptacji Regulaminu. Osoba rejestrująca Restaurację oświadcza, że
          jest do tego uprawniona.
        </li>
        <li>
          Konto kierownika lub pracownika powstaje po przyjęciu zaproszenia i
          akceptacji Regulaminu.
        </li>
        <li>
          Restauracja może w każdej chwili zakończyć korzystanie z Kairos,
          pisząc na adres e-mail Usługodawcy z prośbą o usunięcie jej danych.
          Usunięcie Konta lub lokalu w panelu od razu blokuje do niego dostęp, a
          po 30 dniach Kairos trwale usuwa jego dane, w tym zamówienia
          usuniętego lokalu.
        </li>
        <li>
          Usługodawca może zakończyć świadczenie usług z końcem Okresu testowego
          albo wcześniej, z ważnych powodów, informując Restaurację z co
          najmniej 14-dniowym wyprzedzeniem, chyba że przerwa wynika z przyczyn
          bezpieczeństwa.
        </li>
      </ol>

      <h2>§ 6. Obowiązki Restauracji</h2>
      <ol>
        <li>
          Restauracja korzysta z Kairos zgodnie z prawem i nie wprowadza treści
          bezprawnych.
        </li>
        <li>
          Oznaczenia zamówień powinny ograniczać się do numeru lub krótkiego
          kodu; Restauracja nie powinna umieszczać w nich imion, nazwisk ani
          innych danych Klientów.
        </li>
        <li>
          Restauracja informuje swoich Klientów o przetwarzaniu ich danych w
          związku ze śledzeniem zamówień. Może w tym celu korzystać z informacji
          o prywatności dostępnej w aplikacji klienta.
        </li>
        <li>
          Restauracja odpowiada za link do opinii Google i za zgodność zaproszeń
          do opinii z zasadami Google.
        </li>
        <li>
          Restauracja chroni hasła, zaproszenia i klucze API oraz niezwłocznie
          informuje Usługodawcę o podejrzeniu ich ujawnienia.
        </li>
      </ol>

      <h2>§ 7. Charakter testowy i odpowiedzialność</h2>
      <ol>
        <li>
          Kairos jest udostępniany bezpłatnie, w wersji testowej, w stanie „tak
          jak jest”. Usługodawca nie gwarantuje ciągłości działania ani
          określonego poziomu dostępności.
        </li>
        <li>
          Usługodawca nie wykonuje kopii zapasowych danych w Okresie testowym.
          Dane mogą zostać utracone; Restauracja nie powinna traktować Kairos
          jako jedynego miejsca przechowywania istotnych informacji.
        </li>
        <li>
          W najszerszym zakresie dopuszczalnym przez prawo Usługodawca nie
          odpowiada za szkody wynikające z przerw w działaniu, utraty danych lub
          niedostarczenia powiadomień. Ograniczenie nie dotyczy szkód
          wyrządzonych umyślnie ani przypadków, w których prawo nie pozwala
          ograniczyć odpowiedzialności.
        </li>
      </ol>

      <h2>§ 8. Powierzenie przetwarzania danych osobowych</h2>
      <ol>
        <li>
          W zakresie danych Klientów związanych ze śledzeniem zamówień
          Restauracja jest administratorem danych, a Usługodawca podmiotem
          przetwarzającym w rozumieniu art. 28 RODO. Postanowienia tego
          paragrafu stanowią umowę powierzenia przetwarzania danych.
        </li>
        <li>
          Przedmiotem powierzenia są: identyfikator śledzenia zamówienia,
          oznaczenie i status zamówienia, dane subskrypcji powiadomień push oraz
          techniczne dane połączenia (np. adres IP), przetwarzane w celu
          udostępnienia Klientom śledzenia zamówień i powiadomień, przez czas
          korzystania z Kairos przez Restaurację.
        </li>
        <li>
          Usługodawca:
          <ul>
            <li>
              przetwarza powierzone dane wyłącznie na udokumentowane polecenie
              Restauracji, którym jest korzystanie z funkcji Kairos zgodnie z
              Regulaminem;
            </li>
            <li>
              zapewnia, że osoby upoważnione do przetwarzania danych są
              zobowiązane do zachowania poufności;
            </li>
            <li>
              stosuje środki bezpieczeństwa wymagane przez art. 32 RODO, w tym
              szyfrowanie połączeń i szyfrowanie danych subskrypcji powiadomień;
            </li>
            <li>
              korzysta z dalszych podmiotów przetwarzających wskazanych w
              Polityce prywatności i informuje Restaurację o planowanych
              zmianach, umożliwiając wyrażenie sprzeciwu;
            </li>
            <li>
              pomaga Restauracji w realizacji praw osób, których dane dotyczą,
              oraz obowiązków z art. 32–36 RODO;
            </li>
            <li>
              zgłasza Restauracji naruszenie ochrony danych bez zbędnej zwłoki,
              nie później niż w ciągu 48 godzin od jego wykrycia;
            </li>
            <li>
              usuwa powierzone dane w ciągu 30 dni od zakończenia Okresu
              testowego albo od zgłoszenia przez Restaurację zakończenia
              korzystania z Kairos, chyba że prawo wymaga ich dalszego
              przechowywania;
            </li>
            <li>
              udostępnia Restauracji informacje niezbędne do wykazania
              spełnienia tych obowiązków i umożliwia audyt po uprzednim
              uzgodnieniu terminu.
            </li>
          </ul>
        </li>
      </ol>

      <h2>§ 9. Reklamacje</h2>
      <ol>
        <li>
          Reklamacje dotyczące działania Kairos można zgłaszać na adres e-mail
          Usługodawcy, opisując problem i podając adres e-mail Konta.
        </li>
        <li>Usługodawca odpowiada na reklamację w ciągu 14 dni.</li>
      </ol>

      <h2>§ 10. Postanowienia końcowe</h2>
      <ol>
        <li>
          Zasady przetwarzania danych osobowych Użytkowników opisuje{" "}
          <a href="/polityka-prywatnosci">Polityka prywatności</a>.
        </li>
        <li>
          Usługodawca może zmienić Regulamin z ważnych powodów, w szczególności
          zmiany przepisów lub funkcji Kairos, informując Restauracje z co
          najmniej 7-dniowym wyprzedzeniem. Restauracja, która nie akceptuje
          zmian, może zakończyć korzystanie z Kairos.
        </li>
        <li>
          W sprawach nieuregulowanych stosuje się prawo polskie. Postanowienia
          Regulaminu nie ograniczają praw, które przysługują na podstawie
          bezwzględnie obowiązujących przepisów.
        </li>
      </ol>
    </LegalDocument>
  );
}
