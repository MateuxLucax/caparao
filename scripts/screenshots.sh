#!/usr/bin/env bash
# Refreshes docs/screenshots from a running emulator: the onboarding and every tab, in English and Portuguese, light and dark.
# The status bar is put in demo mode (fixed clock, full battery, no notifications) so the images don't change for
# no reason. Images are scaled to 540 px wide to keep the repository small.
set -euo pipefail
cd "$(dirname "$0")/.."

adb="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
app=io.github.mateuxlucax.caparao.debug
device_dir=/sdcard/Android/data/$app/files/screenshots
out=docs/screenshots

# The emulator by default, so a connected phone is never touched; set ANDROID_SERIAL to pick another device.
export ANDROID_SERIAL="${ANDROID_SERIAL:-$("$adb" devices | awk '/^emulator-/ { print $1; exit }')}"
[ -n "$ANDROID_SERIAL" ] || { echo "no emulator running (or set ANDROID_SERIAL)" >&2; exit 1; }
./gradlew --console=plain -q assembleDebug assembleDebugAndroidTest
"$adb" install -r -t app/build/outputs/apk/debug/app-debug.apk >/dev/null
"$adb" install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk >/dev/null

demo() { "$adb" shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null; }
"$adb" shell settings put global sysui_demo_allowed 1
demo enter
demo clock -e hhmm 1000
demo battery -e level 100 -e plugged false
demo network -e wifi show -e level 4 -e mobile show -e level 4
demo notifications -e visible false
# Animations off: the welcome animation then shows its still frame (a full cup) instead of an arbitrary one.
scale=$("$adb" shell settings get global animator_duration_scale)
"$adb" shell settings put global animator_duration_scale 0

"$adb" shell rm -rf "$device_dir"
for lang in en pt-BR; do
  for theme in light dark; do
    "$adb" shell cmd locale set-app-locales "$app" --locales "$lang"
    "$adb" shell cmd uimode night "$([ "$theme" = dark ] && echo yes || echo no)" >/dev/null
    "$adb" shell am force-stop "$app"
    "$adb" shell am instrument -w -e screenshots "$lang-$theme" \
      -e class io.github.mateuxlucax.caparao.ui.ScreenshotTest "$app.test/androidx.test.runner.AndroidJUnitRunner" | grep -q "OK (4 tests)" \
      || { echo "screenshot run failed for $lang-$theme" >&2; exit 1; }
  done
done

"$adb" shell cmd uimode night no >/dev/null
demo exit
"$adb" shell settings put global animator_duration_scale "${scale/null/1}"

tmp=$(mktemp -d)
"$adb" pull -q "$device_dir" "$tmp"
for lang in en pt-BR; do
  mkdir -p "$out/$lang"
  for f in "$tmp"/screenshots/"$lang"-*/*.png; do
    theme=$(basename "$(dirname "$f")"); theme=${theme#"$lang"-}
    sips -Z 1210 "$f" --out "$out/$lang/$(basename "$f" .png)-$theme.png" >/dev/null
  done
done
rm -rf "$tmp"
echo "Screenshots in $out/"
