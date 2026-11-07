import type { Metadata } from "next";

import { StaffPanel } from "@/components/staff-panel";
import { NOT_INDEXED } from "@/src/seo/metadata";

export const metadata: Metadata = { robots: NOT_INDEXED };

// A shared layout keeps the panel mounted while its tabs change the URL.
export default function WorkspaceLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <>
      <StaffPanel />
      {children}
    </>
  );
}
