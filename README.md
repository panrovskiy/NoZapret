# NoZapret - Advanced DPI Bypass for Android

NoZapret is a powerful, coroutine-based Android application designed to bypass Deep Packet Inspection (DPI) using the `byedpi` engine. It works either as a system-wide VPN or a local SOCKS5 proxy.

## Technical Details

- **Core**: 100% Kotlin with Coroutines & Flow.
- **Native Engine**: `byedpi` (compiled as a shared library) and `hev-socks5-tunnel`.
- **UI**: Jetpack Compose (Material 3).
- **Data**: Jetpack DataStore.
- **Requirements**: Android 11 (API 30) or higher. Supports Android 17+.

## Troubleshooting
If you experience issues:
1. Run the **Diagnostics** tool from the settings menu.
2. Use the **Fix** buttons for any reported Warnings or Failures.
3. Use the **Site Tester** to verify which strategy works best for your specific ISP.
4. Check the **Log Viewer** for native engine logs.
