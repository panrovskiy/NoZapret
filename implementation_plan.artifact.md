# Implementation Plan - NoZapret Stability and UI Overhaul

Deep audit and repair of the NoZapret Android project to address functional crashes, UI scrolling issues, theme inconsistencies, and localization gaps.

## User Review Required

> [!IMPORTANT]
> The fix for the strategy test crash involves adding a native mutex in the JNI layer. This will synchronize all calls to the `byedpi` engine across different components (VPN Service and Testing Service). This ensures that only one instance of the engine is being initialized or cleaned up at a time, preventing memory corruption and race conditions.

> [!NOTE]
> The main connection screen layout will be updated to remove vertical scrolling. On very small screens, some elements might be slightly tighter, but the goal is to keep all essential controls visible without scrolling.

## Proposed Changes

### Native Layer (JNI)

#### [MODIFY] [native-lib.cpp](file:///C:/Users/1/AndroidStudioProjects/NoZapret/app/src/main/cpp/native-lib.cpp)
- Introduce a global `std::mutex g_proxy_mutex` to synchronize all `byedpi` operations.
- Wrap `jniStartProxy`, `jniStopProxy`, and `jniForceClose` logic inside this mutex.
- Ensure `reset_params` and `clear_params` are always called within the locked context.

### Core Logic

#### [MODIFY] [VpnController.kt](file:///C:/Users/1/AndroidStudioProjects/NoZapret/app/src/main/java/com/example/nozapret/core/VpnController.kt)
- Improve `runWithVpnStopped` to be more robust by ensuring a complete stop of both the VPN service and the underlying native proxy before proceeding with testing.

#### [MODIFY] [DpiVpnService.kt](file:///C:/Users/1/AndroidStudioProjects/NoZapret/app/src/main/java/com/example/nozapret/services/DpiVpnService.kt)
- Ensure `isRunning` state reflects the full lifecycle, including native cleanup.

### UI Components & Screens

#### [MODIFY] [HomeTab.kt](file:///C:/Users/1/AndroidStudioProjects/NoZapret/app/src/main/java/com/example/nozapret/ui/screens/HomeTab.kt)
- Remove `verticalScroll` from the main `Column`.
- Adjust `Spacer` weights and bottom padding to ensure the UI fits within the viewport on most devices.
- Refine the layout to be more responsive to available height.

#### [MODIFY] [SettingsTab.kt](file:///C:/Users/1/AndroidStudioProjects/NoZapret/app/src/main/java/com/example/nozapret/ui/screens/SettingsTab.kt)
- Remove `bouncingClickable` from Material Button components (`Button`, `OutlinedButton`, `FilledTonalButton`) to resolve background color issues and redundant click listeners.
- Preserve `bouncingClickable` for `ListItem` and other non-button interactive elements.

#### [MODIFY] [BouncingClickable.kt](file:///C:/Users/1/AndroidStudioProjects/NoZapret/app/src/main/java/com/example/nozapret/ui/components/BouncingClickable.kt)
- Add a check to ensure `indication` is handled correctly if it needs to be restored, though currently it is intentionally `null` for the bounce effect.

### Localization

#### [MODIFY] [strings.xml (ru)](file:///C:/Users/1/AndroidStudioProjects/NoZapret/app/src/main/res/values-ru/strings.xml)
#### [MODIFY] [strings.xml (uk)](file:///C:/Users/1/AndroidStudioProjects/NoZapret/app/src/main/res/values-uk/strings.xml)
#### [MODIFY] [strings.xml (kk)](file:///C:/Users/1/AndroidStudioProjects/NoZapret/app/src/main/res/values-kk/strings.xml)
- Translate all missing user-facing strings (e.g., diagnostic messages, new settings labels, tester results).

## Verification Plan

### Automated Tests
- Run `gradle build` to ensure no regressions in compilation or resources.
- Run unit tests if available (none specified as critical, but general build check is mandatory).

### Manual Verification
1. **Crash Test**:
   - Select Strategy A.
   - Start VPN.
   - Go to "Strategy Test" or "Site Tester".
   - Test Strategy A.
   - Verify no crash occurs and the test proceeds correctly.
2. **UI Audit**:
   - Open Home screen: Verify it does not scroll and all elements are visible.
   - Open Settings: Verify all buttons (Diagnostics, Updates, etc.) have correct backgrounds (not black) and ripple effects.
3. **Localization Check**:
   - Switch language to Russian, Ukrainian, and Kazakh.
   - Verify all visible text is correctly translated and formatted.
