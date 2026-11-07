export async function orderQrCode(url: string) {
  // The encoder loads only when an order's details first open.
  const { default: QRCodeStyling } = await import("qr-code-styling");
  const svg = (await new QRCodeStyling({
    type: "svg",
    width: 640,
    height: 640,
    margin: 16,
    data: url,
    image: "/icons/kairos-app-icon.svg",
    qrOptions: { errorCorrectionLevel: "H" },
    imageOptions: { hideBackgroundDots: true, imageSize: 0.3, margin: 8 },
    dotsOptions: { type: "rounded", color: "#000000" },
    cornersSquareOptions: { type: "extra-rounded" },
    cornersDotOptions: { type: "dot" },
  }).getRawData("svg")) as Blob;

  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(await svg.text())}`;
}
