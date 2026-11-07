export function StepProgress({ step, total }: { step: number; total: number }) {
  return (
    <div
      aria-label="Postęp"
      aria-valuemax={total}
      aria-valuemin={1}
      aria-valuenow={step}
      aria-valuetext={`Krok ${step} z ${total}`}
      className="step-progress"
      role="progressbar"
    >
      {Array.from({ length: total }, (_, index) => (
        <span
          key={index}
          className="step-progress__segment"
          data-lit={index < step || undefined}
        />
      ))}
    </div>
  );
}
