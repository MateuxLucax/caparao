#!/usr/bin/env bash
set -euo pipefail

: "${TAG:?}" "${TARGET_SHA:?}" "${GH_REPO:?}" "${APK:?}" "${WEAR_APK:?}" "${SIGNER_NOTE:?}"

tag_sha=$(gh api "repos/$GH_REPO/commits/$TAG" --jq .sha 2>/dev/null || true)
if gh release view "$TAG" >/dev/null 2>&1; then
  [ "$tag_sha" = "$TARGET_SHA" ] || {
    echo "::error::release $TAG points to ${tag_sha:-no commit}, expected $TARGET_SHA"
    exit 1
  }
  gh release upload "$TAG" "$APK" "$WEAR_APK" --clobber
  notes=$(gh release view "$TAG" --json body --jq .body)
  if [[ "$notes" != *"$SIGNER_NOTE"* ]]; then
    gh release edit "$TAG" --notes "$(printf '%s\n\n%s' "$notes" "$SIGNER_NOTE")"
  fi
else
  if [ -n "$tag_sha" ] && [ "$tag_sha" != "$TARGET_SHA" ]; then
    echo "::error::tag $TAG points to $tag_sha, expected $TARGET_SHA"
    exit 1
  fi
  gh release create "$TAG" "$APK" "$WEAR_APK" --target "$TARGET_SHA" --title "$TAG" --generate-notes --notes "$SIGNER_NOTE"
fi
