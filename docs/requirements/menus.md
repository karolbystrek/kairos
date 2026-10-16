# Menus and order items

## Location menus

Each non-archived location owns at most one **Menu**: an ordered list of item
names with no name of its own. Menus are independent; there is no tenant-wide
menu library and no sharing between locations. A tenant administrator can
create, edit and delete the menu of any location in the tenant; a location
manager can do so only for their own assigned location. Operators can read
their location's menu but cannot change it.

A menu holds between 1 and 100 items. Item names are trimmed single-line text
of at most 60 characters and are unique within their menu after case
normalization. Saving a menu replaces its complete item list and takes effect
at once for new orders at that location. Deleting a menu leaves the location
without one. Archiving a location removes its menu.

The panel manages menus on the Menu page for the selected location. Staff may
copy the items of another location's menu they can access into the editor;
nothing is saved until they save, and the copy keeps no link to its source.
Location details show the location's menu and link to the Menu page.

## Ordering from a menu

Every order may carry an optional list of menu items, each with a quantity
between 1 and 99, stored separately from its optional label and its daily
number. Items and label are independent: an order may have neither, either or
both. When the location has a menu, every item must belong to it and
appear once; a stale selection is rejected. A location without a menu
rejects items. Staff and External Integrations follow the same rules. Items are
an immutable snapshot of names and quantities, so later menu edits or deletion
never change existing orders.

When the selected location has a menu, the panel's order-creation flow
shows its items as tiles ([staff panel](staff-panel.md#orders-and-customer-qr)). Changing the selection regenerates the label field from it,
for example `2× Burger, Frytki`: items follow the menu order, quantity 1 omits
the multiplier, and items are separated by a comma and space. Text longer than
the 80-character label limit is shortened with an ellipsis. Staff may then
edit the label freely; a later item change regenerates it again and replaces
those edits, while editing the label never changes the items.

## Menu storage and access

Location menus and their ordered items are tenant-owned rows protected by
row-level security: staff with access to the location can read its menu, and
only its administrator or manager can write it. External Integrations can read
only the menus of locations their key can read. Order items are protected with
their order. A menu references its location as a database constraint, and
location retention purge removes the menu with the location.
