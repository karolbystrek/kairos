import { Alert, Button, Input, Label, TextField, Tooltip } from "@heroui/react";
import { Check as CheckIcon, Copy as CopyIcon } from "lucide-react";
import { useEffect, useState } from "react";

export type PendingOneTimeSecret = {
  title: string;
  description: string;
  value: string;
  afterConfirmed?: () => void;
};

export function OneTimeSecret({
  secret,
  onConfirmed,
}: {
  secret: PendingOneTimeSecret;
  onConfirmed: () => void;
}) {
  const [copyStatus, setCopyStatus] = useState<"idle" | "copied" | "failed">(
    "idle",
  );

  useEffect(() => {
    if (copyStatus !== "copied") return;

    const timeout = window.setTimeout(() => setCopyStatus("idle"), 2500);

    return () => window.clearTimeout(timeout);
  }, [copyStatus]);

  async function copySecret() {
    try {
      await navigator.clipboard.writeText(secret.value);
      setCopyStatus("copied");
    } catch {
      setCopyStatus("failed");
    }
  }

  return (
    <section className="mx-auto flex w-full max-w-3xl flex-col gap-5">
      <div>
        <h2 className="page-title">{secret.title}</h2>
        <p className="mt-2 secondary-text">{secret.description}</p>
      </div>

      <Alert status="warning">
        <Alert.Indicator />
        <Alert.Content>
          <Alert.Title>Zapisz sekret</Alert.Title>
          <Alert.Description>
            Po potwierdzeniu nie pokażemy go ponownie.
          </Alert.Description>
        </Alert.Content>
      </Alert>

      <TextField fullWidth isReadOnly value={secret.value}>
        <Label>Sekret</Label>
        <div className="relative">
          <Input className="pr-12 font-mono" />
          <Tooltip delay={500}>
            <Tooltip.Trigger>
              <Button
                isIconOnly
                aria-label={
                  copyStatus === "copied"
                    ? "Sekret skopiowany"
                    : "Skopiuj sekret"
                }
                className="absolute right-1 top-1/2 -translate-y-1/2 rounded-md"
                size="sm"
                variant="tertiary"
                onPress={copySecret}
              >
                {copyStatus === "copied" ? (
                  <CheckIcon size={18} />
                ) : (
                  <CopyIcon size={18} />
                )}
              </Button>
            </Tooltip.Trigger>
            <Tooltip.Content>
              {copyStatus === "copied" ? "Skopiowano" : "Skopiuj sekret"}
            </Tooltip.Content>
          </Tooltip>
        </div>
      </TextField>

      {copyStatus === "failed" && (
        <p className="text-sm text-danger">
          Nie udało się skopiować do schowka. Zaznacz i skopiuj sekret ręcznie.
        </p>
      )}

      <div className="flex justify-end">
        <Button onPress={onConfirmed}>
          <CheckIcon size={18} />
          Zapisano sekret
        </Button>
      </div>
    </section>
  );
}
