# NoZapret Reliability and UI Overhaul Plan

This plan addresses critical VPN connection failures after strategy testing, improves the Strategy Test UI by implementing smooth reordering, and fixes unintended black backgrounds on Settings buttons.

## User Review Required

> [!IMPORTANT]
> The fix for VPN connection involves stricter resource management and potentially slightly longer wait times when transitioning from Test to VPN mode to ensure native resources are fully released.

## Proposed Changes

### Core Lifecycle & Reliability (Problem 1)

#### [MODIFY] [StrategyTester.kt](file:///C:/Users/1/AndroidStudioProjects/NoZapret/app/src/main/java/com/example/nozapret/core/StrategyTester.kt)
- Enhance `testStrategy` cleanup logic to ensure the proxy is fully stopped before returning.
- Implement a more robust `proxy.forceClose()` call and verification.
- Ensure `isTesting` state is correctly reset in all exit paths.

#### [MODIFY] [TestingService.kt](file:///C:/Users/1/AndroidStudioProjects/NoZapret/app/src/main/java/com/example/nozapret/services/TestingService.kt)
- Add explicit cleanup in `onDestroy`.
- Ensure `isRunning` flag is cleared reliably.

#### [MODIFY] [VpnController.kt](file:///C:/Users/1/AndroidStudioProjects/NoZapret/app/src/main/java/com/example/nozapret/core/VpnController.kt)
- Add a new method to stop all tests and wait for cleanup.
- Enhance `runWithVpnStopped` to be more aggressive in verifying that ports and processes are released.

#### [MODIFY] [MainActivity.kt](file:///C:/Users/1/AndroidStudioProjects/NoZapret/app/src/main/java/com/example/nozapret/MainActivity.kt)
- Update `onToggleVpn` to use the new synchronized test stopping logic.
- Ensure `VpnController` is the source of truth for stopping tests before starting VPN.

---

### Strategy Test UI (Problem 2)

#### [MODIFY] [SettingsTab.kt](file:///C:/Users/1/AndroidStudioProjects/NoZapret/app/src/main/java/com/example/nozapret/ui/screens/SettingsTab.kt)
- Update the sorting logic for `allStrats`:
    1.  `currentlyTesting` items move to the top.
    2.  Completed items sorted by success count (descending).
    3.  Deterministic tie-breaker (alphabetical).
- Remove `FadeEntrance` from strategy list items to prevent fade-in/out flicker during reordering.
- Add `Modifier.animateItemPlacement()` to strategy cards for smooth movement.

---

### UI Styling (Problem 3)

#### [MODIFY] [SettingsTab.kt](file:///C:/Users/1/AndroidStudioProjects/NoZapret/app/src/main/java/com/example/nozapret/ui/screens/SettingsTab.kt)
- Inspect and fix `Button` and `SegmentedButton` colors.
- Ensure `surfaceContainerLow` in `SettingsGroup` is correctly defined or overridden if it causes black backgrounds in certain themes.
- Check `ExposedDropdownMenuDefaults` and `OutlinedTextField` colors.

---

### Diagnostics & Logging

- Add structured debug logs prefixed with `[TEST]`, `[VPN]`, `[CLEANUP]` to trace the lifecycle transitions.

## Verification Plan

### Automated Tests
- Build the project to ensure no compilation errors.

### Manual Verification
1.  **Sequence TEST → VPN**: Run a strategy test, wait for completion, then start VPN. Verify VPN starts.
2.  **Sequence TEST MULTIPLE → VPN**: Run multiple tests, then start VPN.
3.  **Sequence TEST CANCEL → VPN**: Start a test, cancel it, then immediately start VPN.
4.  **UI Animation**: Observe the strategy list during testing. The testing strategy should slide to the top. After completion, it should slide to its ranked position.
5.  **Settings UI**: Check all buttons in Settings in both Light and Dark modes to ensure no black backgrounds.
