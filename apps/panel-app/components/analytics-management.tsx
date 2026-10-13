"use client";

import type { ReactNode } from "react";
import type { CurrentAccount } from "@/src/api/authentication";

import { Alert, Button, Input, Label, ListBox, TextField } from "@heroui/react";
import { ArrowsClockwiseIcon } from "@phosphor-icons/react";
import { useState } from "react";
import useSWR, { useSWRConfig } from "swr";
import { Bar, BarChart, CartesianGrid, Cell, XAxis, YAxis } from "recharts";

import { LoadingIndicator } from "@/components/loading-indicator";
import { ChartContainer, ChartTooltip } from "@/components/chart";
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
  prominence,
  detail,
}: {
  label: string;
  value: string;
  prominence?: "primary" | "large";
  detail?: string;
}) {
  return (
    <div className="min-w-0 rounded-lg bg-surface-secondary p-4">
      <dt className="text-sm secondary-text">{label}</dt>
      <dd
        className={`${prominence === "primary" ? "text-5xl" : prominence === "large" ? "text-3xl" : "text-2xl"} mt-2 break-words font-semibold leading-tight tracking-tight tabular-nums`}
      >
        {value}
      </dd>
      {detail && <dd className="mt-2 text-sm secondary-text">{detail}</dd>}
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

type OrderChartDay = {
  label: string;
  value: number | null;
  transitions: Record<string, number>;
  partial?: boolean;
};

function OrderChart({
  data,
  byLocation = false,
}: {
  data: OrderChartDay[];
  byLocation?: boolean;
}) {
  const [trigger, setTrigger] = useState<"hover" | "click">("hover");

  return (
    <ChartContainer
      className="w-full min-w-0 aspect-auto"
      config={{ orders: { label: "Zamówienia", color: "var(--accent)" } }}
      style={{ height: byLocation ? Math.max(200, data.length * 44) : 260 }}
      onPointerDown={(event) =>
        setTrigger(event.pointerType === "touch" ? "click" : "hover")
      }
      onPointerMove={(event) => {
        if (event.pointerType === "mouse") setTrigger("hover");
      }}
    >
      <BarChart
        accessibilityLayer
        aria-label={
          byLocation
            ? "Zakończone zamówienia według lokalu"
            : "Przyjęte zamówienia według dnia"
        }
        data={data}
        layout={byLocation ? "vertical" : "horizontal"}
        margin={{ top: 8, right: 8, bottom: 0, left: 0 }}
      >
        <CartesianGrid horizontal={!byLocation} vertical={byLocation} />
        <XAxis
          allowDecimals={false}
          axisLine={false}
          dataKey={byLocation ? undefined : "label"}
          minTickGap={28}
          tickFormatter={
            byLocation
              ? undefined
              : (value: string) =>
                  dayFormat.format(new Date(`${value}T00:00:00Z`))
          }
          tickLine={false}
          type={byLocation ? "number" : "category"}
        />
        <YAxis
          allowDecimals={false}
          axisLine={false}
          dataKey={byLocation ? "label" : undefined}
          tickFormatter={
            byLocation
              ? (value: string) =>
                  value.length > 14 ? `${value.slice(0, 14)}…` : value
              : undefined
          }
          tickLine={false}
          type={byLocation ? "category" : "number"}
          width={byLocation ? 100 : 36}
        />
        <ChartTooltip
          content={({ active, payload }) => {
            const day: OrderChartDay | undefined = payload?.[0]?.payload;

            if (!active || !day || day.value === null) return null;

            return (
              <div className="max-w-[min(18rem,80vw)] rounded-lg border border-separator bg-surface px-3 py-2 text-sm shadow-md">
                <p className="break-words font-medium">
                  {byLocation
                    ? day.label
                    : dayFormat.format(new Date(`${day.label}T00:00:00Z`))}
                  {day.partial && " · Dzień w toku"}
                </p>
                <dl className="mt-2 grid grid-cols-[1fr_auto] gap-x-4 gap-y-1">
                  {statuses.map(([key, label]) => (
                    <div key={key} className="contents">
                      <dt className="secondary-text">{label}</dt>
                      <dd className="tabular-nums">
                        {numberFormat.format(day.transitions[key] ?? 0)}
                      </dd>
                    </div>
                  ))}
                </dl>
              </div>
            );
          }}
          cursor={{ fill: "var(--surface-secondary)" }}
          trigger={trigger}
        />
        <Bar
          dataKey="value"
          fill="var(--color-orders)"
          isAnimationActive={false}
          maxBarSize={48}
          radius={4}
        >
          {data.map((day, index) => (
            <Cell
              key={index}
              fillOpacity={day.partial ? 0.45 : 1}
              stroke={day.partial ? "var(--accent)" : undefined}
              strokeDasharray={day.partial ? "3 3" : undefined}
            />
          ))}
        </Bar>
      </BarChart>
    </ChartContainer>
  );
}

function ReportView({
  report,
  platform,
}: {
  report: AnalyticsReport;
  platform: boolean;
}) {
  const collectedFrom =
    report.firstRecordedHour === null
      ? null
      : localDate(new Date(report.firstRecordedHour), report.zone);
  const count = (key: string) =>
    numberFormat.format(report.transitions[key] ?? 0);

  return (
    <div className="flex min-w-0 flex-col gap-6">
      <dl className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <Metric
          label="Zakończone zamówienia"
          prominence="primary"
          value={count("COMPLETED")}
        />
        <Metric
          label="Średni czas przygotowania"
          prominence="large"
          value={formatDuration(report.preparation.meanMs)}
        />
        <Metric
          label="Średni czas do odbioru"
          prominence="large"
          value={formatDuration(report.collection.meanMs)}
        />
      </dl>
      <dl className="grid grid-cols-3 gap-3">
        {statuses
          .filter(([key]) => key !== "COMPLETED")
          .map(([key, label]) => (
            <Metric key={key} label={label} value={count(key)} />
          ))}
      </dl>
      <Section title="Przyjęte zamówienia dziennie">
        <OrderChart
          data={report.days.map((day) => ({
            label: day.date,
            value:
              collectedFrom === null || day.date < collectedFrom
                ? null
                : (day.transitions.IN_PREPARATION ?? 0),
            transitions: day.transitions,
            partial: day.date === report.partialDay,
          }))}
        />
        {report.partialDay && (
          <p className="flex items-center gap-2 text-xs secondary-text">
            <span
              aria-hidden="true"
              className="h-3 w-3 rounded-sm border border-dashed border-accent bg-accent/45"
            />
            Dzień w toku
          </p>
        )}
      </Section>
      {report.locations && report.locations.length > 0 && (
        <Section title="Zakończone zamówienia według lokalu">
          <OrderChart
            byLocation
            data={report.locations.map((location) => ({
              label:
                location.name ??
                `Usunięta lokalizacja (${location.locationId.slice(0, 8)})`,
              value: location.transitions.COMPLETED ?? 0,
              transitions: location.transitions,
            }))}
          />
        </Section>
      )}
      <Section title="Opinie Google">
        <dl className="grid grid-cols-2 gap-3">
          {reviewActions.map(([key, label]) => (
            <Metric
              key={key}
              label={label}
              value={numberFormat.format(report.actions[key] ?? 0)}
            />
          ))}
        </dl>
      </Section>
      {platform && (
        <Section title="Korzystanie z panelu">
          <dl className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            {featureActions.map(([key, label]) => (
              <Metric
                key={key}
                label={label}
                value={numberFormat.format(report.actions[key] ?? 0)}
              />
            ))}
          </dl>
        </Section>
      )}
    </div>
  );
}

function ReportArea({
  platform = false,
  range,
  report,
  error,
  onRetry,
}: {
  platform?: boolean;
  range: RangeState;
  report?: AnalyticsReport;
  error?: unknown;
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
  if (!report) {
    return <LoadingIndicator label="Wczytywanie analityki" />;
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
      <ReportView platform={platform} report={report} />
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
  const { data, error, mutate } = useSWR(
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
    <div className="flex min-w-0 flex-col gap-6">
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
      <LoadingIndicator label="Wczytywanie stanu systemu" />
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
          <dl className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {health.workers.map((worker) => (
              <Metric
                key={worker.worker}
                detail={`Najstarsze: ${worker.pending > 0 ? formatAge(worker.oldestAgeSeconds) : "—"}`}
                label={workerLabels[worker.worker] ?? worker.worker}
                value={`${numberFormat.format(worker.pending)} oczekujących`}
              />
            ))}
          </dl>
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
      <div className="flex min-w-0 flex-col gap-6">
        <header>
          <h2 className="text-2xl font-semibold tracking-tight">
            Analityka platformy
          </h2>
        </header>
        <RangeFilter state={range} />
        <ReportArea
          platform
          error={report.error}
          range={range}
          report={report.data}
          onRetry={() => void report.mutate()}
        />
      </div>
    </div>
  );
}
