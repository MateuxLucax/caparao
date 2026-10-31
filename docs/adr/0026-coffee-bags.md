# 26. Coffee bags

Status: Accepted

## Context

The user wants to manage the bags of coffee on the shelf, to follow them after each brew: how much is left and how old
the roast is. Brews already carry a free-text coffee note ([ADR 0021](0021-brew-notes.md)) that syncs to the exporter.

## Decision

- A `coffee_bags` table on the phone (schema 4): name, roaster, origin and process, roast date, bag weight, notes and a
  finished flag. Bags never leave the phone, like recipes.
- A brew points to a bag by name, through its coffee note. A bag's use is the dose of every brew with that coffee since
  the bag was added, so the brew table, the contract and the server stay as they are.
- Open bags come first among the coffee suggestions after a brew.
- Managed in a Coffees section at the top of Settings: a list, and a dialog to add, edit, finish or delete one.

## Consequences

- Renaming a bag detaches the brews typed with the old name; two open bags with the same name share their brews.
- A bag id on the brew can come later, if per-bag history is needed.
