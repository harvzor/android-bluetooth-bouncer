## Context

The app's Room database (`AppDatabase.kt`) is currently at schema version 3, with two migration objects defined in `Migrations.kt`. These migrations were written incrementally during development to add columns (`cdmAssociationId`, `isTemporarilyAllowed`, `isAlertEnabled`) to the `blocked_devices` table.

The app has never been distributed. There is exactly one installation (the developer's device), and that device will have its app data wiped before this change is deployed. There are no users to protect and no migration paths to maintain.

## Goals / Non-Goals

**Goals:**
- Remove all migration code and simplify `AppDatabase` to a clean v1 schema
- Add `fallbackToDestructiveMigration()` as a permanent safety net for future development-time schema changes
- Reduce maintenance surface (no migration objects to keep in sync)

**Non-Goals:**
- Changing the schema itself — the entity fields remain identical
- Introducing any new database tooling or abstraction

## Decisions

**Reset version to 1 and delete migrations**

Since there are no existing installations to protect, resetting to version 1 is strictly cleaner than bumping to version 4. Version numbers carry an implicit promise about upgrade history; starting from 1 correctly signals that this is a clean baseline.

Alternatives considered:
- *Bump to version 4 with a no-op migration*: Unnecessarily preserves dead code structure.
- *Leave version at 3, just add destructive fallback*: Misleading — version 3 implies three prior migrations exist somewhere.

**Add `fallbackToDestructiveMigration()`**

During active development, schema changes are frequent. Without this, any schema change that lacks a migration crashes the app on launch. With it, Room wipes and recreates the database automatically — acceptable behavior during development for a single-developer app.

This should be removed before any public distribution.

## Risks / Trade-offs

- **Data loss on existing device** → Expected and intentional; developer must manually uninstall or wipe app data before installing the updated build. No mitigation needed beyond awareness.
- **`fallbackToDestructiveMigration()` left in permanently** → Future risk if the app is ever distributed. Mitigate by adding a code comment marking it as dev-only.

## Migration Plan

1. Developer uninstalls the app (or clears app data) on their device
2. Apply code changes
3. Build and install the updated APK
4. App starts fresh with a clean v1 database

Rollback: not applicable — there is no data to lose and the old code remains in git history.

## Open Questions

None.
