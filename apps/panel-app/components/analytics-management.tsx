"use client";

import type { ReactNode } from "react";
import type { CurrentAccount } from "@/src/api/authentication";

import {
  Alert,
  Button,
  Input,
  Label,
  ListBox,
  Spinner,
  TextField,
} from "@heroui/react";
import { ArrowsClockwiseIcon } from "@phosphor-icons/react";
import { useState } from "react";
import useSWR, { useSWRConfig } from "swr";

import { FormSelect } from "@/components/form-controls";
import { ApiError } from "@/src/api/api-fetch";
import {
  getPlatformHealth,
  getPlatformReport,
  getRestaurantReport,
  type AnalyticsReport,
  type PlatformHealth,
} from "@/src/api/analytics";
import {
  staffAnalyticsKey,
  staffLocationsKey,
  staffPlatformHealthKey,
} from "@/src/api/cache-keys";
import { listLocations, sortLocations } from "@/src/api/locations";
import {
  browserTimeZone,
  localDate,
  reportRange,
  type RangePreset,
  type ReportRange,
} from "@/src/api/report-range";

const statuses = [
  ["IN_PREPARATION", "Przyjęte"],
  ["READY", "Gotowe"],
  ["COMPLETED", "Zakończone"],
  ["CANCELED", "Anulowane"],
] as const;

const featureActions = [
  ["queue_opened", "Otwarcia kolejki zamówień"],
  ["order_creation_form_opened", "Otwarcia formularza zamówienia"],
  ["menu_editor_opened", "Otwarcia edytora menu"],
  ["integration_settings_opened", "Otwarcia ustawień integracji"],
] as const;

const reviewActions = [
  ["review_link_clicked", "Kliknięcia linku do opinii Google"],
  ["review_prompt_dismissed", "Zamknięcia prośby o opinię"],
] as const;

const workerLabels: Record<string, string> = {
  analytics: "Analityka zamówień",
  webhook_fanout: "Przygotowanie webhooków",
  webhook_delivery: "Dostarczanie webhooków",
  push_fanout: "Przygotowanie powiadomień push",
  push_delivery: "Dostarczanie powiadomień push",
};

const numberFormat = new Intl.NumberFormat("pl-PL");
const timeFormat = new Intl.DateTimeFormat("pl-PL", {
  dateStyle: "short",
  timeStyle: "medium",
});
const dayFormat = new Intl.DateTimeFormat("pl-PL", {
  weekday: "short",
  day: "numeric",
  month: "short",
  timeZone: "UTC",
});

function shouldRetryOnError(error: Error): boolean {
  return !(
    error instanceof ApiError &&
    error.status >= 400 &&
    error.status < 500
  );
}

function isAccessDenied(error: unknown): boolean {
  return error instanceof ApiError && error.status === 403;
}

function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message;

  return "Nie udało się wczytać danych. Sprawdź połączenie i spróbuj ponownie.";
}

function formatDuration(milliseconds: number | null): string {
  if (milliseconds === null) return "—";
  const seconds = Math.round(milliseconds / 1000);
  const minutes = Math.floor(seconds / 60);

  return minutes > 0 ? `${minutes} min ${seconds % 60} s` : `${seconds} s`;
}

function formatAge(seconds: number): string {
  if (seconds < 60) return `${Math.round(seconds)} s`;
  if (seconds < 3600) return `${Math.round(seconds / 60)} min`;

  return `${Math.round(seconds / 3600)} godz.`;
}

function useReportRange() {
  const [zone] = useState(browserTimeZone);
  const [preset, setPreset] = useState<RangePreset>("7");
  const [custom, setCustom] = useState<Partial<ReportRange>>({});
  const today = localDate(new Date(), zone);

  return {
    zone,
    preset,
    setPreset,
    custom,
    setCustom,
    today,
    range: reportRange(preset, custom, today),
  };
}

type RangeState = ReturnType<typeof useReportRange>;

function DateInput({
  label,
  max,
  value,
  onChange,
}: {
  label: string;
  max: string;
  value?: string;
  onChange: (value: string) => void;
}) {
  return (
    <TextField
      className="w-full sm:w-auto"
      type="date"
      value={value ?? ""}
      onChange={onChange}
    >
      <Label>{label}</Label>
      <Input max={max} />
    </TextField>
  );
}

