#!/usr/bin/env bash
# Runs once when the dev container is created: a sample audiobook for the test server, then
# the server's first-run setup.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"

bash "$here/make-sample-audiobook.sh"
bash "$here/abs-seed.sh" || echo "Audiobookshelf setup did not finish; rerun: bash .devcontainer/abs-seed.sh"

cat <<'EOF'

Dev container ready.
  Unit tests:      ./gradlew :app:testDebugUnitTest
  Emulator:        bash .devcontainer/emulator.sh start
  Install + run:   bash .devcontainer/emulator.sh install
  Cut the server:  bash .devcontainer/net.sh server-down   (server-up to restore)
See .devcontainer/README.md for the full walkthrough.
EOF
