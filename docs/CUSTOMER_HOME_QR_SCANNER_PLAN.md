# Customer Home and QR Scanner Plan

## Goal

Redesign the customer Home screen around active orders and add a simple,
in-app QR scanner for opening another order.

## User experience

- Rename the order section to **Your orders** and show active orders only.
- When there are no active orders, show a centered QR-scanner icon button with
  the label **Scan an order**.
- When active orders exist, keep the list as the focal point and place scanning
  in a compact secondary action in the Home header.
- Add a quiet Home action to active order views.
- Remember the last stable destination. Reopen the last active order when the
  customer left it open; otherwise reopen Home. Preserve the existing one-time
  order launch after installing from an order page.

## Scanner

- Open a dedicated `/scan` view and request camera access after the customer
  presses the scan button.
- Prefer the rear camera and allow camera switching when multiple cameras are
  available. Do not add a flashlight control.
- Decode QR codes locally with a cross-browser decoder.
- Accept only Kairos customer URLs matching `/orders/<UUID>` on the configured
  customer origin.
- Navigate immediately after recognizing a valid code.
- Keep scanning after an invalid code and show a small inline error.
- Stop all camera tracks after success, cancellation, navigation, or unmount.
- If camera access fails, show simple instructions to scan the code with the
  device Camera app.
- If a valid code is scanned offline, keep it in memory and offer Retry after
  connectivity returns. Do not add it to **Your orders** before it loads
  successfully.

## Stopping order tracking

- Remove the existing bulk **Clear tracked orders** action.
- Swiping an order card left reveals **Stop tracking**; it does not remove the
  order immediately.
- Provide the same action in an accessible per-card overflow menu for desktop,
  keyboard, and assistive-technology users.
- Remove only that order's local snapshot and notification enrollment, then
  update the application badge.
- Allow an explicitly scanned or reopened active order to be tracked again.

## Implementation outline

1. Extract shared order-route construction and add strict scanned-URL parsing.
2. Add the `/scan` page, camera lifecycle handling, and QR decoder.
3. Redesign Home for the empty and populated states.
4. Add single-order untracking using the existing notification-enrollment API.
5. Store the last stable Home/order destination in IndexedDB and restore it in
   installed-app launches, including the existing offline snapshot behavior.
6. Add focused tests for URL validation, scanner states, camera cleanup,
   single-order removal, and launch restoration.

## Verification

- Run `npm --prefix apps/customer-app run check`.
- Run `git diff --check`.
- Manually verify camera permission, scanning, cancellation, and installed-PWA
  launch behavior on Android Chrome and iOS Safari.
