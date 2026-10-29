# 12. Sideloaded APK on GitHub Releases

Status: Accepted

## Context

A personal app does not need the Play Store, its review or its policies. The repository is private for now and will
be made public.

## Decision

Distribute a signed release APK through GitHub Releases. The keystore and its passwords exist only as repository
secrets and on the developer's machine, passed to Gradle through environment variables. Every green push
to `main` is released automatically, as in health-connect-bridge (manual while the repository was private); a manual
run or a `v*` tag still works.

## Consequences

- Updates install over the previous version as long as the signing key never changes: the key must be backed up.
- No automatic updates; the user installs new versions by hand.
