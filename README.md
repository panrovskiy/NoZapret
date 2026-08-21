# NoZapret v2.0.0 - Advanced DPI Bypass for Android

NoZapret is a powerful, coroutine-based Android VPN application designed to bypass Deep Packet Inspection (DPI) and restore access to restricted services.

## Key Features in v2.0.0

### 1. Massive Size Optimization
- **R8 Full Mode**: Enabled aggressive code shrinking and optimization using R8's most advanced mode.
- **Resource Stripping**: Minimized APK size by stripping unnecessary localizations (keeping only English and Russian) and removing redundant system resources.
- **ABI Filtering**: Removed legacy CPU architectures (x86, x86_64) to focus on modern mobile devices, significantly reducing the final binary size.
- **Bytecode Stripping**: All debug and information log calls (`Log.d`, `Log.i`, `Log.v`) are now completely stripped at the bytecode level in release builds.
- **Metadata Cleanup**: Removed unnecessary Kotlin metadata and ProGuard attributes to save every possible kilobyte.

## Key Features in v1.4.1

### 1. Protocol & Legacy Cleanup
- **HTTP Strategy Verification**: Added full support for plain HTTP testing in the strategy engine to ensure connectivity for non-TLS services.
- **Decommissioned Roblox**: Removed the legacy Roblox preset and all associated infrastructure mappings.
- **Optimized UI & Logic**: Purged redundant traffic statistics logic and unused formatting helpers, reducing the app's footprint.

## Key Features in v1.4.0

### 2. Modernized VPN Architecture
- **Coroutine-Powered Lifecycle**: The `DpiVpnService` has been fully migrated to Kotlin Coroutines, ensuring non-blocking operations and efficient resource management.
- **Thread-Safe State Management**: Utilizes a `vpnLock` Mutex to prevent race conditions during service startup and shutdown.
- **Clean Resource Release**: Robust native resource cleanup using `cancelAndJoin` and `NonCancellable` blocks, preventing zombie processes (ByeDpi/HevSocks5).

### 3. Interactive System Diagnostics
A comprehensive diagnostic suite to identify and resolve connectivity issues:
- **Automatic Checks**:
    - Native library integrity (`libbyedpi.so`).
    - Battery optimization status.
    - Network capabilities and VPN conflicts.
    - IPv6 detection (which can sometimes interfere with bypass strategies).
    - DNS configuration and Local Proxy (127.0.0.1:1081) reachability.
    - MTProto reachability for Telegram.
- **One-Click Fixes**: Interactive "Fix" buttons that guide users to system settings or automatically resolve configuration issues.

### 4. Optimized Strategy Testing Engine (TLS)
Find the most effective bypass strategy for your network environment:
- **Parallel Testing**: Leverages SOCKS5 testing to evaluate multiple strategies (e.g., `Split`, `Disorder`, `Fake`) without interrupting the main VPN connection.
- **Defensive Networking**: 
    - **Concurrency Control**: Implements `Semaphore(2)` to prevent proxy congestion.
    - **Watchdog Timer**: A 45-second `withTimeout` safeguard prevents native socket hangs.
- **Detailed Insights**: View TLS handshake results, including ping, protocol version, and cipher suites.

### 5. High-Performance Log Viewer
- **Real-time Streaming**: Integrated `logcat` viewer for real-time debugging.
- **UI Batching**: Implements a 1-second/20-line buffer with `SnapshotStateList` to ensure the UI remains responsive even during high-frequency logging.

### 6. Enhanced Customization
- **Material 3 UI**: Modern design with dynamic theming support.
- **Custom Presets**: Easily manage bypass lists for YouTube, Telegram, and other services.
- **Advanced Args**: Full control over `byedpi` parameters for power users.

## Technical Details

- **Language**: 100% Kotlin
- **Concurrency**: Kotlin Coroutines & Flow
- **Native Components**: `byedpi` (shared library), `hev-socks5-tunnel`
- **Data Persistence**: Jetpack DataStore
- **Minimum SDK**: 26 (Android 8.0)

## Troubleshooting
If you experience issues:
1. Run the **Diagnostics** tool from the main menu.
2. Use the **Fix** buttons for any reported Warnings or Failures.
3. Try different **Strategies** using the built-in Tester.
4. Check the **Log Viewer** for specific native error messages.
