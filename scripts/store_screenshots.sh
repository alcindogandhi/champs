#!/usr/bin/env bash
# Gera as capturas da loja (fastlane/metadata/android/<idioma>/images/phoneScreenshots) num emulador ligado.
# Uso: scripts/store_screenshots.sh   (instala o build de debug e percorre as telas em pt-BR e en-US)
set -euo pipefail
ADB=${ADB:-$HOME/Android/Sdk/platform-tools/adb}
PKG=io.github.alcindogandhi.champs
ROOT=$(cd "$(dirname "$0")/.." && pwd)

tap_text() { # toca no centro do primeiro elemento cujo texto ou descrição é exatamente $1
  local bounds
  "$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null
  bounds=$("$ADB" shell cat /sdcard/ui.xml | grep -o "\(text\|content-desc\)=\"$1\"[^>]*bounds=\"[^\"]*\"" | head -1 | grep -o '\[[0-9,]*\]\[[0-9,]*\]' || true)
  [ -n "$bounds" ] || { echo "não achei: $1" >&2; return 1; }
  read -r x1 y1 x2 y2 < <(echo "$bounds" | grep -o '[0-9]\+' | tr '\n' ' '; echo)
  "$ADB" shell input tap $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 ))
}

shot() { sleep "${2:-4}"; "$ADB" exec-out screencap -p > "$OUT/$1.png"; }

"$ROOT/gradlew" -p "$ROOT" installDebug -q
for LOCALE in pt-BR en-US; do
  OUT="$ROOT/fastlane/metadata/android/$LOCALE/images/phoneScreenshots"
  mkdir -p "$OUT"
  "$ADB" shell pm clear "$PKG" >/dev/null
  "$ADB" shell cmd locale set-app-locales "$PKG" --locales "$LOCALE"
  "$ADB" shell am start -n "$PKG/.MainActivity" >/dev/null
  sleep 5
  if [ "$LOCALE" = pt-BR ]; then MENU="Abrir menu"; MATCHES="Jogos"; LINEUP="Escalação"; else MENU="Open menu"; MATCHES="Matches"; LINEUP="Lineups"; fi
  tap_text "$MENU"; sleep 1; tap_text "Brasileirão Série A"; shot 1 8
  tap_text "$MATCHES"; shot 2 8
  "$ADB" shell input tap 540 600; shot 3 6
  tap_text "$LINEUP"; shot 4 3
  "$ADB" shell input keyevent BACK; sleep 1
  tap_text "$MENU"; sleep 1; tap_text "Copa do Brasil"; sleep 6; tap_text "$MATCHES"; sleep 6
  if [ "$LOCALE" = pt-BR ]; then tap_text "Semifinais"; else tap_text "Semifinals"; fi
  shot 5 3
done
echo "Capturas gravadas em fastlane/metadata/android/*/images/phoneScreenshots"
