# Menus and order items

## Contents

- [Menus and order items](#menus-and-order-items)
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

Every order may carry an optional list of menu items, each with a quantity
between 1 and 99, stored separately from its optional label and its daily
number. Items and label are independent: an order may have neither, either or
both. When the location has an active menu, every item must belong to it and
appear once; a stale selection is rejected. A location without an active menu
rejects items. Staff and External Integrations follow the same rules. Items are
an immutable snapshot of names and quantities, so later menu edits or deletion
never change existing orders.

When the selected location has an active menu, the panel's order-creation sheet
lists its items. Changing the selection regenerates the label field from it,
for example `2× Burger, Frytki`: items follow the menu order, quantity 1 omits
the multiplier, and items are separated by a comma and space. Text longer than
the 80-character label limit is shortened with an ellipsis. Staff may then
edit the label freely; a later item change regenerates it again and replaces
those edits, while editing the label never changes the items.

## Menu storage and access

Menus, their ordered items and the location-to-menu choice are tenant-owned
rows protected by row-level security: tenant staff can read menus, only tenant
administrators can write them, and the active-menu choice is readable by staff
with access to the location and writable by its administrator or manager.
External Integrations can read only the menus active at locations their key can
read. Order items are protected with their order. The
active-menu reference to a menu is a database constraint, so a menu in use
cannot be removed even under concurrent changes. Location retention purge
removes the location's active-menu choice with the location.
