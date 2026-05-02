## 1. Repo Hygiene

- [ ] 1.1 Add `.adb-test-tmp/` to `.gitignore`
- [ ] 1.2 Delete stray ADB artifact files from repo root (`ui.xml`, `ui2.xml`, `screen.png`, `screen2.png`, `screen3.png`)

## 2. Android ADB Testing Skill

- [ ] 2.1 Create skill directory at `~/.agents/skills/android-adb-testing/`
- [ ] 2.2 Write `SKILL.md` covering: device detection (`adb devices`, auto-select physical device), UI dump (`uiautomator dump`), parsing bounds from XML to compute tap coordinates, tapping (`input tap X Y`), screenshotting (`screencap`), pulling files to `.adb-test-tmp/`, polling for state changes, app lifecycle (`am start`, `am force-stop`, `pm install`, `pm uninstall`), granting permissions via ADB (`pm grant`), and cleanup

## 3. Project Test Plan

- [ ] 3.1 Create `TESTING.md` in repo root with: prerequisites, full vs. quick mode description, device auto-detection logic, the complete phased test plan (Phases 0–7) with step-by-step assertions and pass/fail criteria for each step, user checkpoint instructions, known limitations (CDM dialog risk, reconnection timing), and cleanup steps
