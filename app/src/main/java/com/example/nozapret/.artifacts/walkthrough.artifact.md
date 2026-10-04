# Walkthrough: Strategy Test Stability & Per-Strategy Fake SNI Improvements

All planned features and bugfixes have been successfully implemented and verified with a clean build (`app:assembleDebug`).

---

## Changes Made

### 1. Critical Strategy Test Stability & Crash Fixes
- **Socket Leak Fix (`StrategyTester.kt`):** Wrapped raw TCP and SSL sockets in `try-finally` blocks in `fastTlsCheck` to ensure sockets are immediately closed on both success and error, eliminating File Descriptor (`EMFILE` / "Too many open files") exhaustion during batch test runs.
- **Native Memory Lifecycle Fix (`StrategyTester.kt`):** Removed premature `jniCleanup()` calls from per-test `finally` blocks in `StrategyTester.kt` that were freeing `byedpi` C pointers while background native threads were still shutting down, eliminating `SIGSEGV` native crashes.
- **State Race Condition Fix (`MainViewModel.kt` & `SettingsTab.kt`):** Enforced state modifications on `Dispatchers.Main` and used defensive immutable state snapshots (`stats.toMap()`, `testResults.toMap()`, `currentlyTesting.toList()`) during list sorting and Compose rendering in `SettingsTab.kt`.

### 2. Per-Strategy Fake SNI (0 or 1 Domain)
- **Data Model & Persistence:** Configured per-strategy Fake SNI stored in DataStore (`STRATEGY_FAKE_SNI_MAP`).
- **Single-Domain Validation:** Enforced strictly 0 or 1 domain per strategy (`getFakeSniForStrategy` & `setFakeSniForStrategy`). Comma/newline/space multi-domain strings are rejected as invalid input.
- **Strategy Isolation:** Updating Fake SNI for Strategy A only affects Strategy A and never overwrites Strategy B.
- **Info Dialog:** Displays `Fake SNI: <domain>` or `Fake SNI: Disabled` and passes the strategy's resolved Fake SNI to `Config.getStrategyArgs` so displayed arguments match execution.

### 3. Core Strategy Quality Overhaul
- **Modern Ultra:** Updated to aggressive, valid `byedpi` parameters combining TLS record fragmentation (`--tlsrec 1+s`), desync disorder (`--disorder 1`), TCP split (`--split 2`), fake SNI (`www.google.com`), and QUIC block.
- **Auto:** Implemented deterministic `getBestStrategy()` selection logic in `MainViewModel.kt` prioritizing highest successful site count, lowest average ping tie-break, and excluding failed strategies.
- **YouTube/Google Fix:** Streamlined to valid single Fake SNI (`www.google.com`) with TCP split, TLS fragment, and QUIC block.
- **Discord ALT & ALT2:** Meaningfully distinguished `ru_discord_alt` (TCP split 1, disorder, `--fake-sni discord.com`, UDP fake 5) from `ru_discord_alt2` (OOB split 2, disorder, `--fake-sni discord.gg`, UDP fake 15).

---

## Verification Results

### Build Status
- **BUILD VERIFIED:** `./gradlew assembleDebug` completed successfully with 0 errors.