function RangeFilter({
  state,
  children,
}: {
  state: RangeState;
  children?: ReactNode;
}) {
  return (
    <div className="flex flex-col gap-2">
      <div className="flex flex-wrap items-end gap-3">
        {children}
        <FormSelect
          className="w-full sm:max-w-[14rem]"
          label="Zakres dat"
          selectedKey={state.preset}
          onSelectionChange={(key) => {
            if (key !== null) state.setPreset(String(key) as RangePreset);
          }}
        >
          <ListBox>
            <ListBox.Item id="7" textValue="Ostatnie 7 dni">
              Ostatnie 7 dni
            </ListBox.Item>
            <ListBox.Item id="30" textValue="Ostatnie 30 dni">
              Ostatnie 30 dni
            </ListBox.Item>
            <ListBox.Item id="custom" textValue="Własny zakres">
              Własny zakres (do 90 dni)
            </ListBox.Item>
          </ListBox>
        </FormSelect>
        {state.preset === "custom" && (
          <>
            <DateInput
              label="Od"
              max={state.today}
              value={state.custom.from}
              onChange={(from) => state.setCustom({ ...state.custom, from })}
            />
            <DateInput
              label="Do"
              max={state.today}
              value={state.custom.to}
              onChange={(to) => state.setCustom({ ...state.custom, to })}
            />
          </>
        )}
      </div>
      {"error" in state.range && (
        <p className="text-sm text-danger" role="alert">
          {state.range.error}
        </p>
      )}
    </div>
  );
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="flex flex-col gap-3">
      <h3 className="text-lg font-semibold tracking-tight">{title}</h3>
      {children}
    </section>
  );
}

function Metric({
  label,
  value,
  detail,
}: {
  label: string;
  value: string;
  detail?: string;
}) {
  return (
    <div className="panel-card px-3 py-2">
      <dt className="text-sm secondary-text">{label}</dt>
      <dd className="text-2xl font-semibold tracking-tight">{value}</dd>
      {detail && <dd className="text-xs secondary-text">{detail}</dd>}
    </div>
  );
}

function StaleNotice({
  error,
  since,
  onRetry,
}: {
  error: unknown;
  since: string;
  onRetry: () => void;
}) {
  return (
    <Alert status="warning">
      <Alert.Indicator />
      <Alert.Content>
        <Alert.Title>Dane mogą być nieaktualne</Alert.Title>
        <Alert.Description>
          {errorMessage(error)} Wyświetlane wyniki pochodzą z{" "}
          {timeFormat.format(new Date(since))}.
        </Alert.Description>
        <div className="mt-3">
          <Button size="sm" variant="secondary" onPress={onRetry}>
            <ArrowsClockwiseIcon aria-hidden="true" size={18} /> Spróbuj
            ponownie
          </Button>
        </div>
      </Alert.Content>
    </Alert>
  );
}

function LoadFailure({
  error,
  message = errorMessage(error),
  title,
  onRetry,
}: {
  error: unknown;
  message?: string;
  title: string;
  onRetry: () => void;
}) {
  return (
    <Alert status="danger">
      <Alert.Indicator />
      <Alert.Content>
        <Alert.Title>{title}</Alert.Title>
        <Alert.Description>{message}</Alert.Description>
        {!isAccessDenied(error) && (
          <div className="mt-3">
            <Button size="sm" variant="danger" onPress={onRetry}>
              <ArrowsClockwiseIcon aria-hidden="true" size={18} /> Spróbuj
              ponownie
            </Button>
          </div>
        )}
      </Alert.Content>
    </Alert>
  );
}

