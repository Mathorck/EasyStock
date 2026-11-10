# Tooling fixes (WSL + Android Studio MCP)

- Gradle can't run from WSL (no Android SDK). Compile with the Android Studio MCP `build_project`.
- Run Gradle tasks (tests) with the MCP `execute_terminal_command`, as a process, not in shell mode (shell mode fails on the pwsh path):
  `cmd.exe /c C:\Users\Admin.MMO\AndroidStudioProjects\StockApp2\gradlew.bat -p C:\Users\Admin.MMO\AndroidStudioProjects\StockApp2 :shared:testAndroidHostTest --console=plain`
- Its output gets truncated. Read results from `shared/build/test-results/**/*.xml` instead.
