# Walkthrough: NoZapret Production Enhancements

All planned features and bugfixes have been successfully implemented and verified with a clean build (`app:assembleDebug`).

---

## Changes Made

### 1. Editable Preset Management System
- **Domain Validation & Normalization (`Config.kt`):** Added robust hostname sanitization and validation (`Config.validateDomain`) that strips protocol prefixes (`http://`, `https://`), trailing slashes, paths, and ports, while preventing duplicates and malformed entries.
- **Persistence (`DataStoreManager.kt` & `SettingsManager.kt`):** Added `PRESET_OVERRIDES` storage to persist user modifications for every preset (YouTube, Discord, Telegram, Cloudflare, Socials, Torrent/Tools, etc.).
- **Editor UI (`PresetEditorDialog.kt` & `SettingsTab.kt`):** Created a unified preset domain editor supporting Add, Edit, Remove, Clear List, and Restore Defaults with live domain counts.
- **Testing Integration (`MainViewModel.kt`):** Updated `bypassedSites` to seamlessly incorporate user-edited preset lists so Strategy and Site tests immediately evaluate modified domains.

### 2. Configurable Decoy Fake SNI Pool
- **Separation of Concerns:** Separated target domains from Fake SNI decoy hosts.
- **Configurable Pool:** Managed via `SettingsManager` and persisted in DataStore (`FAKE_SNI_POOL`), defaulting to `www.google.com`, `yandex.ru`, `apple.com`, `wikipedia.org`.
- **Strategy Integration (`Config.kt`):** Strategies using Fake SNI dynamically inject the configured pool, while strategies without Fake SNI remain untouched.
- **Info Dialog & Testing:** `StrategyInfoDialog`, `StrategyTester`, and `DpiVpnService` reflect exact execution arguments.

### 3. AndroidManifest & Lint Cleanup
- Removed unused `RECEIVE_BOOT_COMPLETED` permission.
- Added `tools:targetApi="33"` on `localeConfig` to resolve SDK min/target level warnings.
- Added `"tr"` to `localeFilters` in `app/build.gradle.kts`.

### 4. Turkish (`tr`) Localization
- Registered `tr` locale in `locales_config.xml` and added complete Turkish translations in `values-tr/strings.xml`.
- Audited and added missing string keys across English, Russian, Kazakh, and Ukrainian locales.

### 5. App Theme Selection & System/Light/Dark
- Preserved Material 3 theme modes (System, Light, Dark) with immediate reactivity and persistence.

### 6. About & Version Section in Settings
- Added an About section at the end of Settings displaying NoZapret version (`2.3.5` from `BuildConfig.VERSION_NAME`) and ByeDPI engine version (`17.3`), with direct links opening official GitHub repositories (`https://github.com/panrovskiy/NoZapret` and `https://github.com/hagil/byedpi`).

### 7. YouTube Long-Run Connection Stability
- Updated `tunnel.yaml` in `DpiVpnService.kt` to increase `read-write-timeout` to 300,000ms (5 minutes) and `udp-read-write-timeout` to 60,000ms, preventing `hev-socks5-tunnel` from abruptly tearing down idle TCP/QUIC sessions during YouTube buffering pauses.

---

## Verification Results

### Build Status
- **BUILD VERIFIED:** `./gradlew assembleDebug` completed successfully with 0 errors.
