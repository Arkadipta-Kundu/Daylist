#!/usr/bin/env bash
set -eu
if [[ "${1:-}" != "--prebuilt" ]]; then
  gradle --no-daemon --stacktrace assembleDebug assembleDebugAndroidTest
fi
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
mkdir -p visual
for profile in light dark-large; do
  adb shell pm clear dev.daylist
  adb shell pm grant dev.daylist android.permission.POST_NOTIFICATIONS
  if [[ "$profile" == dark-large ]]; then
    adb shell wm size 720x1280
    adb shell wm density 320
    adb shell settings put system font_scale 1.5
    adb shell cmd uimode night yes
  else
    adb shell cmd uimode night no
  fi
  adb shell am instrument -w -e variant "$profile" dev.daylist.test/androidx.test.runner.AndroidJUnitRunner | tee "visual/$profile.log"
  adb pull /sdcard/Android/data/dev.daylist/files/visual/. visual/ || true
  grep -Eq '^OK [(][1-9][0-9]* tests?[)]' "visual/$profile.log"
done
