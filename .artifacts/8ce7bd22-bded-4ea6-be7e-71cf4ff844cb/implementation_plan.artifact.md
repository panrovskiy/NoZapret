# Fix Connectivity, Updates, and Versioning

This plan addresses the 404 error in update checks, the VPN connectivity issues (deadlocks and binding), and updates the versioning/changelogs for the upcoming fix release.

## User Review Required

> [!IMPORTANT]
> The VPN connectivity fix involves removing `synchronized` blocks in JNI wrappers to prevent deadlocks during service stop/start. This assumes the native libraries are thread-safe for signaling stops while a start command is blocking, which the current `native-lib.c` implementation supports via atomic flags.

## Proposed Changes

### 1. Update Check & Versioning

#### [MODIFY] [MainViewModel.kt](file:///C:/Users/1/AndroidStudioProjects/NoZapret/app/src/main/java/com/example/nozapret/MainViewModel.kt)
- Fix `GITHUB_API_URL` to point to the correct owner `panrovskiy`.

#### [MODIFY] [strings.xml](file:///C:/Users/1/AndroidStudioProjects/NoZapret/app/src/main/res/values/strings.xml)
- Add `v2.3.1-fix2` to `changelog_content` with the today\'s date (August 22, 2026).
- Include fixes for connectivity and update system in the changelog.

#### [MODIFY] [build.gradle.kts](file:///C:/Users/1/AndroidStudioProjects/NoZapret/app/build.gradle.kts)
- Update `versionName` to `2.3.1-fix2`.
- Increment `versionCode` to `13`.

### 2. VPN Connectivity & Deadlock Fix

#### [MODIFY] [ByeDpiProxy.kt](file:///C:/Users/1/AndroidStudioProjects/NoZapret/app/src/main/java/com/example/nozapret/core/ByeDpiProxy.kt)
- Remove `synchronized(Lock)` from `start()` and `stop()` to prevent deadlocks when `start()` is blocking.

#### [MODIFY] [HevSocks5Tunnel.kt](file:///C:/Users/1/AndroidStudioProjects/NoZapret/app/src/main/java/com/example/nozapret/core/HevSocks5Tunnel.kt)
- Remove `synchronized(Lock)` from `start()` and `stop()` to prevent deadlocks when `start()` is blocking.

#### [MODIFY] [DpiVpnService.kt](file:///C:/Users/1/AndroidStudioProjects/NoZapret/app/src/main/java/com/example/nozapret/services/DpiVpnService.kt)
- Improve logging and state management during `startVpn`.
- Ensure `stopForeground` is called if `establish()` fails.

## Verification Plan

### Automated Tests
- Run `gradlew assembleDebug` to ensure compilation is successful.

### Manual Verification
- **Check for Updates**: Trigger update check and verify it no longer returns 404 (should show "Latest version" if no new release is on GitHub).
- **VPN Connection**: Select a strategy and connect. Verify the VPN icon appears and traffic is routed.
- **Reconnect**: Disconnect and reconnect immediately to ensure no deadlocks or port binding issues.
- **Changelog**: Verify the new entry appears in the app's changelog dialog.
