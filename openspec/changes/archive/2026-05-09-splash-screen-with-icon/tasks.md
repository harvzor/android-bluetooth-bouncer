## 1. Add Dependency

- [x] 1.1 Add `coreSplashscreen = "1.2.0"` to the `[versions]` section of `gradle/libs.versions.toml`
- [x] 1.2 Add `androidx-core-splashscreen = { group = "androidx.core", name = "core-splashscreen", version.ref = "coreSplashscreen" }` to the `[libraries]` section of `gradle/libs.versions.toml`
- [x] 1.3 Add `implementation(libs.androidx.core.splashscreen)` to `app/build.gradle.kts`

## 2. Configure Themes

- [x] 2.1 Add `Theme.BluetoothBouncer.Splash` style to `app/src/main/res/values/themes.xml`
- [x] 2.2 Create `app/src/main/res/values-night/themes.xml` with a dark variant of `Theme.BluetoothBouncer` (parent `android:Theme.Material.NoActionBar`) and a dark variant of `Theme.BluetoothBouncer.Splash` using `#1C1B1F` as `windowSplashScreenBackground`

## 3. Wire Up Manifest and Activity

- [x] 3.1 Change the `android:theme` attribute on `MainActivity` in `AndroidManifest.xml` from `@style/Theme.BluetoothBouncer` to `@style/Theme.BluetoothBouncer.Splash`
- [x] 3.2 Add `installSplashScreen()` call in `MainActivity.kt` as the first statement in `onCreate`, before `super.onCreate(savedInstanceState)`, with the required import `androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen`

## 4. Fix Theme.kt Status Bar Override

- [x] 4.1 Remove the `SideEffect` block in `ui/theme/Theme.kt` (lines setting `window.statusBarColor` and `isAppearanceLightStatusBars`) — `enableEdgeToEdge()` already handles this correctly
- [x] 4.2 Remove the now-unused imports in `Theme.kt`: `android.app.Activity`, `androidx.compose.ui.graphics.toArgb`, `androidx.compose.ui.platform.LocalView`, `androidx.core.view.WindowCompat`

## 5. Build and Verify

- [x] 5.1 Run `.\gradlew.bat assembleDebug` and confirm `BUILD SUCCESSFUL`
- [x] 5.2 Install the APK on the physical test device (`adb install -r`)
- [ ] 5.3 Launch the app in light mode and verify: white splash background, bouncer icon centred, smooth transition into the app UI
- [ ] 5.4 Switch device to dark mode, launch the app, and verify: dark (`#1C1B1F`) splash background, bouncer icon centred, no white flash on transition
