import type { Metadata } from "next";

import { StaffPanel } from "@/components/staff-panel";
import { NOT_INDEXED } from "@/src/seo/metadata";

export const metadata: Metadata = { title: "Zaloguj się", robots: NOT_INDEXED };

export default async function Login({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const { "password-changed": passwordChanged } = await searchParams;

  return (
    <StaffPanel
      passwordChanged={passwordChanged !== undefined}
      screen="login"
    />
  );
}
