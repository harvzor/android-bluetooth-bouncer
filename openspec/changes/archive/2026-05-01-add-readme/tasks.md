## 1. Create README.md

- [x] 1.1 Create `README.md` at repository root with hero section: app name and "Sorry mate, not tonight." tagline
- [x] 1.2 Write "The Problem" section: 2-3 sentences about Android's indiscriminate Bluetooth auto-connection behavior, using concrete examples (work headset on weekends, car stereo hijacking audio)
- [x] 1.3 Write "Features" section with benefit-focused descriptions for: Block/Allow toggle, Alerts (note Android 13+ requirement), Temporary Allow, Survives Reboots, Re-pair Protection, Live Status
- [x] 1.4 Write "Requirements" section: Android 12+ (API 31), Shizuku with brief inline explanation of what it is and why it's needed, plus link to Shizuku GitHub. Note Alert feature requires Android 13+
- [x] 1.5 Write "Getting Started" section: numbered walkthrough (install app → install Shizuku → start Shizuku → grant permission → block devices)
- [x] 1.6 Write "How It Works" section: light-touch explanation of hidden `setConnectionPolicy` API, Shizuku as privilege bridge, OS-level persistence (no background service needed)
- [x] 1.7 Write "Permissions" section: table of each permission (Bluetooth Connect, Notifications, Boot Completed, Companion Device Presence) with plain-language explanation of why each is needed

## 2. Update AGENTS.md

- [x] 2.1 Add `## Documentation` section to `AGENTS.md` (after "App Details") stating that `README.md` must be kept in sync when user-facing features are added, removed, or changed
