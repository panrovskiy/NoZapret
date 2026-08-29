# Implementation Plan - Fix VPN Startup and Optimize Service

The goal is to fix the VPN startup timeout issue caused by an incorrect state check in `DpiVpnService`, improve the service lifecycle by ensuring proper cleanup on startup failure, and reduce main-thread blocking operations.

## User Review Required

> [!IMPORTANT]
> The changes in `DpiVpnService` affect the core VPN startup logic. While these fixes address identified bugs, they should be verified on the target device (SM-A366B) to ensure no regressions in connectivity.

## Proposed Changes

### [DpiVpnService]

#### [MODIFY] [DpiVpnService.kt](file:///C:/Users/1/AndroidStudioProjects/NoZapret/app/src/main/java/com/example/nozapret/services/DpiVpnService.kt)
- Fix `waitForProxy` to not exit early when `isRunning` is false (as it is during startup).
- Ensure `stopVpn` is called upon startup failure to clean up resources, regardless of the `isRunning` flag.
- Optimize `createNotification` to remove `runBlocking` and instead use a passed-in strategy name or a default value.
- Update the notification with the actual strategy name once it is fetched asynchronously in `startVpn`.

## Verification Plan

### Automated Tests
- I will deploy the modified app to the SM-A366B device and monitor the logcat to verify that:
    1. The proxy starts successfully.
    2. `waitForProxy` correctly identifies the ready state.
    3. The VPN service successfully transitions to the "Running" state.
    4. No `Proxy failed to start in time` errors occur unless there's a genuine timeout.

### Manual Verification
- Verify that the app UI updates to "Connected" correctly.
- Verify that the notification shows the correct strategy name.
- Trigger a stop action and verify that all native components and the VPN interface are closed properly.

## Release Build
- Once verified, I will execute the `assembleRelease` task to generate the release APK.
