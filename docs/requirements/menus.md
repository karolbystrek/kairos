# Menus and menu-item order labels

## Contents

- [Menus and menu-item order labels](#menus-and-menu-item-order-labels)
- [Contents](#contents)
- [Centralized tenant menus](#centralized-tenant-menus)
- [Active location menu](#active-location-menu)
- [Ordering from a menu](#ordering-from-a-menu)
- [Menu storage and access](#menu-storage-and-access)

## Centralized tenant menus

A **Menu** is a named, ordered list of item names owned by one tenant. Only a
tenant administrator creates, edits and deletes menus. Every menu is available
to every location of its tenant; there is no per-location availability, so
menus need no location authorization of their own. Location managers and
operators can read their tenant's menus but cannot change them.

Menu names are trimmed single-line text of at most 120 characters, unique
within a tenant after case normalization. A menu holds between 1 and 100 items.
Item names are trimmed single-line text of at most 60 characters and are unique
within their menu after case normalization. Editing a menu replaces its name and
complete item list and takes effect at once for every location using it.

## Active location menu

Each non-archived location has at most one active menu. A tenant administrator
can choose or clear the active menu of any location in the tenant; a location
manager can do so only for their own assigned location. Operators cannot change
it. A menu that is active at any location cannot be deleted until every location
using it switches to another menu or to no menu. Deleting a location clears its
active menu.

## Ordering from a menu

When the selected location has an active menu, the panel's order-creation sheet
lists its items. Staff choose items with quantities between 1 and 99, type a
custom label, or leave both empty for an automatic label; items and a custom
label cannot be combined. Chosen items must belong to the location's current
active menu; a stale selection is rejected.

The order data model does not change. The API allocates the ordinary automatic
daily number and stores the selection only in the order's immutable label as
`<number> · <items>`, for example `14 · 2× Burger, Frytki`. Items follow the
menu order, quantity 1 omits the multiplier, and items are separated by a comma
and space. The complete label is limited to 200 characters; a longer selection
is rejected and the panel blocks it before submission. Later menu edits or
deletion never change existing labels. A location without an active menu creates
orders exactly as before, and External Integrations keep sending only an
optional custom label.

## Menu storage and access

Menus, their ordered items and the location-to-menu choice are tenant-owned
rows protected by row-level security: tenant staff can read menus, only tenant
administrators can write them, and the active-menu choice is readable by staff
with access to the location and writable by its administrator or manager. The
active-menu reference to a menu is a database constraint, so a menu in use
cannot be removed even under concurrent changes. Location retention purge
removes the location's active-menu choice with the location.
