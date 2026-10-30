# 24. Brand color as an option next to Material You

Status: Proposed

## Context

[ADR 0005](0005-compose-material3-dynamic-color.md) uses only the system's dynamic colors, so the app looks like every
other app on the phone and the brand green shows only in the logo. The user wants a setting to choose between a color of
the app's own and the system's, to give the app more personality.

## Decision

- A theme setting in Settings: **System colors** (Material You, the default) or **Caparaó** (a brand scheme).
- The brand scheme is generated from one seed color with Material Color Utilities' tonal spot, the same algorithm Android
  uses for wallpapers, and checked in as constant light and dark `ColorScheme`s (`BrandColor`). No runtime dependency
  ([ADR 0004](0004-minimal-dependencies.md)).
- Seed candidates, rendered by `ScreenshotsTest.brandColors` in `docs/screenshots/*/brand-*.png`:

  | Name | Seed | Idea |
  |------|------|------|
  | Leaf | `#1E3B2C` | Coffee-leaf green, already the launcher background |
  | Coffee | `#6F4E37` | Roasted coffee brown |
  | Cherry | `#B23A2E` | Ripe coffee-cherry red |
  | Mountain | `#3E5C76` | Caparaó mountain blue |

  One is kept; the others are dropped before this is accepted.
- Screens use only the scheme's color roles (https://m3.material.io/styles/color/roles), never fixed colors, so both
  options work unchanged: `primary` for the key accents (weight line, timer, progress, selected brew), `tertiary` for the
  contrasting second series (flow line, inside the target window), `error` for failures and past the window,
  `onSurface`/`onSurfaceVariant` for text, `surfaceContainerHighest` for empty tracks and `outlineVariant` for dividers.
  Each `on*` color goes only on its own pair (`onErrorContainer` on `errorContainer`).
- The setting lives in `SettingsStore` and applies at once, without restarting the app. Light and dark still follow
  the system.

## Consequences

- The app can look like itself, with the logo's color across the screens, while Material You stays the default.
- Two schemes to keep readable: screens are checked in both through the screenshots.
- Amends ADR 0005, whose "no theme code to maintain" now holds only for the system option.
