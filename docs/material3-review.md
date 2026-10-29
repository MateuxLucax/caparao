# Material 3 review

A pass over the app against Material 3 (m3.material.io): color, shape, typography, motion and transitions. Each
finding names the screen, what the guideline says and what to do. "Done" marks what the motion and brew PRs already
changed.

## Overall

| Area | State | Finding |
| --- | --- | --- |
| Color | Good | Dynamic color only, light and dark ([ADR 0005](adr/0005-compose-material3-dynamic-color.md)). Roles are used by meaning: `primary` for the live value, `errorContainer` for failures, `onSurfaceVariant` for secondary text. |
| Shape | Good | Default `Shapes` everywhere: Card medium (12dp), buttons full, segmented buttons from `SegmentedButtonDefaults`. No ad hoc `RoundedCornerShape`. |
| Typography | Mostly good | The type scale is used throughout. One exception: the readout's weight is `64.sp` with `FontWeight.Medium`, outside the scale. `displayLarge` (57sp) is the closest token; keep the custom size only if 57sp reads too small on the bar. |
| Motion easing | Off | Animations use Compose defaults (`FastOutSlowIn`, 300ms springs) or `LinearEasing`. M3 asks for the emphasized easing on transitions in and out of view and the standard easing on small changes, with durations from the token set (short 50–200ms, medium 250–400ms, long 450–600ms). |
| Transitions | Partly | Screens swap with no transition (tabs, opening a brew or a saved brew). The brew steps fade, the guide header slides by a third with a plain fade. M3 names three patterns for these: fade through (unrelated tabs), shared axis X (forward and back in a flow) and container transform (a list item opening into its detail). |

## Per screen

### Onboarding

- Steps swap instantly when tapping Next or Back. Use shared axis X (slide 30dp + fade, emphasized easing, ~300ms),
  forward to the right, Back to the left. The step indicator already reads as a flow, so the motion matches it.
- `Get started` is 52dp tall, the brew screen's main buttons are 64dp. Pick one height for primary actions in flows
  (56dp is the M3 large-button height).

### Home (scale)

- The ring around the weight carries the flow, which the text under it already shows. Removing it leaves the readout
  and the controls, closer to M3's "one primary focus per screen".
- Round controls use `FilledIconButton` and `FilledTonalIconButton` at custom sizes (64dp, 80dp). M3 Expressive has
  sized icon buttons; until the app moves to that version, these sizes are fine as long as the touch target stays at
  least 48dp.
- The mode selector is a `SingleChoiceSegmentedButtonRow` used as navigation (picking a mode opens a screen). M3
  segmented buttons select an option on the same screen; a row of buttons or cards ("Espresso", "Filter") reads more
  honestly as "start a brew". Low priority, as the current one works and tests cover it.

### Brew (guide)

- Steps change with a fade (`fadeIn() togetherWith fadeOut()`), the header with a short slide. Use shared axis X for
  both, emphasized decelerate in and accelerate out, so moving forward through the guide feels like one direction.
- The stage indicator's segments animate color with the default spring. Use the standard easing at a short duration
  (~200ms).
- The ring around the weight showed the flow, which the text already says, and the ratio was a plain label. Two rings
  around the weight show "when to stop" instead: the outer one fills toward the target ratio, the inner one changes
  color once the ratio reads the target.
- The live weight glides with a 100ms linear tween between 10 Hz readings. Keep it linear: it tracks a signal, it is
  not a UI transition, and an eased curve would lag the scale.

### Brews (list) and brew detail

- Opening a brew replaces the list with no transition. M3 suggests a container transform from the card. Compose has
  it through `SharedTransitionLayout` + `sharedBounds`; until then, shared axis X (forward into the detail, back out)
  is a cheap, consistent stand-in.
- Cards use the default filled `Card`. Fine; a list of many items could also be `ListItem`s with dividers, which is
  denser, but cards match the rest of the app.

### Settings

- Sections are filled `Card`s with a `titleMedium` title, consistent with the rest. Long forms in M3 are usually flat
  lists with headers; with four short sections, cards are fine.
- Text fields are `OutlinedTextField`, the recommended style for forms.

### Navigation

- Tabs switch instantly. M3 uses fade through between top-level destinations: the old one fades out (~90ms), the new
  one fades in and scales from 92% (~210ms), emphasized easing.
- Opening a brew from Home and closing it back: shared axis Z or X; X is used here to match the detail.

## Motion tokens to use

From m3.material.io/styles/motion/easing-and-duration/tokens-specs:

| Token | Value | Use |
| --- | --- | --- |
| Emphasized decelerate | cubic(0.05, 0.7, 0.1, 1.0) | Entering the screen |
| Emphasized accelerate | cubic(0.3, 0.0, 0.8, 0.15) | Leaving the screen |
| Standard | cubic(0.2, 0.0, 0.0, 1.0) | Changes that stay on screen (color, progress) |
| Short 4 | 200ms | Small changes |
| Medium 2 | 300ms | Fades and slides within a screen |
| Medium 4 | 400ms | Full-screen transitions |

Compose's `MotionScheme` (material3 1.4) wraps these as springs; until the app uses it, a small `Motion` object with
the curves and durations above keeps them in one place.
