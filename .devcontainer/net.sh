#!/usr/bin/env bash
# Network conditions inside the emulator, for testing playback through connection loss.
#
#   server-down [host] [port]   the server refuses connections, the network stays up
#                               (like Tailscale switched off)
#   server-hang [host] [port]   the server swallows packets: connections time out
#   server-up   [host] [port]   the server is reachable again
#   wifi-off | wifi-on          the emulated Wi-Fi ("AndroidWifi")
#   data-off | data-on          the emulated cellular data
#   offline | online            both radios at once
#   status                      the active server rules and radios
#
# The host defaults to 10.0.2.2:13378, the dev container's Audiobookshelf as the emulator sees it.
set -euo pipefail

cmd="${1:-status}"
host="${2:-10.0.2.2}"
port="${3:-13378}"

# iptables as root through the image's su. Not `adb root`: that restarts adbd,
# which drops every adb client, scrcpy included.
iptables() { adb shell "su 0 iptables $*"; }

rule() { echo "OUTPUT -p tcp -d $host --dport $port -j $1"; }

drop_rules() {
  for target in REJECT DROP; do
    while iptables -D "$(rule "$target")" 2> /dev/null; do :; done
  done
}

case "$cmd" in
  server-down)
    drop_rules
    iptables -I "$(rule REJECT)"
    echo "Server $host:$port now refuses connections"
    ;;
  server-hang)
    drop_rules
    iptables -I "$(rule DROP)"
    echo "Server $host:$port now drops packets (timeouts)"
    ;;
  server-up)
    drop_rules
    echo "Server $host:$port reachable"
    ;;
  wifi-off) adb shell svc wifi disable ;;
  wifi-on) adb shell svc wifi enable ;;
  data-off) adb shell svc data disable ;;
  data-on) adb shell svc data enable ;;
  offline) adb shell svc wifi disable; adb shell svc data disable ;;
  online) adb shell svc wifi enable; adb shell svc data enable ;;
  status)
    echo "Server rules:"
    iptables -S OUTPUT | grep -- '--dport' || echo "  none"
    echo "Default network:"
    adb shell "dumpsys connectivity" | grep -m1 -E 'Active default network' || true
    ;;
  *)
    sed -n '2,15p' "$0"
    exit 2
    ;;
esac
