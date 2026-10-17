import QRCode from "qrcode";
import { expect, it } from "vitest";

import { orderQrCode } from "@/src/order-qr-code";

const url = "https://kairos.example/orders/0b6f3a52-6f0e-4c7a-9a43-1f2d5c8e7b90";

it("encodes at high error correction with the Kairos mark centred", async () => {
  const dataUrl = await orderQrCode(url);
  const svg = decodeURIComponent(dataUrl.replace("data:image/svg+xml;charset=utf-8,", ""));
  const size = Number(svg.match(/viewBox="0 0 (\d+) \d+"/)?.[1]);
  const logo = svg.match(/<svg x="([\d.]+)" y="([\d.]+)" width="([\d.]+)" height="\3"/);

  expect(dataUrl).toMatch(/^data:image\/svg\+xml;charset=utf-8,/);
  expect(size).toBe(QRCode.create(url, { errorCorrectionLevel: "H" }).modules.size + 4);
  expect(svg).toContain('fill="#0088FF"');
  expect(logo).not.toBeNull();
  const [, x, y, side] = logo!.map(Number);
  expect(x + side / 2).toBeCloseTo(size / 2);
  expect(y + side / 2).toBeCloseTo(size / 2);
  expect(side / size).toBeLessThan(0.25);
});
