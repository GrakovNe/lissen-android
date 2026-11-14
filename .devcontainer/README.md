# Dev container

A build and test environment for Lissen that needs nothing on the host but Podman, VS Code
with the Dev Containers extension, and KVM. It is made for rootless Podman
(`"dev.containers.dockerPath": "podman"`). To use Docker remove `userns_mode: keep-id`
from `docker-compose.yml`.

What it contains:

| | |
|---|---|
| `dev` (`lissen-dev`) | JDK 25 (as CI), Android SDK 37, build-tools 37.0.0, and a headless API 34 emulator on the host's `/dev/kvm`. Host network. |
| `audiobookshelf` (`lissen-abs`) | A throwaway Audiobookshelf server on `localhost:13378`, user `root`, password `lissen`, with a 30-minute sample book that beeps once a second. |

Gradle's cache and the emulator's AVD are kept in named volumes. The server's data and
the sample book live in `.devcontainer/.data/`, which git ignores.

## Open it

In VS Code: **Dev Containers: Reopen in Container**. The first build downloads about 3 GB.
`post-create.sh` then generates the sample book and sets up the server.

## Build and unit tests

```bash
./gradlew :app:testDebugUnitTest          # all JVM unit tests
./gradlew :app:testDebugUnitTest --tests 'org.grakovne.lissen.playback.PlaybackInterruptionServiceTest'
./gradlew :app:assembleDebug              # app/build/outputs/apk/debug/
```

## Run the app on the emulator

**These commands are ran from within the dev container.**

```bash
bash .devcontainer/emulator.sh start      # boots in ~30 s on KVM
bash .devcontainer/emulator.sh install    # builds, installs and opens Lissen (debug)
```

The emulator has no window. To see and control it, run [scrcpy](https://github.com/Genymobile/scrcpy)
**on the host** (`sudo pacman -S scrcpy` on Arch/CachyOS). The container shares the host
network, so the host's `adb` and `scrcpy` find the emulator through the container's adb
server. scrcpy also plays the device audio on the host.

In the app, log in with server `http://10.0.2.2:13378` (the emulator's name for the
host), user `root`, password `lissen`.

You can also use a real phone. Enable wireless debugging, then run `adb pair <ip>:<port>`
and `adb connect <ip>:<port>` from the container. Every script here works with whichever
device adb is connected to, as long as it is the only one.

## Simulating connection loss

**These commands are ran from within the dev container.**

`net.sh` changes the network inside the emulator:

```bash
bash .devcontainer/net.sh server-down     # network up, server refuses connections (Tailscale off)
bash .devcontainer/net.sh server-hang     # network up, server swallows packets (timeouts)
bash .devcontainer/net.sh server-up       # server reachable again
bash .devcontainer/net.sh offline         # Wi-Fi and cellular off
bash .devcontainer/net.sh online
bash .devcontainer/net.sh wifi-off | wifi-on | data-off | data-on
bash .devcontainer/net.sh status
```

From the host you can also stop the server itself with `podman stop lissen-abs` and
`podman start lissen-abs`.

### Test: warning when the server becomes unreachable

1. Play *Beeping Book*. Allow notifications when Lissen asks. Without them, the warning
   shows as a toast.
2. Seek to a part you haven't played yet. Parts you have played come from the playback
   cache and keep working offline.
3. `net.sh server-down`. Playback continues from the buffer for up to about a minute,
   then stops. A **"Playback stopped: server unreachable"** notification appears, telling
   you to press play once you're connected.
4. `net.sh server-up`, then press play. Playback continues where it stopped and the
   notification goes away.

### Test: automatic resume (setting off by default)

1. Settings → Playback → turn on **Resume when the server is back**.
2. Repeat steps 1–3 above. This time the notification says playback will continue on
   its own.
3. `net.sh server-up`. Within 30 seconds the beeping resumes from where it stopped, with
   nobody touching the app.

Variations worth trying:

- `net.sh offline` instead of `server-down`. Nothing is probed while there's no network.
  `net.sh online` probes at once.
- Press pause (or unplug headphones) while it waits. It won't resume on its own; the
  notification switches back to "press play".
- With `server-hang`, the stop takes longer (connection timeouts) but behaves the same.

### Test: rejoining a registered local network (your Tailscale/home Wi-Fi case)

The emulator's Wi-Fi is named `AndroidWifi`. Reach the same server under two addresses:
use the host's LAN IP as the "external" address and `10.0.2.2` as the local one.

1. Log in with `http://<host LAN IP>:13378` (find it with `ip -4 addr` on the host).
2. Settings → Connection → Local network server address: add SSID `AndroidWifi` with
   `http://10.0.2.2:13378`. Grant the location permission it asks for. Android needs it
   to read the Wi-Fi name.
3. Make the external address unreachable: `net.sh server-down <host LAN IP> 13378`.
4. Turn auto-resume on, play, then `net.sh wifi-off`. On cellular, Lissen falls back to
   the external address, which is blocked, so playback stops with the warning.
5. `net.sh wifi-on`. Lissen probes through the local URL and playback resumes.
6. Clean up with `net.sh server-up <host LAN IP> 13378`.

## Logs

```bash
adb logcat -s PlaybackInterruptionService MediaRepository
```

The service logs `Playback stopped, server unreachable`, `Server reachable again; resuming
playback`, or why it stopped waiting.

## Reset

Server data:

```bash
podman stop lissen-abs                    # host
rm -rf .devcontainer/.data/abs            # host or dev container (same folder)
podman start lissen-abs                   # host
bash .devcontainer/abs-seed.sh            # dev container
```

Or delete `.devcontainer/.data/abs` and run **Dev Containers: Rebuild Container** in VS Code.
That recreates both containers, and `post-create.sh` seeds the server again.

Emulator, all in the dev container:

```bash
bash .devcontainer/emulator.sh stop
avdmanager delete avd -n lissen
bash .devcontainer/emulator.sh start      # creates a fresh AVD
```

This wipes only the AVD. The `android-home` volume also holds adb's key, so the host's scrcpy and any paired phone stay authorized.
