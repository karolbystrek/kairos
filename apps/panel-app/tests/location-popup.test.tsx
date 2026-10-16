import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";

import { apiCalls, body, fakeApi, location, renderWithSWR, status } from "./helpers";

import { LocationCreationModal } from "@/components/location-creation-modal";
import { LocationManagement } from "@/components/location-management";

const ids = { a: "00000000-0000-4000-8000-00000000000a", b: "00000000-0000-4000-8000-00000000000b" };
let locations: ReturnType<typeof location>[];
let routes: Parameters<typeof fakeApi>[0];
let calls: ReturnType<typeof fakeApi>;

beforeEach(() => {
  vi.spyOn(console, "error").mockImplementation(() => {});
  locations = [location(ids.a, "Location A", { googleReviewUrl: "https://g.page/r/a/review" })];
  routes = { "GET /api/locations/v1": () => locations };
  calls = fakeApi(new Proxy({}, { get: (_, key: string) => routes[key] }));
});

const textbox = (name: string) => screen.queryByRole("textbox", { name }) as HTMLInputElement | null;
const writes = () => apiCalls(calls).filter((call) => !call.startsWith("GET"));
// Proceed and Create share a position, so a submit right after a step change is ignored.
const settle = () => new Promise((resolve) => setTimeout(resolve, 401));

it("dismissed location drafts cannot carry into a newly created location", async () => {
  const created = location(ids.b, "Location B", { googleReviewUrl: "https://g.page/r/b/review" });

  routes["POST /api/locations/v1"] = () => {
    locations = [...locations, created];

    return created;
  };
  renderWithSWR(<LocationManagement accountId="staff" onEditMenu={() => {}} onViewOrders={() => {}} />);
  const user = userEvent.setup();

  await user.click(await screen.findByRole("button", { name: "Pokaż lokal Location A, włączony" }));
  await user.clear(textbox("Nazwa lokalu")!);
  await user.type(textbox("Nazwa lokalu")!, "Draft A");
  await user.clear(textbox("Link do opinii Google")!);
  await user.type(textbox("Link do opinii Google")!, "https://g.page/r/draft-a/review");
  await user.keyboard("{Escape}");
  await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());

  await user.click(screen.getAllByRole("button", { name: "Nowy lokal" }).find((element) => element.tagName === "BUTTON")!);
  await user.type(textbox("Nazwa lokalu")!, "Location B");
  await user.click(screen.getByRole("button", { name: "Dalej" }));
  await user.type(textbox("Link do opinii Google (opcjonalnie)")!, "https://g.page/r/b/review");
  await settle();
  await user.click(screen.getByRole("button", { name: "Utwórz lokal" }));

  const details = await screen.findByRole("dialog", { name: "Lokal Location B" });

  expect((within(details).getByRole("textbox", { name: "Nazwa lokalu" }) as HTMLInputElement).value).toBe("Location B");
  expect((within(details).getByRole("textbox", { name: "Link do opinii Google" }) as HTMLInputElement).value).toBe("https://g.page/r/b/review");
});

it("location creation keeps step input and creates only after the last step", async () => {
  routes["POST /api/locations/v1"] = status(409);
  renderWithSWR(<LocationCreationModal isOpen isRequired accountId="staff" onCreated={() => {}} onOpenChange={() => {}} />);
  const user = userEvent.setup();

  // The browser's required check stops the button; submit directly to reach the component's validation.
  fireEvent.submit(textbox("Nazwa lokalu")!.closest("form")!);
  expect(await screen.findByText("Podaj nazwę.")).toBeTruthy();
  await user.type(textbox("Nazwa lokalu")!, "Bistro");
  await user.click(screen.getByRole("button", { name: "Dalej" }));
  expect(textbox("Nazwa lokalu")).toBeNull();
  await user.type(textbox("Link do opinii Google (opcjonalnie)")!, "https://g.page/r/bistro/review");
  await user.click(screen.getByRole("button", { name: "Wstecz" }));
  expect(textbox("Nazwa lokalu")!.value).toBe("Bistro");
  await user.click(screen.getByRole("button", { name: "Dalej" }));
  expect(textbox("Link do opinii Google (opcjonalnie)")!.value).toBe("https://g.page/r/bistro/review");
  await user.click(screen.getByRole("button", { name: "Utwórz lokal" }));
  expect(writes()).toEqual([]);
  await settle();
  await user.click(screen.getByRole("button", { name: "Utwórz lokal" }));
  await waitFor(() => expect(writes()).toEqual(["POST /api/locations/v1"]));
  expect(body(calls, "/api/locations/v1")).toMatchObject({ name: "Bistro", googleReviewUrl: "https://g.page/r/bistro/review" });
  expect(await screen.findByText("Lokal o tej nazwie już istnieje.")).toBeTruthy();
});

it("location Save sends only changed fields and keeps the draft when one fails", async () => {
  routes[`PUT /api/locations/v1/${ids.a}/reviews`] = status(500);
  renderWithSWR(<LocationManagement accountId="staff" onEditMenu={() => {}} onViewOrders={() => {}} />);
  const user = userEvent.setup();

  await user.click(await screen.findByRole("button", { name: "Pokaż lokal Location A, włączony" }));
  await user.clear(textbox("Link do opinii Google")!);
  await user.type(textbox("Link do opinii Google")!, "https://g.page/r/draft/review");
  await user.click(screen.getByRole("button", { name: "Zapisz" }));
  await waitFor(() => expect(writes()).toEqual([`PUT /api/locations/v1/${ids.a}/reviews`]));
  expect(textbox("Link do opinii Google")!.value).toBe("https://g.page/r/draft/review");
});
