## Why

The project has no README. Anyone landing on the repository — whether from GitHub, a link, or a search result — has no way to understand what Bluetooth Bouncer does, why it exists, what it requires (Android 12+, Shizuku), or how to get started. A good README is the front door to the project.

Additionally, AGENTS.md has no rule about keeping the README in sync with the app's features, so it will inevitably go stale as the app evolves.

## What Changes

- **Create `README.md`** at the repository root with:
  - Tagline and hero section ("Sorry mate, not tonight.")
  - Problem statement explaining Android's indiscriminate Bluetooth auto-connection behavior
  - Feature overview: Block/Allow, Alerts (Android 13+), Temporary Allow, boot persistence, re-pair protection, live connection status
  - Requirements section: Android 12+ and Shizuku (brief explanation + link)
  - Getting Started walkthrough: install app, install Shizuku, start Shizuku, grant permission, block devices
  - How It Works: light-touch technical explanation (hidden API, Shizuku privilege bridge, OS-level persistence)
  - Permissions table: what the app asks for and why
- **Update `AGENTS.md`** with a Documentation section requiring the README to be kept in sync when user-facing features change

## Capabilities

### New Capabilities

_None — this change is documentation only, no new app capabilities._

### Modified Capabilities

_None — no spec-level behavior changes._

## Impact

- **Files created**: `README.md`
- **Files modified**: `AGENTS.md`
- **No code changes** — documentation only
- **No dependency changes**
