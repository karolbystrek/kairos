import { notFound } from "next/navigation";

import { parsePanelPath } from "@/src/routes";

// The workspace layout renders the panel from the URL; other paths are not pages.
export default async function Workspace({
  params,
}: {
  params: Promise<{ workspace: string[] }>;
}) {
  const { workspace } = await params;

  if (!parsePanelPath(`/panel/${workspace.join("/")}`)) notFound();

  return null;
}
