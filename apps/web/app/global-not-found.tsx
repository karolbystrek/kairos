import "@/styles/panel.css";

import { NotFoundNotice } from "@/components/not-found-notice";

// Unmatched URLs belong to neither root layout.
export default function GlobalNotFound() {
  return (
    <html lang="pl">
      <body className="flex min-h-dvh flex-col bg-background text-foreground antialiased">
        <main className="panel-page mx-auto flex w-full flex-1 flex-col max-w-[var(--content-panel)] px-5 pb-24 pt-5 sm:px-7 sm:pb-10 sm:pt-6 lg:px-10">
          <NotFoundNotice />
        </main>
      </body>
    </html>
  );
}