function ReportView({ report }: { report: AnalyticsReport }) {
  if (report.firstRecordedHour === null) {
    return (
      <Alert status="default">
        <Alert.Indicator />
        <Alert.Content>
          <Alert.Title>Brak zebranych danych</Alert.Title>
          <Alert.Description>
            Zbieranie danych rozpoczęło się wraz z wdrożeniem tej funkcji i nie
            obejmuje wcześniejszej historii.
          </Alert.Description>
        </Alert.Content>
      </Alert>
    );
  }
  const collectedFrom = localDate(
    new Date(report.firstRecordedHour),
    report.zone,
  );
  const count = (values: Record<string, number>, key: string) =>
    values[key] ?? 0;
  const busiestDay = Math.max(
    1,
    ...report.days.map((day) => count(day.transitions, "IN_PREPARATION")),
  );
  const hasActivity = statuses.some(([key]) => count(report.transitions, key));

  return (
    <div className="flex flex-col gap-8">
      {collectedFrom > report.from && (
        <p className="text-sm secondary-text">
          Dane są zbierane od {collectedFrom}; wcześniejsze dni oznaczono jako
          brak danych, a nie jako zero.
        </p>
      )}
      {!hasActivity && (
        <p className="text-sm secondary-text">
          Brak zmian statusu zamówień w wybranym okresie.
        </p>
      )}

      <Section title="Zamówienia">
        <dl className="grid grid-cols-2 gap-3 lg:grid-cols-4">
          {statuses.map(([key, label]) => (
            <Metric
              key={key}
              label={label}
              value={numberFormat.format(count(report.transitions, key))}
            />
          ))}
        </dl>
        <p className="text-xs secondary-text">
          Liczby zmian statusu w okresie. Przyjęte i zakończone w tym samym
          okresie to różne zamówienia, więc ich iloraz nie jest wskaźnikiem
          realizacji.
        </p>
        <dl className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <Metric
            detail={`${numberFormat.format(report.preparation.samples)} próbek`}
            label="Średni czas przygotowania"
            value={formatDuration(report.preparation.meanMs)}
          />
          <Metric
            detail={`${numberFormat.format(report.collection.samples)} próbek`}
            label="Średni czas do odbioru"
            value={formatDuration(report.collection.meanMs)}
          />
        </dl>
      </Section>

      <Section title="Dzienny przebieg">
        <div className="overflow-x-auto">
          <table className="w-full min-w-[34rem] text-sm">
            <caption className="sr-only">
              Zmiany statusu zamówień według dnia w strefie {report.zone}
            </caption>
            <thead>
              <tr className="text-left secondary-text">
                <th className="py-2 pr-3 font-medium" scope="col">
                  Dzień
                </th>
                <th className="py-2 pr-3 font-medium" scope="col">
                  Przyjęte
                </th>
                {statuses.slice(1).map(([key, label]) => (
                  <th
                    key={key}
                    className="py-2 pr-3 text-right font-medium"
                    scope="col"
                  >
                    {label}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {report.days.map((day) => {
                const missing = day.date < collectedFrom;
                const received = count(day.transitions, "IN_PREPARATION");

                return (
                  <tr key={day.date} className="border-t border-separator">
                    <th className="py-2 pr-3 text-left font-normal" scope="row">
                      {dayFormat.format(new Date(`${day.date}T00:00:00Z`))}
                      {day.date === report.partialDay && (
                        <span className="secondary-text"> (dzień w toku)</span>
                      )}
                    </th>
                    {missing ? (
                      <td className="py-2 secondary-text" colSpan={4}>
                        brak danych
                      </td>
                    ) : (
                      <>
                        <td className="py-2 pr-3">
                          <div className="flex items-center gap-2">
                            <span className="w-10 tabular-nums">
                              {numberFormat.format(received)}
                            </span>
                            <span
                              aria-hidden="true"
                              className="h-2 rounded-full bg-accent"
                              style={{
                                width: `${(received / busiestDay) * 8}rem`,
                              }}
                            />
                          </div>
                        </td>
                        {statuses.slice(1).map(([key]) => (
                          <td
                            key={key}
                            className="py-2 pr-3 text-right tabular-nums"
                          >
                            {numberFormat.format(count(day.transitions, key))}
                          </td>
                        ))}
                      </>
                    )}
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </Section>

      {report.locations && report.locations.length > 0 && (
        <Section title="Lokale">
          <ul className="flex flex-col">
            {report.locations.map((location) => (
              <li
                key={location.locationId}
                className="panel-card flex flex-wrap justify-between gap-2 px-3 py-2"
              >
                <span className="font-medium">
                  {location.name ??
                    `Usunięta lokalizacja (${location.locationId.slice(0, 8)})`}
                </span>
                <span className="text-sm tabular-nums secondary-text">
                  {statuses
                    .map(
                      ([key, label]) =>
                        `${label}: ${numberFormat.format(count(location.transitions, key))}`,
                    )
                    .join(" · ")}
                </span>
              </li>
            ))}
          </ul>
        </Section>
      )}

      <Section title="Korzystanie z panelu">
        <dl className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          {featureActions.map(([key, label]) => (
            <Metric
              key={key}
              label={label}
              value={numberFormat.format(count(report.actions, key))}
            />
          ))}
        </dl>
        <p className="text-xs secondary-text">
          Przybliżona liczba działań zgłoszonych przez przeglądarki. Część
          działań może nie zostać zarejestrowana; liczby nie oznaczają liczby
          osób.
        </p>
      </Section>

      <Section title="Opinie Google">
        <dl className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          {reviewActions.map(([key, label]) => (
            <Metric
              key={key}
              label={label}
              value={numberFormat.format(count(report.actions, key))}
            />
          ))}
        </dl>
        <p className="text-xs secondary-text">
          Kliknięcie linku nie potwierdza wystawienia opinii. Zamknięcia
          obejmują tylko wyraźne zamknięcie prośby przez klienta.
        </p>
      </Section>
    </div>
  );
}

function ReportArea({
  range,
  report,
  error,
  isLoading,
  onRetry,
}: {
  range: RangeState;
  report?: AnalyticsReport;
  error?: unknown;
  isLoading: boolean;
  onRetry: () => void;
}) {
  if ("error" in range.range) return null;
  if (error && (!report || isAccessDenied(error))) {
    return (
      <LoadFailure
        error={error}
        title="Nie udało się wczytać analityki"
        onRetry={onRetry}
      />
    );
  }
  if (!report || isLoading) {
    return <Spinner aria-label="Wczytywanie analityki" />;
  }

  return (
    <div className="flex flex-col gap-4">
      {error !== undefined && (
        <StaleNotice
          error={error}
          since={report.generatedAt}
          onRetry={onRetry}
        />
      )}
      <p className="text-xs secondary-text">
        Strefa czasowa: {report.zone}. Godzinowe dane UTC przypisano do dnia
        według początku godziny, więc w strefach z przesunięciem
        niepełnogodzinnym granice dni są przybliżone. Najnowsze zmiany mogą
        pojawić się z kilkusekundowym opóźnieniem.
      </p>
      <ReportView report={report} />
    </div>
  );
}

export function RestaurantAnalytics({
  account,
  onAccessLost,
}: {
  account: CurrentAccount;
  onAccessLost: () => void;
}) {
  const { mutate: mutateCache } = useSWRConfig();
  const isAdministrator = account.tenantRole === "ADMIN";
  const range = useReportRange();
  const [selectedLocationId, setSelectedLocationId] = useState<string>();
  const { data: locations } = useSWR(
    staffLocationsKey(account.accountId),
    listLocations,
    { shouldRetryOnError },
  );
  const locationId = isAdministrator
    ? selectedLocationId
    : account.assignment?.locationId;
  const key =
    "error" in range.range
      ? null
      : staffAnalyticsKey(
          account.accountId,
          "restaurant",
          range.range.from,
          range.range.to,
          range.zone,
          locationId,
        );
  const { data, error, isLoading, mutate } = useSWR(
    key,
    ([, , , , location, from, to, zone]) =>
      getRestaurantReport({
        from,
        to,
        zone,
        locationId: location === "tenant" ? undefined : location,
      }),
    {
      errorRetryCount: 3,
      shouldRetryOnError,
      onError: (failure) => {
        if (!isAccessDenied(failure)) return;
        // A lost role must not keep earlier reports cached under this account.
        void mutateCache(
          (cached) =>
            Array.isArray(cached) &&
            cached[1] === account.accountId &&
            cached[3] === "restaurant",
          undefined,
          { revalidate: false },
        );
        onAccessLost();
      },
    },
  );
  const assignedName = locations?.find(
    (location) => location.id === locationId,
  )?.name;

  return (
    <div className="flex flex-col gap-6">
      <header>
        <h2 className="text-2xl font-semibold tracking-tight">Analityka</h2>
        {!isAdministrator && (
          <p className="text-sm secondary-text">
            {assignedName ?? "Przypisany lokal"}
          </p>
        )}
      </header>
      <RangeFilter state={range}>
        {isAdministrator && (
          <FormSelect
            className="w-full sm:max-w-xs"
            label="Lokal"
            selectedKey={selectedLocationId ?? "all"}
            onSelectionChange={(selected) =>
              setSelectedLocationId(
                selected === "all" || selected === null
                  ? undefined
                  : String(selected),
              )
            }
          >
            <ListBox
              items={[
                { id: "all", name: "Wszystkie lokale" },
                ...sortLocations(locations ?? []),
              ]}
            >
              {(location) => (
                <ListBox.Item id={location.id} textValue={location.name}>
                  {location.name}
                </ListBox.Item>
              )}
            </ListBox>
          </FormSelect>
        )}
      </RangeFilter>
      <ReportArea
        error={error}
        isLoading={isLoading}
        range={range}
        report={data}
        onRetry={() => void mutate()}
      />
    </div>
  );
}

const statusLabels: Record<PlatformHealth["redis"], string> = {
  UP: "Działa",
  DOWN: "Niedostępny",
  UNKNOWN: "Nieznany (brak odpowiedzi na czas)",
};

function HealthView({
  health,
  error,
  onRetry,
}: {
  health?: PlatformHealth;
  error?: unknown;
  onRetry: () => void;
}) {
  if (!health) {
    return error ? (
      <LoadFailure
        error={error}
        message={
          error instanceof ApiError
            ? undefined
            : "API jest nieosiągalne. Przeglądarka nie odróżni awarii API od problemu z siecią."
        }
        title="Stan systemu nieznany"
        onRetry={onRetry}
      />
    ) : (
      <Spinner aria-label="Wczytywanie stanu systemu" />
    );
  }

  return (
    <div className="flex flex-col gap-4">
      {error !== undefined && (
        <StaleNotice
          error={error}
          since={health.observedAt}
          onRetry={onRetry}
        />
      )}
      <p className="text-xs secondary-text">
        Odczyt z {timeFormat.format(new Date(health.observedAt))}. Odświeżanie
        co 30 s; historia dostępności nie jest przechowywana.
      </p>
      <dl className="grid grid-cols-1 gap-3 sm:grid-cols-3">
        <Metric label="API" value={error ? "Nieznany" : "Działa"} />
        <Metric label="PostgreSQL" value={statusLabels[health.database]} />
        <Metric label="Redis" value={statusLabels[health.redis]} />
      </dl>
      {health.backlogObservedAt === null ? (
        <p className="text-sm secondary-text">
          Kolejki zadań: brak pomiaru — stan nieznany.
        </p>
      ) : (
        <>
          {!health.backlogCurrent && (
            <p className="text-sm text-warning" role="status">
              Ostatni pomiar kolejek nie powiódł się; pokazano wartości z{" "}
              {timeFormat.format(new Date(health.backlogObservedAt))}.
            </p>
          )}
          <div className="overflow-x-auto">
            <table className="w-full min-w-[24rem] text-sm">
              <caption className="sr-only">Zaległości zadań w tle</caption>
              <thead>
                <tr className="text-left secondary-text">
                  <th className="py-2 pr-3 font-medium" scope="col">
                    Zadanie
                  </th>
                  <th className="py-2 pr-3 text-right font-medium" scope="col">
                    Oczekujące
                  </th>
                  <th className="py-2 text-right font-medium" scope="col">
                    Najstarsze
                  </th>
                </tr>
              </thead>
              <tbody>
                {health.workers.map((worker) => (
                  <tr key={worker.worker} className="border-t border-separator">
                    <th className="py-2 pr-3 text-left font-normal" scope="row">
                      {workerLabels[worker.worker] ?? worker.worker}
                    </th>
                    <td className="py-2 pr-3 text-right tabular-nums">
                      {numberFormat.format(worker.pending)}
                    </td>
                    <td className="py-2 text-right tabular-nums">
                      {worker.pending > 0
                        ? formatAge(worker.oldestAgeSeconds)
                        : "—"}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="text-xs secondary-text">
            Oczekujące pomiary analityki opisują bieżące opóźnienie
            przetwarzania, a nie dokładny moment kompletności raportów.
          </p>
        </>
      )}
    </div>
  );
}

export function PlatformAnalytics({
  accountId,
  onAccessLost,
}: {
  accountId: string;
  onAccessLost: () => void;
}) {
  const { mutate: mutateCache } = useSWRConfig();
  const range = useReportRange();
  const onError = (error: unknown) => {
    if (!isAccessDenied(error)) return;
    // Revoked authority must not leave cross-tenant data in the client cache.
    void mutateCache(
      (key) =>
        Array.isArray(key) &&
        key[1] === accountId &&
        (key[3] === "platform" || key[2] === "platform-health"),
      undefined,
      { revalidate: false },
    );
    onAccessLost();
  };
  const health = useSWR(staffPlatformHealthKey(accountId), getPlatformHealth, {
    refreshInterval: 30_000,
    shouldRetryOnError,
    onError,
  });
  const key =
    "error" in range.range
      ? null
      : staffAnalyticsKey(
          accountId,
          "platform",
          range.range.from,
          range.range.to,
          range.zone,
        );
  const report = useSWR(
    key,
    ([, , , , , from, to, zone]) => getPlatformReport({ from, to, zone }),
    { errorRetryCount: 3, shouldRetryOnError, onError },
  );

  return (
    <div className="flex flex-col gap-10">
      <Section title="Stan systemu">
        <HealthView
          error={health.error}
          health={isAccessDenied(health.error) ? undefined : health.data}
          onRetry={() => void health.mutate()}
        />
      </Section>
      <div className="flex flex-col gap-6">
        <header>
          <h2 className="text-2xl font-semibold tracking-tight">
            Analityka platformy
          </h2>
          <p className="text-sm secondary-text">
            Sumy ze wszystkich restauracji. Trwałe usunięcie restauracji usuwa
            jej udział.
          </p>
        </header>
        <RangeFilter state={range} />
        <ReportArea
          error={report.error}
          isLoading={report.isLoading}
          range={range}
          report={report.data}
          onRetry={() => void report.mutate()}
        />
      </div>
    </div>
  );
}
