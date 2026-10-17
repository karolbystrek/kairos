const margin = 2;
// The logo hides centre modules; level H recovers up to 30% of codewords, so keep it well below.
const logoShare = 0.22;
// Inner markup of docs/brand/kairos-app-icon.svg (256-unit grid).
const appIcon =
  '<rect width="256" height="256" rx="56" fill="#0088FF"/><g transform="translate(-8 -4) translate(128 128) scale(0.8736) translate(-128 -128)"><g fill="#FFFFFF" transform="rotate(-15 128 60)"><path d="M64 172C80 166 88 150 88 128V112A40 40 0 0 1 168 112V128C168 150 176 166 192 172Z"/><rect x="120" y="56" width="16" height="22" rx="8"/><rect x="56" y="170" width="144" height="18" rx="9"/><path d="M113 188A15 15 0 0 0 143 188Z"/></g><path d="M69.3 186.0A139 139 0 0 1 29.7 158.3M80.7 161.5A112 112 0 0 1 57.5 147.0" fill="none" stroke="#FFFFFF" stroke-width="12" stroke-linecap="round"/></g>';

export async function orderQrCode(url: string) {
  // The encoder loads only when an order's details first open.
  const svg = await (
    await import("qrcode")
  ).toString(url, {
    errorCorrectionLevel: "H",
    margin,
    type: "svg",
  });
  const size = Number(svg.match(/viewBox="0 0 (\d+)/)![1]);
  const side = (size - 2 * margin) * logoShare;
  const start = (size - side) / 2;
  const logo =
    `<rect x="${start - 1}" y="${start - 1}" width="${side + 2}" height="${side + 2}" rx="1.5" fill="#ffffff"/>` +
    `<svg x="${start}" y="${start}" width="${side}" height="${side}" viewBox="0 0 256 256">${appIcon}</svg>`;

  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg.replace("</svg>", `${logo}</svg>`))}`;
}
