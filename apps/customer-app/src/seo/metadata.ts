import type { Metadata } from "next";

export const PREVIEW_IMAGE = {
  alt: "Kairos — śledzenie zamówień w restauracji",
  size: { width: 1200, height: 630 },
};

// Pages that set openGraph replace the inherited file-based image, so it is
// referenced explicitly here.
export const SHARED_OPEN_GRAPH = {
  type: "website",
  locale: "pl_PL",
  siteName: "Kairos",
  images: {
    url: "/opengraph-image",
    alt: PREVIEW_IMAGE.alt,
    ...PREVIEW_IMAGE.size,
  },
} satisfies Metadata["openGraph"];

export const NOT_INDEXED: Metadata["robots"] = {
  index: false,
  follow: false,
};

export function publicPageMetadata({
  title,
  description,
  path,
}: {
  title: string;
  description: string;
  path: string;
}): Metadata {
  return {
    title,
    description,
    alternates: { canonical: path },
    openGraph: { ...SHARED_OPEN_GRAPH, title, description, url: path },
  };
}
