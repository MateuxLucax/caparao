#!/usr/bin/env bash
set -euo pipefail

: "${TAG:?}" "${TARGET_SHA:?}" "${GH_REPO:?}" "${APK:?}" "${WEAR_APK:?}" "${AAB:?}" "${WEAR_AAB:?}" "${SIGNER_NOTE:?}"

if ! tag_sha=$(gh api "repos/$GH_REPO/commits/$TAG" --jq .sha 2>/dev/null); then
  tag_sha=
fi
if gh release view "$TAG" >/dev/null 2>&1; then
  [ "$tag_sha" = "$TARGET_SHA" ] || {
    echo "::error::release $TAG points to ${tag_sha:-no commit}, expected $TARGET_SHA"
    exit 1
  }
  gh release upload "$TAG" "$APK" "$WEAR_APK" "$AAB" "$WEAR_AAB" --clobber
  release=$(gh release view "$TAG" --json isDraft,body)
  notes=$(jq -r .body <<< "$release")
  is_draft=$(jq -r .isDraft <<< "$release")
  if [[ "$notes" != *"$SIGNER_NOTE"* ]]; then
    gh release edit "$TAG" --notes "$(printf '%s\n\n%s' "$notes" "$SIGNER_NOTE")"
  fi
  if [ "$is_draft" = true ]; then
    gh release edit "$TAG" --draft=false
  fi
else
  if [ -n "$tag_sha" ] && [ "$tag_sha" != "$TARGET_SHA" ]; then
    echo "::error::tag $TAG points to $tag_sha, expected $TARGET_SHA"
    exit 1
  fi
  gh release create "$TAG" "$APK" "$WEAR_APK" "$AAB" "$WEAR_AAB" --target "$TARGET_SHA" --title "$TAG" --generate-notes --notes "$SIGNER_NOTE"
fi
