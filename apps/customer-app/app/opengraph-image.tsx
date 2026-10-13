import { readFile } from "node:fs/promises";
import { join } from "node:path";

import { ImageResponse } from "next/og";

import { customerAppUrl } from "@/src/config/customer-app-url";
import { PREVIEW_IMAGE } from "@/src/seo/metadata";

export const alt = PREVIEW_IMAGE.alt;
export const size = PREVIEW_IMAGE.size;
export const contentType = "image/png";

export default async function OpenGraphImage() {
  const lockup = await readFile(
    join(process.cwd(), "public/icons/kairos-lockup.png"),
  );

  return new ImageResponse(
    (
      <div
        style={{
          display: "flex",
          flexDirection: "column",
          justifyContent: "space-between",
          width: "100%",
          height: "100%",
          padding: 80,
          background: "#ffffff",
          color: "#1d1d1f",
        }}
      >
        {/* eslint-disable-next-line jsx-a11y/alt-text */}
        <img
          height={120}
          src={`data:image/png;base64,${lockup.toString("base64")}`}
          width={311}
        />
        <div style={{ display: "flex", flexDirection: "column", gap: 24 }}>
          <span style={{ fontSize: 72, lineHeight: 1.1 }}>
            Śledź swoje zamówienie w restauracji
          </span>
          <span style={{ fontSize: 36, color: "#6e6e73" }}>
            Zeskanuj kod QR i sprawdzaj status bez zakładania konta.
          </span>
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: 20 }}>
          <div
            style={{
              width: 64,
              height: 8,
              borderRadius: 4,
              background: "#0088ff",
            }}
          />
          <span style={{ fontSize: 28, color: "#6e6e73" }}>
            {customerAppUrl.host}
          </span>
        </div>
      </div>
    ),
    size,
  );
}
