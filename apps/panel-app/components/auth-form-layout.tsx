import type { ReactNode } from "react";

import { BrandWordmark } from "@/components/brand-wordmark";

export function AuthFormLayout({
  title,
  children,
  footer,
}: {
  title: string;
  children: ReactNode;
  footer: ReactNode;
}) {
  return (
    <div className="auth-form-layout">
      <div className="mx-auto my-auto flex w-full max-w-md flex-col gap-6">
        <BrandWordmark />
        <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
        <div className="auth-form-content">
          <div>{children}</div>
          <p className="text-center text-sm text-muted">{footer}</p>
        </div>
      </div>
    </div>
  );
}
