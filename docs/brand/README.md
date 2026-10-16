# Kairos brand mark

## Idea

A hanging bell caught mid-swing. The bell's motion, shown by two trails left
on the path of its lip, carries the "your order is ready" signal. Sound waves
are deliberately absent.

## Masters

All files are SVG on a 256-unit grid with no live text, rasters or filters.

| File | Use |
|---|---|
| `kairos-symbol.svg` | Black master, transparent background |
| `kairos-symbol-blue.svg` | System blue symbol on light backgrounds |
| `kairos-symbol-white.svg` | Reversed symbol on blue or dark backgrounds |
| `kairos-symbol-small.svg` | Small-size cut (one heavier trail) below 24 px |
| `kairos-app-icon.svg` | White symbol on a rounded blue tile |
| `kairos-app-icon-small.svg` | Tile with the small-size cut, used for 16 px favicons |
| `kairos-app-icon-accent.svg` | Marketing variant with orange trails; not used in the apps |
| `kairos-lockup.svg` | Horizontal lockup: blue symbol, black wordmark |
| `kairos-lockup-black.svg` | One-colour horizontal lockup |
| `kairos-lockup-white.svg` | Reversed horizontal lockup |
| `kairos-lockup-stacked.svg` | Stacked lockup for square formats |

The wordmark is Inter Display SemiBold (SIL Open Font License), converted to
outlines, so it needs no installed font.

## Colour

| Role | HEX | Notes |
|---|---|---|
| Brand blue | `#0088FF` | Apple system blue (light); tile and symbol colour |
| Brand blue, dark UI | `#0091FF` | System blue (dark), for the symbol on dark surfaces |
| Wordmark | `#000000` | Reversed to `#FFFFFF` on blue or dark backgrounds |
| Accent | `#FF8D28` | System orange, only for the trails in the accent variant |

The one-colour and reversed versions must remain usable wherever colour is
unavailable.

## Clear space and minimum size

- Keep clear space of at least one quarter of the symbol's height (64 grid
  units) around the symbol or lockup.
- Use the small-size cut whenever the symbol renders below 24 px; the regular
  symbol starts at 24 px.
- Keep the horizontal lockup at least 24 px tall on screen.

## Application assets

The raster assets in `apps/customer-app/public` and `apps/panel-app/public` are
rendered from these masters:

- `icons/kairos-icon-192.png` and `icons/kairos-icon-512.png` render
  `kairos-app-icon.svg`.
- `icons/kairos-icon-maskable-512.png` renders the symbol on a full-bleed
  square at 62 % scale, inside the maskable safe zone.
- `apple-touch-icon.png` (180 px) renders the symbol on a full-bleed square
  because iOS applies its own corner mask.
- `favicon.ico` contains `kairos-app-icon-small.svg` at 16 px and
  `kairos-app-icon.svg` at 32 and 48 px.
- `icons/kairos-lockup.png` (240 px tall) renders `kairos-lockup.svg` for the
  share-preview images.

## Misuse

- Don't add sound waves, rays or extra trails. The motion is the idea.
- Don't straighten the bell or change its tilt.
- Don't recolour parts of the symbol except with the accent variant.
- Don't stretch, outline, add shadows or gradients, or place the symbol on
  low-contrast backgrounds.
- Don't retype the wordmark in another font.
