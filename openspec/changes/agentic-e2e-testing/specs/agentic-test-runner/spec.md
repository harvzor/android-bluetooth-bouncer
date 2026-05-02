## ADDED Requirements

### Requirement: Agent can execute a full E2E test run
The agent SHALL be able to run a structured, phased test suite against the real app on a physical Android device via ADB, producing a pass/fail result for each phase.

#### Scenario: Full test run requested
- **WHEN** the user asks the agent to run the E2E tests
- **THEN** the agent detects the physical device, asks for full or quick mode, runs each phase in order, pauses at checkpoints requiring user action, and reports a summary of pass/fail results

#### Scenario: Quick mode skips reinstall phases
- **WHEN** the user selects quick mode
- **THEN** the agent skips Phases 0, 1, and 2 and begins at Phase 3

#### Scenario: Ambiguous device selection
- **WHEN** `adb devices` returns more than one physical device
- **THEN** the agent asks the user which device to target before proceeding

#### Scenario: Single physical device with emulator
- **WHEN** `adb devices` returns exactly one physical device and one or more emulators
- **THEN** the agent automatically selects the physical device without asking

### Requirement: Agent pauses before Bluetooth-dependent phases
The agent SHALL checkpoint with the user before executing any phase that requires a physical Bluetooth device to be powered on and in range.

#### Scenario: Checkpoint before block/unblock phase
- **WHEN** the agent is about to begin Phase 4
- **THEN** the agent asks the user to confirm the target Bluetooth device is turned on and connected before proceeding

### Requirement: Temporary ADB artifacts are not committed to git
The agent SHALL write all temporary files (screenshots, UI dumps) to `.adb-test-tmp/` and this directory SHALL be listed in `.gitignore`.

#### Scenario: Agent creates temp files during test run
- **WHEN** the agent takes a screenshot or dumps the UI hierarchy
- **THEN** the file is written to `.adb-test-tmp/` not the repo root

#### Scenario: Temp directory cleaned up after run
- **WHEN** Phase 7 (cleanup) executes
- **THEN** all files in `.adb-test-tmp/` are deleted
