#!/usr/bin/env bash
#
# Runs the whole instrumented suite on a fleet of emulators on a single CI
# machine (one self-hosted runner cannot run jobs concurrently, so the
# parallelism lives inside the job).
#
#   slot 0  debug build + androidTest APK   -> HiltTestRunner (connected suite)
#   slot 1  minified app + minifiedTest APK -> E2E shard 1
#   slot 2  ...                             -> E2E shard 2
#
# The connected suite runs alone first: CoverOomReproTest measures HTTP/2
# buffering throughput and the UI tests have wall-clock timeouts, so both
# flake when several emulators render at once. The E2E shards then run in
# parallel on the remaining slots. Every shard is a plain `am instrument`
# run; a shard passes when its output ends with "OK (N tests)" with N > 0.
# Failed tests are re-run once on their own (launcher gestures get swallowed
# under load); a shard whose failures pass in isolation counts as green. The
# script fails if anything is still red and prints a per-shard summary.
#
set -uo pipefail

SDK="${ANDROID_HOME:?ANDROID_HOME must be set}"
EMULATOR="$SDK/emulator/emulator"
ADB="adb"
RESULTS_DIR="${1:-build/instrumented-results}"
BOOT_TIMEOUT="${BOOT_TIMEOUT:-600}"
TEST_TIMEOUT="${TEST_TIMEOUT:-2700}"
CONNECTED_RAM="${CONNECTED_RAM:-4096}"
E2E_RAM="${E2E_RAM:-3072}"
DISK_SIZE="${DISK_SIZE:-8192M}"

MINIFIED_APP_APK="app/build/outputs/apk/minified/app-minified.apk"
MINIFIED_TEST_APK="minifiedTest/build/outputs/apk/minified/minifiedTest-minified.apk"
DEBUG_APP_APK="app/build/outputs/apk/debug/app-debug.apk"
DEBUG_TEST_APK="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"

MINIFIED_INSTR="org.grakovne.lissen.minifiedtest/androidx.test.runner.AndroidJUnitRunner"
CONNECTED_INSTR="org.grakovne.lissen.debug.test/org.grakovne.lissen.HiltTestRunner"

# Shard plan: balanced by test count; widget and shortcut tests share one slot
# because they mutate the same ambient launcher state. Override a slot's classes
# with SLOT_CLASSES_<n> and the connected suite's classes with CONNECTED_CLASSES
# (smoke runs); the defaults are the full suite.
NS=org.grakovne.lissen.minifiedtest
SHARD_CLASSES[1]="$NS.SettingsGapsE2ETest,$NS.LoginRobustnessE2ETest,$NS.LoginFlowE2ETest,$NS.SettingsFlowE2ETest"
SHARD_CLASSES[2]="$NS.LibraryGapsE2ETest,$NS.LibraryFlowE2ETest,$NS.RobustnessFlowE2ETest,$NS.PlaybackFlowE2ETest,$NS.PlayerTabsFlowE2ETest,$NS.PlayerGapsE2ETest,$NS.ShortcutFlowE2eTest,$NS.WidgetFlowE2eTest"

AVD_PREFIX="ci-e2e"
BASE_PORT=5554
SLOTS="${FLEET_SLOTS:-0 1 2}"
CONNECTED_CLASSES="${CONNECTED_CLASSES:-}"
for slot in $SLOTS; do
  override="SLOT_CLASSES_$slot"
  [ -n "${!override:-}" ] && SHARD_CLASSES[$slot]="${!override}"
done

mkdir -p "$RESULTS_DIR"
PIDS=()
LOGCAT_PIDS=()
EMU_PIDS=()
declare -A EMU_PID
log() { echo "[fleet] $*"; }

cleanup() {
  for pid in "${LOGCAT_PIDS[@]:-}"; do kill "$pid" 2>/dev/null; done
  for pid in "${EMU_PIDS[@]:-}"; do kill "$pid" 2>/dev/null; done
}
trap cleanup EXIT

port_of() { echo $((BASE_PORT + 2 * $1)); }
serial_of() { echo "emulator-$(port_of "$1")"; }

set_avd_config() {
  local cfg="$1" key="$2" value="$3"
  if grep -qE "^[[:space:]]*$key[[:space:]]*=" "$cfg"; then
    sed -i -E "s|^[[:space:]]*$key[[:space:]]*=.*|$key=$value|" "$cfg"
  else
    echo "$key=$value" >>"$cfg"
  fi
}

