# NoZapret v2.3.0 - Advanced DPI Bypass for Android

NoZapret is a powerful, coroutine-based Android application designed to bypass Deep Packet Inspection (DPI) using the `byedpi` engine. It works either as a system-wide VPN or a local SOCKS5 proxy.

## Key Features in v2.3.0

### 1. Stability & Connectivity
- **Stability Fix**: Resolved a critical issue where the VPN would unexpectedly disconnect after several seconds on specific strategies.
- **Application Modes**: Added **Proxy Mode** (SOCKS5), allowing you to run the engine without a system-wide TUN interface.
- **Improved Site Tester**: Enhanced verification engine with detailed TLS/HTTP results and robust timeout handling.

### 2. User Interface Enhancements
- **Redesigned Home Screen**: A more accessible, centered toggle button and a refined layout for better visual balance.
- **Language Support**: Added official support for English, Russian, Ukrainian, and Kazakh. Includes a fixed language selector that updates the UI immediately.
- **Scrollable Menus**: Optimized the strategy selection and settings menus for better navigation with large lists.
- **App Shortcuts**: Support for static shortcuts (long-press app icon) to quickly Start or Stop the service.

### 3. Performance & Size Optimization
- **Aggressive Stripping**: Release builds are optimized using R8 Full Mode, ABI filtering (ARM only), and bytecode-level log stripping to ensure the smallest possible APK size.
- **Per-App Language Settings**: Integrated with modern Android locale APIs for seamless language management.

## Key Features in v1.4.x

### 1. Interactive System Diagnostics
A comprehensive diagnostic suite to identify and resolve connectivity issues:
- **Automatic Checks**: Native library integrity, battery optimization, network conflicts, IPv6 detection, and MTProto reachability.
- **One-Click Fixes**: Interactive "Fix" buttons to guide users through troubleshooting steps.

### 2. Optimized Strategy Testing Engine
- **Parallel Testing**: Evaluate multiple strategies (e.g., `Split`, `Disorder`, `Fake`) without interrupting your active connection.
- **TLS Handshake Insights**: View detailed results including ping, protocol version, and cipher suites.

### 3. High-Performance Log Viewer
- **Real-time Streaming**: Integrated `logcat` viewer with batch UI updates to maintain responsiveness during high-frequency logging.

## Technical Details

- **Core**: 100% Kotlin with Coroutines & Flow.
- **Native Engine**: `byedpi` (compiled as a shared library) and `hev-socks5-tunnel`.
- **UI**: Jetpack Compose (Material 3).
- **Data**: Jetpack DataStore.
- **Requirements**: Android 11 (API 30) or higher. Supports Android 15+.

## Troubleshooting
If you experience issues:
1. Run the **Diagnostics** tool from the settings menu.
2. Use the **Fix** buttons for any reported Warnings or Failures.
3. Use the **Site Tester** to verify which strategy works best for your specific ISP.
4. Check the **Log Viewer** for native engine logs.
