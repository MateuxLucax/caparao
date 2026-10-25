# 5. Jetpack Compose and Material 3 with dynamic color

Status: Accepted

## Context

The app has a handful of screens and should feel native on a current Android phone.

## Decision

UI in Jetpack Compose with Material 3 and the system's dynamic colors in light and dark. minSdk 31 guarantees dynamic
color ([ADR 0009](0009-min-sdk-31.md)), so there is no fallback scheme. The brand color (coffee-leaf green) appears only
in the logo.

## Consequences

- The app follows the user's wallpaper colors; no theme code to maintain.
- Screens are previewable and testable with Compose testing.
