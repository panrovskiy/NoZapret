# Goal Description

Establish a clean, modern, and perfectly consistent Material 3 surface hierarchy for NoZapret. This plan eliminates accidental nested background containers, unwanted button backgrounds, and misaligned corner/clipping behaviors across the entire application while preserving core interactive elements like ripples and tactile animations.

## User Review Required

> [!IMPORTANT]
> - All section row backgrounds (`ListItem`) inside `SettingsGroup` containers will be set to `Color.Transparent` so that they uniformly blend into the group's surface (`surfaceContainerLow`).
> - The outer `Card` wrapper in the Presets section will be removed. The animated border highlight feature will be directly integrated into `SettingsGroup`, aligning Presets with the architectural pattern of all other settings sections.
> - `Modifier.bouncingClickable` will be updated to support the standard Material 3 ripple effect (`LocalIndication.current`) alongside its scaling animation to fulfill interactive and accessibility criteria.

## Open Questions

None at this stage, as the codebase audit reveals clear root causes for the styling bugs.

## Proposed Changes

---

### Core UI Components & Design System

#### [MODIFY] [SharedComponents.kt](file:///C:/Users/1/AndroidStudioProjects/NoZapret/app/src/main/java/com/example/nozapret/ui/components/SharedComponents.kt)
- Enhance [SettingsGroup](method://com.example.nozapret.ui.components.SharedComponentsKt#SettingsGroup) to accept an optional `border: BorderStroke? = null`.
- Apply `Modifier.clip(MaterialTheme.shapes.extraLarge)` to the internal `Surface` to ensure content does not leak beyond the rounded card bounds.
- Pass the optional `border` parameter to the `Surface`.

#### [MODIFY] [BouncingClickable.kt](file:///C:/Users/1/AndroidStudioProjects/NoZapret/app/src/main/java/com/example/nozapret/ui/components/BouncingClickable.kt)
- Change `indication = null` to `indication = androidx.compose.foundation.LocalIndication.current` to restore standard Material 3 ripple feedback when list items or buttons are tapped.

#### [MODIFY] [Theme.kt](file:///C:/Users/1/AndroidStudioProjects/NoZapret/app/src/main/java/com/example/nozapret/ui/theme/Theme.kt)
- Ensure the custom theme mode (`themeMode == "Custom"`) correctly derives all modern surface roles (such as `surfaceContainerLow` and `surfaceContainerHigh`) from the base Material 3 color palettes instead of leaving them uninitialized or mismatched.

---

### Main Screens & Feature Layers

#### [MODIFY] [SettingsTab.kt](file:///C:/Users/1/AndroidStudioProjects/NoZapret/app/src/main/java/com/example/nozapret/ui/screens/SettingsTab.kt)
- **Presets Section**: Remove the outer `Card` wrapper and its nested layout layers. Apply the animated border highlight directly to the `SettingsGroup`.
- **Transparency Audit**: Update every `ListItem` call inside the settings tabs to explicitly set `colors = ListItemDefaults.colors(containerColor = Color.Transparent)`. This removes the accidental inner dark blocks and ensures proper surface consistency.
- **Nested Padding Audit**: Optimize internal list item paddings to remove unwanted visual offsets and double-nested margins.

## Verification Plan

### Automated Tests
- Run Gradle check tasks to ensure source file compilation remains perfectly clean:
  `./gradlew assembleDebug`

### Manual Verification
- Deploy the updated app package on an Android device/emulator.
- Inspect the **Presets** section: Verify it matches the standard section layout without nested cards or contrasting dark rows, and that the highlight border animates correctly.
- Inspect the **Connection** section: Verify the list items sit seamlessly on a unified `surfaceContainerLow` block without square corner leaks.
- Tap interactive items to verify both the bounce scaling and Material 3 ripple animations work correctly.
- Toggle between Light and Dark themes to verify color harmony and contrast compliance.
