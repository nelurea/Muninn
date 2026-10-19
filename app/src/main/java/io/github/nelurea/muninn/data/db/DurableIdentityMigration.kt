package io.github.nelurea.muninn.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Non-destructive v22 -> v23 foundation for external Personal Context snapshots.
 * It deliberately changes no Work / Media / Session primary or foreign key.
 * The actual external writer, reader, and reconciliation belong to later slices.
 */
val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `durable_identities` (
                `entityType` TEXT NOT NULL,
                `rowId` INTEGER NOT NULL,
                `stableId` TEXT NOT NULL,
                PRIMARY KEY(`entityType`, `rowId`)
            )
            """.trimIndent()
        )
        // Backfill BEFORE creating the unique index or triggers. Every existing
        // row keeps its original Room ID and all existing associations intact.
        for ((type, table) in DURABLE_IDENTITY_TABLES) {
            db.execSQL(
                "INSERT INTO durable_identities (entityType, rowId, stableId) " +
                    "SELECT '$type', id, lower(hex(randomblob(16))) FROM `$table`"
            )
        }
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_durable_identities_stableId` " +
                "ON `durable_identities` (`stableId`)"
        )
        installDurableIdentityTriggers(db)
    }
}

private val DURABLE_IDENTITY_TABLES = listOf(
    DurableIdentityType.SESSION to "sessions",
    DurableIdentityType.WORK to "captured_works",
    DurableIdentityType.MEDIA to "captured_media",
    DurableIdentityType.LEGACY_IMAGE to "images",
    DurableIdentityType.FOCUS to "media_focus"
)

/** Called by the v22 migration and by Room's onCreate for fresh installations. */
fun installDurableIdentityTriggers(db: SupportSQLiteDatabase) {
    for ((type, table) in DURABLE_IDENTITY_TABLES) {
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS `durable_identity_${type}_insert`
            AFTER INSERT ON `$table`
            BEGIN
                INSERT INTO `durable_identities` (`entityType`, `rowId`, `stableId`)
                VALUES ('$type', NEW.`id`, lower(hex(randomblob(16))));
            END
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS `durable_identity_${type}_delete`
            AFTER DELETE ON `$table`
            BEGIN
                DELETE FROM `durable_identities`
                WHERE `entityType` = '$type' AND `rowId` = OLD.`id`;
            END
            """.trimIndent()
        )
    }
}
