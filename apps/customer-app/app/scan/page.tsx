import type { Metadata } from "next";

import { OrderScanner } from "./order-scanner";

import { NOT_INDEXED } from "@/src/seo/metadata";

export const metadata: Metadata = {
  title: "Zeskanuj kod QR",
  robots: NOT_INDEXED,
};

export default function ScanOrderPage() {
  return <OrderScanner />;
}
