# 13. Charts drawn on a Compose Canvas

Status: Accepted

## Context

The app draws one kind of chart: a brew's weight and flow over time, with a few axes and labels. Chart libraries for
Compose are large and change often.

## Decision

Draw the curves on a Compose `Canvas`. The scaling (axes, ticks, mapping values to pixels) is a pure function tested on
the JVM; the composable only draws what it returns.

## Consequences

- No dependency ([ADR 0004](0004-minimal-dependencies.md)) and full control over the look.
- Interactions such as zoom or tooltips must be written by hand if the stage 7 wireframes ask for them.
