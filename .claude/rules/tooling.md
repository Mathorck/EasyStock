# Tooling fixes (WSL + Android Studio MCP)

- Gradle can't run from WSL (no Android SDK). Compile with the Android Studio MCP `build_project`.
- Run Gradle tasks (tests) with the MCP `execute_terminal_command`, as a process, not in shell mode (shell mode fails on the pwsh path):
  `cmd.exe /c C:\Users\Admin.MMO\AndroidStudioProjects\StockApp2\gradlew.bat -p C:\Users\Admin.MMO\AndroidStudioProjects\StockApp2 :shared:testAndroidHostTest --console=plain`
- Its output gets truncated. Read results from `shared/build/test-results/**/*.xml` instead.
- The MCP command times out after 10 min. For long tasks, run from WSL in the background: `cmd.exe /c "gradlew.bat <task> --console=plain"` from the repo root.
- Device tests (`shared/src/androidDeviceTest`): `:shared:connectedAndroidDeviceTest` reports "Process crashed" ("No UID for androidx.test.services"), but the tests are fine. Run them directly instead:
  1. `adb.exe -s <device> install -r -t shared/build/outputs/apk/androidTest/shared-androidTest.apk` (built by that Gradle task)
  2. `adb.exe -s <device> shell am instrument -w ch.mathorck.easystock.shared.test/androidx.test.runner.AndroidJUnitRunner`
  3. `adb.exe -s <device> uninstall ch.mathorck.easystock.shared.test`
