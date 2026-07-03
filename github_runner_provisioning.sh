#!/usr/bin/env bash
#
# Provisioning script for a self-hosted GitHub Actions runner
# for GrakovNe/lissen-android on an Ubuntu 22.04 (x86_64) box.
#
# Idempotent: safe to re-run. Run as root.
#
#   sudo ./provision-runner.sh
#
# A fresh registration token is required to (re)register the runner.
# Get one at: Settings -> Actions -> Runners -> New self-hosted runner
# and export it:  export RUNNER_TOKEN=XXXX   (token lives ~1h)
#
set -euo pipefail

# ---- config ------------------------------------------------------------------
RUNNER_USER="actions-runner"
RUNNER_HOME="/home/${RUNNER_USER}"
RUNNER_DIR="${RUNNER_HOME}/actions-runner"
RUNNER_VERSION="2.335.1"
REPO_URL="https://github.com/GrakovNe/lissen-android"
RUNNER_NAME="${RUNNER_NAME:-lissen-local}"
RUNNER_LABELS="self-hosted,linux,x64"

ANDROID_HOME="/opt/android-sdk"
CMDLINE_TOOLS_ZIP="commandlinetools-linux-13114758_latest.zip"
# Keep these in sync with app/build.gradle.kts (compileSdk / buildToolsVersion)
SDK_PACKAGES=("platform-tools" "platforms;android-37.0" "build-tools;36.0.0" "cmdline-tools;latest")

# ---- 0. must be root ---------------------------------------------------------
[ "$(id -u)" -eq 0 ] || { echo "run as root"; exit 1; }

# ---- 1. OS dependencies (build + Android emulator) ---------------------------
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq \
  curl wget unzip ca-certificates \
  libpulse0 libgl1 libnss3 libxcursor1 libxcomposite1 libasound2

# ---- 2. runner user, with KVM access for the emulator ------------------------
id "${RUNNER_USER}" &>/dev/null || useradd -m -s /bin/bash "${RUNNER_USER}"
usermod -aG kvm "${RUNNER_USER}"   # emulator needs /dev/kvm (crw-rw---- root:kvm)

# ---- 3. Android SDK (system-wide, owned by runner user) ----------------------
if [ ! -x "${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager" ]; then
  mkdir -p "${ANDROID_HOME}/cmdline-tools"
  tmp="$(mktemp -d)"
  wget -q "https://dl.google.com/android/repository/${CMDLINE_TOOLS_ZIP}" -O "${tmp}/cmdt.zip"
  unzip -q -o "${tmp}/cmdt.zip" -d "${tmp}/unz"
  rm -rf "${ANDROID_HOME}/cmdline-tools/latest"
  mv "${tmp}/unz/cmdline-tools" "${ANDROID_HOME}/cmdline-tools/latest"
  rm -rf "${tmp}"
fi

export ANDROID_HOME ANDROID_SDK_ROOT="${ANDROID_HOME}"
SDKMANAGER="${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager"
yes | "${SDKMANAGER}" --licenses >/dev/null 2>&1 || true
"${SDKMANAGER}" "${SDK_PACKAGES[@]}"
chown -R "${RUNNER_USER}:${RUNNER_USER}" "${ANDROID_HOME}"

# ---- 4. download & extract the runner agent ---------------------------------
if [ ! -x "${RUNNER_DIR}/config.sh" ]; then
  sudo -u "${RUNNER_USER}" bash -c "
    mkdir -p '${RUNNER_DIR}' && cd '${RUNNER_DIR}'
    curl -sL -o rn.tgz 'https://github.com/actions/runner/releases/download/v${RUNNER_VERSION}/actions-runner-linux-x64-${RUNNER_VERSION}.tar.gz'
    tar xzf rn.tgz && rm rn.tgz
  "
fi

# ---- 5. inject ANDROID_HOME into every job ----------------------------------
cat > "${RUNNER_DIR}/.env" <<EOF
ANDROID_HOME=${ANDROID_HOME}
ANDROID_SDK_ROOT=${ANDROID_HOME}
EOF
chown "${RUNNER_USER}:${RUNNER_USER}" "${RUNNER_DIR}/.env"

# ---- 6. register with GitHub (needs a fresh RUNNER_TOKEN) --------------------
if [ -n "${RUNNER_TOKEN:-}" ]; then
  sudo -u "${RUNNER_USER}" bash -c "
    cd '${RUNNER_DIR}'
    ./config.sh --url '${REPO_URL}' --token '${RUNNER_TOKEN}' \
      --name '${RUNNER_NAME}' --labels '${RUNNER_LABELS}' \
      --work _work --unattended --replace
  "
  # install & (re)start as a systemd service, survives reboot
  ( cd "${RUNNER_DIR}" && ./svc.sh install "${RUNNER_USER}" && ./svc.sh start )
else
  echo ">> RUNNER_TOKEN not set — skipped registration."
  echo ">> Get a token and run:  cd ${RUNNER_DIR} && sudo -u ${RUNNER_USER} ./config.sh --url ${REPO_URL} --token <TOKEN> --labels ${RUNNER_LABELS} --unattended --replace"
  echo ">> then: cd ${RUNNER_DIR} && ./svc.sh install ${RUNNER_USER} && ./svc.sh start"
fi

echo "DONE."
