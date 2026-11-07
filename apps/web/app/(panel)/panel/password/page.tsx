import type { Metadata } from "next";

import { StaffPanel } from "@/components/staff-panel";
import { NOT_INDEXED } from "@/src/seo/metadata";

export const metadata: Metadata = { title: "Zmień hasło", robots: NOT_INDEXED };

export default function PasswordChange() {
  return <StaffPanel screen="password" />;
}
