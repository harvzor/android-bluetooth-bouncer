## 1. Pre-flight

- [x] 1.1 Uninstall the app (or clear app data) from the test device

## 2. Code Changes

- [x] 2.1 In `AppDatabase.kt`, change `version = 3` to `version = 1` in `@Database`
- [x] 2.2 In `AppDatabase.kt`, remove `.addMigrations(MIGRATION_1_2, MIGRATION_2_3)` from the builder
- [x] 2.3 In `AppDatabase.kt`, add `.fallbackToDestructiveMigration()` to the builder with a comment marking it as dev-only
- [x] 2.4 In `AppDatabase.kt`, remove the import of `MIGRATION_1_2` and `MIGRATION_2_3`
- [x] 2.5 Delete `Migrations.kt`

## 3. Verify

- [x] 3.1 Build the app (`gradlew.bat assembleDebug`) — confirm no compile errors
- [x] 3.2 Install and launch on device — confirm app starts cleanly with a fresh empty database
