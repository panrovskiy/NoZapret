# NoZapret Full System Fix Plan

This plan addresses five critical issues: Connection failure after testing, missing Custom UI controls, update download failures, lack of persistent logging, and incorrect VPN icon in SOCKS5 mode.

## User Review Required

> [!IMPORTANT]
> - **SOCKS5 Mode Change**: SOCKS5 mode will no longer establish a system-wide VPN tunnel. It will only start the local proxy backend. Users will need to configure their apps to use the proxy (127.0.0.1:1080) manually if they select this mode.
> - **Log Storage**: Logs will be stored in the app's private internal storage (`/files/logs/`). These will be accessible via a new Export button in the settings.

## Proposed Changes

### 1. Strategy Testing & Connection Lifecycle Fixes

#### [MODIFY] [StrategyTester.kt](file:///C:/Users/1/AndroidStudioProjects/NoZapret/app/src/main/java/com/example/nozapret/core/StrategyTester.kt)
- Add `jniCleanup()` call in the `finally` block of `testStrategy` to reset native state.
- Ensure all resources (sockets, jobs) are strictly closed.
- Add explicit logging for each cleanup step.

#### [MODIFY] [DpiVpnService.kt](file:///C:/Users/1/AndroidStudioProjects/NoZapret/app/src/main/java/com/example/nozapret/services/DpiVpnService.kt)
- Improve `stopVpnAsync` to be more robust.
- Add detailed logging for start/stop sequences.
- Ensure `jniCleanup()` is called whenever the service stops.

### 2. Custom Strategy UI Controls

#### [MODIFY] [SettingsTab.kt](file:///C:/Users/1/AndroidStudioProjects/NoZapret/app/src/main/java/com/example/nozapret/ui/screens/SettingsTab.kt)
- Add "Paste" and "Clear" buttons next to the Custom Arguments text field.
- Localize button labels.
- Implement paste from clipboard and clear logic.

### 3. Update Download Reliability

#### [MODIFY] [UpdateManager.kt](file:///C:/Users/1/AndroidStudioProjects/NoZapret/app/src/main/java/com/example/nozapret/core/UpdateManager.kt)
- Recreate `OkHttpClient` for each request to avoid network binding issues.
- Implement streaming download to file with `.part` extension.
- Add SHA-256 verification (if possible from GitHub API) or at least basic integrity checks.
- Add detailed logging for network failures (DNS, Connect, TLS).

### 4. Persistent Logging System

#### [NEW] [AppLogger.kt](file:///C:/Users/1/AndroidStudioProjects/NoZapret/app/src/main/java/com/example/nozapret/core/AppLogger.kt)
- Centralized logging utility.
- Writes to `Logcat` AND a persistent file in `context.filesDir/logs/`.
- Handles log rotation (max 5 files, 2MB each).
- Thread-safe and asynchronous file writing.

#### [MODIFY] [NoZapretApp.kt](file:///C:/Users/1/AndroidStudioProjects/NoZapret/app/src/main/java/com/example/nozapret/NoZapretApp.kt)
- Initialize `AppLogger` on app startup.
- Set up `UncaughtExceptionHandler` to log crashes.

### 5. SOCKS5 Mode Architecture Fix

#### [MODIFY] [DpiVpnService.kt](file:///C:/Users/1/AndroidStudioProjects/NoZapret/app/src/main/java/com/example/nozapret/services/DpiVpnService.kt)
- Check `runMode` before calling `builder.establish()`.
- If `runMode == "Proxy"`, skip VPN interface creation and tunnel startup.
- Only start the `ByeDpiProxy` backend in Proxy mode.
- Update notification to reflect "Proxy Mode".

## Verification Plan

### Automated Tests
- Run `gradle build` to ensure no syntax errors.
- (If available) Run existing unit tests.

### Manual Verification
1. **Strategy Test -> Connect**: Verify VPN connects immediately after a full strategy test suite.
2. **Custom UI**: Verify Paste and Clear buttons work as expected.
3. **Update**: Trigger a fake update or check logs to verify network path.
4. **Logging**: Check `/files/logs/` via Device Explorer to ensure logs are being written.
5. **SOCKS5**: Verify no VPN icon appears in "Proxy" mode, but the backend still starts (check via Logcat/netstat).
