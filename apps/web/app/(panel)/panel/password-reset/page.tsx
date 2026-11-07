import type { Metadata } from "next";

import { PasswordResetForm } from "@/components/password-reset-form";
import { NOT_INDEXED } from "@/src/seo/metadata";

export const metadata: Metadata = {
  title: "Zresetuj hasło",
  robots: NOT_INDEXED,
};

export default function PasswordReset() {
  return <PasswordResetForm />;
}
