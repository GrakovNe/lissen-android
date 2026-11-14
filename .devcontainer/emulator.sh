#!/usr/bin/env bash
# Headless Android emulator on the host's KVM. View and control it from the host with scrcpy.
#   start    create the AVD if needed, boot it, wait until it is ready
#   install  build the debug app, install it and open it
#   stop     shut the emulator down
#   status   list adb devices
set -euo pipefail

AVD_NAME="${AVD_NAME:-lissen}"
SYSTEM_IMAGE="${ANDROID_AVD_SYSTEM_IMAGE:-system-images;android-34;google_apis;x86_64}"
APP_ID="org.grakovne.lissen.debug"
LOG="/tmp/emulator-$AVD_NAME.log"

running() { adb devices | grep -q '^emulator-'; }

start() {
  if running; then
    echo "Emulator already running"
    return
  fi

  [ -e /dev/kvm ] || { echo "/dev/kvm is missing: the container needs --device /dev/kvm" >&2; exit 1; }

  if ! avdmanager list avd -c | grep -qx "$AVD_NAME"; then
    echo "Creating AVD $AVD_NAME from $SYSTEM_IMAGE"
    echo no | avdmanager create avd -n "$AVD_NAME" -k "$SYSTEM_IMAGE" -d pixel_6 > /dev/null
  fi

  echo "Booting $AVD_NAME (log: $LOG)"
  nohup emulator -avd "$AVD_NAME" -no-window -no-audio -no-boot-anim \
    -gpu swiftshader_indirect -memory 4096 -cores 4 > "$LOG" 2>&1 &

  timeout 300 adb wait-for-device
  for _ in $(seq 1 150); do
    [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ] && { echo "Emulator ready"; return; }
    sleep 2
  done

  echo "Emulator did not finish booting; see $LOG" >&2
  exit 1
}

install() {
  running || start
  cd "$(dirname "$0")/.."
  ./gradlew :app:installDebug
  adb shell monkey -p "$APP_ID" -c android.intent.category.LAUNCHER 1 > /dev/null
  echo "Lissen (debug) installed and opened"
}

case "${1:-start}" in
  start) start ;;
  install) install ;;
  stop) adb emu kill || true ;;
  status) adb devices -l ;;
  *) echo "usage: $0 start|install|stop|status" >&2; exit 2 ;;
esac
