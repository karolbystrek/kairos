import type { Metadata } from "next";

import { RegistrationForm } from "@/components/registration-form";
import { NOT_INDEXED } from "@/src/seo/metadata";

export const metadata: Metadata = {
  title: "Rejestracja restauracji",
  robots: NOT_INDEXED,
};

export default function TenantRegistrationPage() {
  return <RegistrationForm />;
}