create_avds() {
  local avdmanager="$SDK/cmdline-tools/latest/bin/avdmanager"
  [ -x "$avdmanager" ] || avdmanager="$(command -v avdmanager)"
  for slot in $SLOTS; do
    local name="$AVD_PREFIX-$slot" ram="$E2E_RAM"
    [ "$slot" = "0" ] && ram="$CONNECTED_RAM"
    if [ ! -f "$HOME/.android/avd/$name.ini" ]; then
      log "creating AVD $name"
      echo no | "$avdmanager" create avd -n "$name" \
        -k "system-images;android-34;google_apis;x86_64" -d pixel_6 --force >/dev/null
    fi
    local cfg="$HOME/.android/avd/$name.avd/config.ini"
    # A killed emulator leaves this lock behind and the next boot aborts with
    # "Running multiple emulators with the same AVD is an experimental feature".
    rm -f "$HOME/.android/avd/$name.avd/multiinstance.lock"
    set_avd_config "$cfg" hw.ramSize "$ram"
    # 512 MB dalvik heap: the old emulator-runner action set heap-size 512M and
    # CoverOomReproTest asserts the 16 MB/stream behaviour of a 512 MB heap.
    set_avd_config "$cfg" vm.heapSize "512M"
    set_avd_config "$cfg" disk.dataPartition.size "$DISK_SIZE"
  done
}

boot_emulator() {
  local slot="$1" port ram cores
  port="$(port_of "$slot")"
  ram="$E2E_RAM"
  cores=2
  if [ "$slot" = "0" ]; then ram="$CONNECTED_RAM"; cores=4; fi
  "$EMULATOR" -avd "$AVD_PREFIX-$slot" -port "$port" -memory "$ram" -cores "$cores" \
    -no-window -no-audio -no-boot-anim -no-snapshot \
    -gpu swiftshader_indirect \
    >"$RESULTS_DIR/emulator-$slot.log" 2>&1 &
  EMU_PIDS+=($!)
  EMU_PID[$slot]=$!
}

wait_for_boot() {
  local slot="$1" serial deadline
  serial="$(serial_of "$slot")"
  deadline=$((SECONDS + BOOT_TIMEOUT))
  while [ $SECONDS -lt $deadline ]; do
    if [ "$($ADB -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; then
      log "$serial booted"
      return 0
    fi
    if ! kill -0 "${EMU_PID[$slot]:-0}" 2>/dev/null; then
      log "ERROR: emulator for slot $slot exited during boot; log tail:"
      tail -5 "$RESULTS_DIR/emulator-$slot.log" 2>/dev/null || true
      return 1
    fi
    sleep 5
  done
  log "TIMEOUT: $serial did not boot within ${BOOT_TIMEOUT}s"
  return 1
}

prepare_device() {
  local serial="$1"; shift
  $ADB -s "$serial" shell input keyevent KEYCODE_WAKEUP || true
  $ADB -s "$serial" shell wm dismiss-keyguard || true
  $ADB -s "$serial" shell settings put global hide_error_dialogs 1
  $ADB -s "$serial" shell settings put global window_animation_scale 0
  $ADB -s "$serial" shell settings put global transition_animation_scale 0
  $ADB -s "$serial" shell settings put global animator_duration_scale 0
  # AOT-compile now so bg-dexopt does not steal CPU mid-suite (the old
  # emulator-runner action avoided this by reusing warm snapshots).
  local pkg
  for pkg in "$@"; do
    $ADB -s "$serial" shell cmd package compile -m speed -f "$pkg" >/dev/null || true
  done
}

start_logcat() {
  local slot="$1" serial
  serial="$(serial_of "$slot")"
  $ADB -s "$serial" logcat -c || true
  $ADB -s "$serial" logcat -v threadtime \
    ActivityManager:I ActivityTaskManager:I am_kill:V am_anr:V am_crash:V \
    AndroidRuntime:E System.err:V LissenE2E:V "*":S \
    >"$RESULTS_DIR/logcat-slot$slot.txt" &
  LOGCAT_PIDS+=($!)
}

run_instrumentation() {
  local slot="$1" component="$2" out="$3" classes="${4:-}"
  local serial pkg attempt rc failed
  serial="$(serial_of "$slot")"
  pkg="${component%%/*}"
  for attempt in 1 2; do
    # Wipe app state first: a sticky PlaybackService restored from a previous
    # run starts outside the Hilt rule and crashes the instrumentation process.
    $ADB -s "$serial" shell pm clear "$pkg" >/dev/null || true
    if [ -n "$classes" ]; then
      timeout --kill-after=60s "$TEST_TIMEOUT" \
        $ADB -s "$serial" shell am instrument -w -e class "$classes" "$component" \
        >"$out" 2>&1
    else
      timeout --kill-after=60s "$TEST_TIMEOUT" \
        $ADB -s "$serial" shell am instrument -w "$component" \
        >"$out" 2>&1
    fi
    rc=$?
    # rc=0 with a crash marker means the instrumentation process died (e.g. a
    # sticky service restarted between tests outside the Hilt rule). Retry once.
    if [ "$rc" = "0" ] && grep -q "Process crashed" "$out"; then
      rc=137
      [ "$attempt" = "2" ] && break
      log "WARN $(basename "$out"): instrumentation crashed, retrying"
      sleep 10
      continue
    fi
    break
  done
  # Failed tests get one retry round on their own. Launcher gestures (widget
  # drags, long-press popups) are swallowed now and then when the device is
  # busy; a shard whose failures pass in isolation counts as green.
  if [ "$rc" = "0" ]; then
    failed="$(grep -oE '^(Error|Failure) in [^(]+\([^)]+\)' "$out" |
      sed -E 's/^(Error|Failure) in ([^(]+)\(([^)]+)\)/\3#\2/' | sort -u | paste -sd, -)"
    if [ -n "$failed" ]; then
      log "WARN $(basename "$out"): re-running failures once: $failed"
      timeout --kill-after=60s "$TEST_TIMEOUT" \
        $ADB -s "$serial" shell am instrument -w -e class "$failed" "$component" \
        >"$out.retry" 2>&1
      echo $? >"$out.retry.rc"
    fi
  fi
  echo "$rc" >"$out.rc"
}

