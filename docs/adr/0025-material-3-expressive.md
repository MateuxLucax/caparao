# 25. Material 3 Expressive on the phone and the watch

Status: Proposed

## Context

The app uses plain Material 3 on the phone ([ADR 0005](0005-compose-material3-dynamic-color.md)) and Wear Material 3 on
the watch ([ADR 0023](0023-watch-companion.md)) without its Expressive parts. The user wants the more expressive look of
Material 3 Expressive on both, following the Wear OS guide
(https://developer.android.com/design/ui/wear/guides/get-started/apply).

## Decision

Adopt Expressive in small stacked PRs, each with screenshots rendered on the JVM:

1. **Watch.** `AppScaffold` with the curved time on top, tare as an `EdgeButton` hugging the bottom of the round screen,
   the current pour's progress as an arc around the edge, the system's dynamic colors, and buttons that morph shape when
   pressed. `WatchScreenshotsTest` renders it into `docs/screenshots/watch`.
2. **Phone components.** The brew type picker becomes a connected `ButtonGroup`, guided steps show a wavy progress
   indicator, the small spinners become `LoadingIndicator`, and the large brew buttons use the Expressive sizes and press
   shapes.
3. **Phone theme.** `MaterialExpressiveTheme` with the expressive motion scheme inside `AppTheme`, keeping the color
   choice of [ADR 0024](0024-brand-color-option.md).
4. **Showcase moments**: onboarding and the espresso and filter brews get the most expression. Onboarding pages use
   morphing shapes (`MaterialShapes`) and spring transitions between steps; the brew flow morphs the current step's shape
   from taring to cup to first drop, the guided steps use the wavy progress, and the summary opens with a spring and
   a large Roboto Flex result.
5. **Roboto Flex** for the weight, the timer and titles, bundled so it works offline ([ADR 0003](0003-offline-first.md)).

## Consequences

- Part of the phone's Expressive API is experimental (`@ExperimentalMaterial3ExpressiveApi`) and may change between BOM
  updates.
- The watch module gets Robolectric and Roborazzi as test-only dependencies, like the app.
- Roboto Flex adds about 1.7 MB to the APK.
