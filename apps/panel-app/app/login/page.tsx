import type { Metadata } from "next";

import { StaffPanel } from "@/components/staff-panel";
import { NOT_INDEXED } from "@/src/seo/metadata";

export const metadata: Metadata = { title: "Zaloguj się", robots: NOT_INDEXED };

export default function Login() {
  return <StaffPanel screen="login" />;
}
