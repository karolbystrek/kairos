import type { Metadata } from "next";

import { StaffPanel } from "@/components/staff-panel";

export const metadata: Metadata = { title: "Zaloguj się" };

export default function Login() {
  return <StaffPanel screen="login" />;
}
