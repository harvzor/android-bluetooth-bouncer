## Why

The app is single-user and has never been distributed. The database schema has accumulated two migrations from iterative development, but since the only installation will be wiped before the change is applied, there is no data to preserve and no migration paths to maintain.

## What Changes

- Delete `MIGRATION_1_2` and `MIGRATION_2_3` from `Migrations.kt`
- Delete the `Migrations.kt` file entirely
- Remove `.addMigrations(...)` call from `AppDatabase.getDatabase()`
- Add `fallbackToDestructiveMigration()` to the Room builder as a safety net for future development
- Reset the database version to `1` in `@Database`

## Capabilities

### New Capabilities
<!-- None — this is a pure cleanup change with no new user-facing capabilities -->

### Modified Capabilities
- `device-blocking`: Schema version resets to 1; no behavioral change to blocking logic, but the persistence layer is simplified

## Impact

- `app/src/main/java/.../data/AppDatabase.kt` — version reset, builder simplified
- `app/src/main/java/.../data/Migrations.kt` — deleted
- No impact on DAO, entities, ViewModel, or UI
- Requires the app to be uninstalled (or data wiped) on any existing installation before deploying
