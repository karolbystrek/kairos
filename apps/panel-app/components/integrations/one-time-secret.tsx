import { Alert, Button, Input, Label, Surface, TextField } from "@heroui/react";
import { useState } from "react";

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

  async function copySecret() {
    try {
      await navigator.clipboard.writeText(secret.value);
      setCopyStatus("copied");
    } catch {
      setCopyStatus("failed");
    }
  }

  return (
    <Surface className="mx-auto flex w-full max-w-3xl flex-col gap-6 rounded-3xl p-6 sm:p-8">
      <div>
        <p className="text-sm font-medium text-warning">One-time secret</p>
        <h2 className="text-2xl font-semibold">{secret.title}</h2>
        <p className="mt-2 text-muted">{secret.description}</p>
      </div>

      <Alert status="warning">
        <Alert.Indicator />
        <Alert.Content>
          <Alert.Title>Copy this secret now</Alert.Title>
          <Alert.Description>
            Kairos will not show it again. Integration management stays locked
            until you confirm that you saved it.
          </Alert.Description>
        </Alert.Content>
      </Alert>

      <TextField fullWidth isReadOnly value={secret.value}>
        <Label>Secret</Label>
        <Input className="font-mono" />
      </TextField>

      {copyStatus === "copied" && (
        <p className="text-sm text-success">Secret copied to the clipboard.</p>
      )}
      {copyStatus === "failed" && (
        <p className="text-sm text-danger">
          Clipboard access failed. Select and copy the secret manually.
        </p>
      )}

      <div className="flex flex-wrap gap-3">
        <Button variant="secondary" onPress={copySecret}>
          Copy secret
        </Button>
        <Button onPress={onConfirmed}>I copied it</Button>
      </div>
    </Surface>
  );
}
