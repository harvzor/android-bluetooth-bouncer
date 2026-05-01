## Context

The repository currently has no README.md. AGENTS.md contains developer-facing context but nothing for end users or casual visitors. The app is feature-complete with blocking, alerts, temporary allow, boot persistence, re-pair protection, and live connection status — all of which need to be documented.

The README was discussed and planned in an exploration session. Key decisions were made about audience (end users), tone (friendly, approachable), tagline ("Sorry mate, not tonight."), and structure (problem → features → requirements → setup → how it works → permissions).

## Goals / Non-Goals

**Goals:**
- Create a README that lets a new visitor understand what the app does within 60 seconds
- Clearly communicate the Shizuku requirement upfront so users aren't surprised
- Document all user-facing features with benefit-focused descriptions
- Provide a step-by-step getting started guide
- Add an AGENTS.md rule to keep the README in sync with feature changes

**Non-Goals:**
- Screenshots or visual assets (not available yet)
- Build-from-source instructions (end-user audience)
- Developer contribution guidelines (open-source status undecided)
- Marketing copy or Play Store listing

## Decisions

### 1. README structure and section order

**Decision**: Problem → Features → Requirements → Getting Started → How It Works → Permissions

**Rationale**: Lead with the "why" (the problem) before the "what" (features). Requirements come before setup so users know what they need before starting. Technical details go last since the primary audience is non-technical.

**Alternatives considered**:
- Features-first: Would work but lacks the narrative hook of explaining the problem
- Requirements-first: Too much friction before the user understands the value

### 2. Tone: friendly and approachable with a cheeky tagline

**Decision**: Use "Sorry mate, not tonight." as the tagline. Body copy should be conversational — like explaining the app to a friend — without trying too hard after the tagline sets the personality.

**Rationale**: The bouncer metaphor is baked into the app name. The tagline reinforces it memorably. Overly technical or corporate tone would clash with the app's character.

### 3. Shizuku framing: positive, not burdensome

**Decision**: Frame Shizuku as "a free app that gives Bluetooth Bouncer the access it needs" with a brief inline explanation plus a link to Shizuku's GitHub. Mention it in Requirements AND in Getting Started.

**Rationale**: Shizuku is the biggest friction point for new users. Framing it positively and explaining *why* it's needed (Android restricts this API to system apps) helps users understand rather than resent the extra step.

### 4. Feature descriptions: benefit-focused, not technical

**Decision**: Describe features in terms of what the user gets, not how the system works. E.g., "Survives Reboots — Your blocks stick around, even after restarting your phone" rather than "Re-applies CONNECTION_POLICY_FORBIDDEN via BootReceiver."

**Rationale**: End-user audience. Technical implementation details belong in the "How It Works" section, kept brief.

### 5. AGENTS.md documentation rule

**Decision**: Add a `## Documentation` section to AGENTS.md stating that README.md must be updated when user-facing features are added, removed, or changed.

**Rationale**: Without an explicit rule, the README will go stale. AGENTS.md is the project's source of truth for development conventions and is read by both humans and AI agents.

## Risks / Trade-offs

- **[Staleness]** → Mitigated by the AGENTS.md rule, but ultimately depends on discipline. No automated enforcement.
- **[No screenshots]** → The README will be text-only. Acceptable for now; screenshots can be added later without structural changes.
- **[Shizuku links may change]** → Link to Shizuku's GitHub repo (stable) rather than specific release pages.
- **[Feature list may drift]** → The AGENTS.md rule should catch this, but feature names/descriptions may need periodic review.
