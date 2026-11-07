export default function PanelLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <main className="panel-page mx-auto flex w-full flex-1 flex-col max-w-[var(--content-panel)] px-5 pb-24 pt-5 sm:px-7 sm:pb-10 sm:pt-6 lg:px-10">
      {children}
    </main>
  );
}
