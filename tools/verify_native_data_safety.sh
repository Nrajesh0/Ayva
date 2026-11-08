#!/usr/bin/env bash
set -euo pipefail
mkdir -p build/native-verification
collect_evidence() {
  set +e
  adb logcat -d -t 500 > build/native-verification/logcat.txt
  adb shell dumpsys gfxinfo com.focusbyrj.app > build/native-verification/gfxinfo.txt
  if adb shell test -d /sdcard/Android/data/com.focusbyrj.app/files; then
    adb pull /sdcard/Android/data/com.focusbyrj.app/files build/native-verification/screenshots/ > build/native-verification/screenshot-copy.txt 2>&1
  fi
}
trap 'native_exit_code=$?; collect_evidence; exit "$native_exit_code"' EXIT
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
runner=com.focusbyrj.app.test/androidx.test.runner.AndroidJUnitRunner
class=com.focusbyrj.app.util.backup.RestoreRecoveryDeviceTest
run_test() {
  local target="$1" evidence="$2"
  adb shell am instrument -w -r -e class "$target" "$runner" > "$evidence" 2>&1
  cat "$evidence"
  grep -Eq '^OK \([1-9][0-9]* test' "$evidence"
  ! grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=' "$evidence"
}
run_test "$class#nativeKdfAndAndroidKeyStoreRejectTampering" build/native-verification/crypto.txt
run_test com.focusbyrj.app.util.backup.AuxiliaryEncryptionDeviceTest build/native-verification/stores.txt
run_test "$class#snapshotRestoreKeepsVaultEnabledAndDoesNotDuplicate" build/native-verification/snapshot-restore.txt
# This invocation intentionally terminates. Only the fsynced checkpoint plus the
# following independent recovery assertions can establish that it reached the target.
adb shell am instrument -w -r -e class "$class#terminateDuringRestore" "$runner" > build/native-verification/termination.txt 2>&1 || true
adb exec-out run-as com.focusbyrj.app cat no_backup/native_restore_test.json > build/native-verification/termination-checkpoint.json
python3 - <<'PY'
import json
with open('build/native-verification/termination-checkpoint.json') as f:
    assert json.load(f)['terminatedAt'] == 'before_commit'
PY
run_test "$class#verifyStartupRecovery" build/native-verification/recovery.txt
# Existing IME/back/recreation, large-text and native-artwork fixtures also need
# real Android execution; they are intentionally separate from the kill/restart pair.
sdk_level="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
if [ "$sdk_level" -ge 33 ]; then adb shell pm grant com.focusbyrj.app android.permission.POST_NOTIFICATIONS; fi
adb shell appops set com.focusbyrj.app GET_USAGE_STATS allow
adb shell appops set com.focusbyrj.app SYSTEM_ALERT_WINDOW allow
ui_classes=com.focusbyrj.app.ui.TaskSheetBackDeviceTest,com.focusbyrj.app.ui.QuickAddBackDeviceTest,com.focusbyrj.app.ui.ChatModeDeviceTest,com.focusbyrj.app.ui.HabitSheetBackDeviceTest,com.focusbyrj.app.ui.MainNoteBackDeviceTest,com.focusbyrj.app.ui.QuickNoteBackDeviceTest,com.focusbyrj.app.ui.chat.FocusedDiscoveryDeviceTest,com.focusbyrj.app.ui.components.SmokePawChestDeviceTest
run_test "$ui_classes" build/native-verification/editor-and-ui.txt
adb shell am force-stop com.focusbyrj.app
adb shell am start -W -n com.focusbyrj.app/.MainActivity > build/native-verification/cold-start.txt
