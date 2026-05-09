## ADDED Requirements

### Requirement: Splash screen displays on app launch
The app SHALL display a splash screen when launched. The splash screen SHALL show the launcher foreground icon (`ic_launcher_foreground`) centred on a background colour that matches the system theme preference.

#### Scenario: Light mode launch
- **WHEN** the device system theme is set to light mode
- **THEN** the splash screen SHALL display with a white (`#FFFFFF`) background and the launcher foreground icon centred within it

#### Scenario: Dark mode launch
- **WHEN** the device system theme is set to dark mode
- **THEN** the splash screen SHALL display with a dark (`#1C1B1F`) background and the launcher foreground icon centred within it

### Requirement: Splash screen transitions smoothly into the app
The splash screen SHALL dismiss and transition into the main app UI without a visible white flash or abrupt jump, regardless of the system theme setting.

#### Scenario: Transition in light mode
- **WHEN** the splash screen dismisses in light mode
- **THEN** the main Compose UI SHALL appear without any intermediate white or mismatched-colour flash

#### Scenario: Transition in dark mode
- **WHEN** the splash screen dismisses in dark mode
- **THEN** the main Compose UI SHALL appear without any intermediate light flash or colour mismatch

### Requirement: Splash screen does not delay app startup
The splash screen SHALL be dismissed as soon as the Activity is ready to display content. The app SHALL NOT introduce artificial delays (timers, fake loading states) to extend splash screen visibility.

#### Scenario: Normal launch
- **WHEN** the app is launched and `MainActivity.onCreate` completes
- **THEN** the splash screen SHALL transition immediately to the app UI with no imposed delay
