export function BrandWordmark({ className }: { className?: string }) {
  return (
    <p
      className={["app-name app-name--hero text-center", className]
        .filter(Boolean)
        .join(" ")}
    >
      Kairos
    </p>
  );
}