# ---- main --------------------------------------------------------------------
create_avds

E2E_SLOTS="1 2"
[ -n "${FLEET_SLOTS:-}" ] && E2E_SLOTS="$(echo " $FLEET_SLOTS " | sed 's/ 0 / /' | xargs)"

log "booting slot 0 (connected suite runs alone: CoverOomReproTest and audio"
log "timing tests flake while other emulators are up)"
boot_emulator 0
wait_for_boot 0 || { log "aborting: slot 0 did not boot"; exit 1; }

DEBUG_SERIAL="$(serial_of 0)"
log "installing debug APKs"
$ADB -s "$DEBUG_SERIAL" install -r "$DEBUG_APP_APK" >/dev/null || { log "install failed: $DEBUG_APP_APK"; exit 1; }
$ADB -s "$DEBUG_SERIAL" install -r -t "$DEBUG_TEST_APK" >/dev/null || { log "install failed: $DEBUG_TEST_APK"; exit 1; }
prepare_device "$DEBUG_SERIAL" org.grakovne.lissen.debug
start_logcat 0

log "running connected suite on slot 0"
run_instrumentation 0 "$CONNECTED_INSTR" "$RESULTS_DIR/connected.txt" "$CONNECTED_CLASSES"

log "booting E2E emulators in parallel on slots: $E2E_SLOTS"
for slot in $E2E_SLOTS; do boot_emulator "$slot"; done
for slot in $E2E_SLOTS; do
  wait_for_boot "$slot" || { log "aborting: slot $slot did not boot"; exit 1; }
done

log "installing minified APKs"
for slot in $E2E_SLOTS; do
  serial="$(serial_of "$slot")"
  $ADB -s "$serial" install -r "$MINIFIED_APP_APK" >/dev/null || { log "install failed: $MINIFIED_APP_APK"; exit 1; }
  $ADB -s "$serial" install -r -t "$MINIFIED_TEST_APK" >/dev/null || { log "install failed: $MINIFIED_TEST_APK"; exit 1; }
  prepare_device "$serial" org.grakovne.lissen.minified
  start_logcat "$slot"
done

log "running E2E shards in parallel on slots: $E2E_SLOTS"
PIDS=()
RESULT_NAMES="connected"
for slot in $E2E_SLOTS; do
  [ -z "${SHARD_CLASSES[$slot]:-}" ] && continue
  run_instrumentation "$slot" "$MINIFIED_INSTR" "$RESULTS_DIR/e2e-shard$slot.txt" \
    "${SHARD_CLASSES[$slot]}" &
  PIDS+=($!)
  RESULT_NAMES="$RESULT_NAMES e2e-shard$slot"
done
for pid in "${PIDS[@]}"; do wait "$pid"; done

# ---- verdict -----------------------------------------------------------------
FAILED=0
TOTAL=0
for name in $RESULT_NAMES; do
  out="$RESULTS_DIR/$name.txt"
  rc="$(cat "$out.rc" 2>/dev/null || echo missing)"
  count="$(grep -oE '^OK \([0-9]+ tests?\)' "$out" 2>/dev/null | grep -oE '[0-9]+' || true)"
  if [ -z "$count" ] && [ -f "$out.retry" ]; then
    run1="$(grep -oE '^Tests run: [0-9]+' "$out" | grep -oE '[0-9]+' || true)"
    fail1="$(grep -oE 'Failures: [0-9]+' "$out" | tail -1 | grep -oE '[0-9]+' || true)"
    ok2="$(grep -oE '^OK \([0-9]+ tests?\)' "$out.retry" 2>/dev/null | grep -oE '[0-9]+' || true)"
    rc2="$(cat "$out.retry.rc" 2>/dev/null || echo missing)"
    if [ "$rc" = "0" ] && [ "$rc2" = "0" ] && [ -n "$run1" ] && [ -n "$fail1" ] && [ "$ok2" = "$fail1" ]; then
      count="$run1"
      log "RECOVERED $name: $fail1 failed test(s) passed on retry"
    fi
  fi
  if [ -n "$count" ] && [ "$count" -gt 0 ] && [ "$rc" = "0" ]; then
    log "PASS $name: $count tests"
    TOTAL=$((TOTAL + count))
  else
    FAILED=1
    log "FAIL $name (rc=$rc), tail of output:"
    tail -30 "$out" 2>/dev/null || true
  fi
done

log "total tests passed: $TOTAL"

for slot in $SLOTS; do
  $ADB -s "$(serial_of "$slot")" emu kill 2>/dev/null || true
done

exit $FAILED
