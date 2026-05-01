package net.harveywilliams.bluetoothbouncer.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room schema migration from version 1 to 2.
 *
 * Adds two columns to [blocked_devices]:
 *  - cdmAssociationId INTEGER (nullable, default null) — CDM association ID for Watch feature
 *  - isTemporarilyAllowed INTEGER NOT NULL DEFAULT 0 — temporary-allow flag (Boolean stored as 0/1)
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE blocked_devices ADD COLUMN cdmAssociationId INTEGER DEFAULT NULL"
        )
        db.execSQL(
            "ALTER TABLE blocked_devices ADD COLUMN isTemporarilyAllowed INTEGER NOT NULL DEFAULT 0"
        )
    }
}

/**
 * Room schema migration from version 2 to 3.
 *
 * Adds [isAlertEnabled] column to [blocked_devices] and backfills it from
 * [cdmAssociationId]: any row that already had a CDM association had Alert enabled,
 * so it should continue to receive notifications after the upgrade.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE blocked_devices ADD COLUMN isAlertEnabled INTEGER NOT NULL DEFAULT 0"
        )
        db.execSQL(
            "UPDATE blocked_devices SET isAlertEnabled = 1 WHERE cdmAssociationId IS NOT NULL"
        )
    }
}
