import type { ReactNode } from "react";

import { Link } from "@heroui/react";

import { legalOperator } from "@/src/legal/operator";
import { paths } from "@/src/routes";

export function LegalDocument({
  title,
  homeHref = paths.home,
  className = "mx-auto w-full max-w-2xl px-5 py-10 sm:px-6",
  children,
}: {
  title: string;
  homeHref?: string;
  className?: string;
  children: ReactNode;
}) {
  return (
    <article className={`legal-document ${className}`}>
      <Link className="app-name min-h-11 text-2xl" href={homeHref}>
        Kairos
      </Link>
      <h1 className="mt-6 text-3xl font-semibold tracking-tight">{title}</h1>
      <p className="mt-2 text-sm text-muted">
        Obowiązuje od: {legalOperator.effectiveDate}
      </p>
      <div className="legal-document-body mt-8">{children}</div>
    </article>
  );
}
