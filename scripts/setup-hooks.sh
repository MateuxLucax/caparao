#!/usr/bin/env bash
# One-time setup after cloning: use the repo's git hooks (secret scan before every commit).
set -euo pipefail
cd "$(dirname "$0")/.."
git config core.hooksPath .githooks
echo "Hooks enabled from .githooks"
