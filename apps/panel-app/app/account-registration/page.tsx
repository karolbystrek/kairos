import type { Metadata } from "next";

import { AccountRegistration } from "@/components/account-registration";
import { NOT_INDEXED } from "@/src/seo/metadata";

export const metadata: Metadata = {
  title: "Dołącz do zespołu",
  robots: NOT_INDEXED,
};

export default function AccountRegistrationPage() {
  return <AccountRegistration />;
}
