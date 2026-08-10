package io.github.nelurea.muninn.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_8_9 = object : Migration(8, 9) {

    override fun migrate(db: SupportSQLiteDatabase) {

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `captured_works` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `sourceType` TEXT NOT NULL,
                `sourceId` TEXT NOT NULL,
                `canonicalUrl` TEXT NOT NULL,
                `capturedAt` TEXT NOT NULL,
                `authorId` TEXT NOT NULL,
                `authorName` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `caption` TEXT NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS
            `index_captured_works_sourceType_sourceId`
            ON `captured_works` (`sourceType`, `sourceId`)
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `captured_media` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `workId` INTEGER NOT NULL,
                `mediaIndex` INTEGER NOT NULL,
                `localUri` TEXT NOT NULL,
                `sourceUrl` TEXT NOT NULL,
                `mimeType` TEXT NOT NULL,
                `fileName` TEXT NOT NULL,
                FOREIGN KEY(`workId`)
                    REFERENCES `captured_works`(`id`)
                    ON UPDATE NO ACTION
                    ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS
            `index_captured_media_workId`
            ON `captured_media` (`workId`)
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS
            `index_captured_media_workId_mediaIndex`
            ON `captured_media` (`workId`, `mediaIndex`)
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `captured_tags` (
                `workId` INTEGER NOT NULL,
                `position` INTEGER NOT NULL,
                `tag` TEXT NOT NULL,
                PRIMARY KEY(`workId`, `position`),
                FOREIGN KEY(`workId`)
                    REFERENCES `captured_works`(`id`)
                    ON UPDATE NO ACTION
                    ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS
            `index_captured_tags_workId`
            ON `captured_tags` (`workId`)
            """.trimIndent()
        )
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {

    override fun migrate(db: SupportSQLiteDatabase) {

        /*
         * 1. Preserve child-table data without foreign keys.
         */
        db.execSQL(
            """
            CREATE TABLE `captured_media_backup` AS
            SELECT
                `id`,
                `workId`,
                `mediaIndex`,
                `localUri`,
                `sourceUrl`,
                `mimeType`,
                `fileName`
            FROM `captured_media`
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE `captured_tags_backup` AS
            SELECT
                `workId`,
                `position`,
                `tag`
            FROM `captured_tags`
            """.trimIndent()
        )

        /*
         * 2. Create the revised parent table.
         *    title is nullable in schema version 10.
         */
        db.execSQL(
            """
            CREATE TABLE `captured_works_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `sourceType` TEXT NOT NULL,
                `sourceId` TEXT NOT NULL,
                `canonicalUrl` TEXT NOT NULL,
                `capturedAt` TEXT NOT NULL,
                `authorId` TEXT NOT NULL,
                `authorName` TEXT NOT NULL,
                `title` TEXT,
                `caption` TEXT NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            INSERT INTO `captured_works_new`
            (
                `id`,
                `sourceType`,
                `sourceId`,
                `canonicalUrl`,
                `capturedAt`,
                `authorId`,
                `authorName`,
                `title`,
                `caption`
            )
            SELECT
                `id`,
                `sourceType`,
                `sourceId`,
                `canonicalUrl`,
                `capturedAt`,
                `authorId`,
                `authorName`,
                `title`,
                `caption`
            FROM `captured_works`
            """.trimIndent()
        )

        /*
         * 3. Remove the old child tables before replacing
         *    their parent.
         */
        db.execSQL(
            "DROP TABLE `captured_media`"
        )

        db.execSQL(
            "DROP TABLE `captured_tags`"
        )

        db.execSQL(
            "DROP TABLE `captured_works`"
        )

        db.execSQL(
            """
            ALTER TABLE `captured_works_new`
            RENAME TO `captured_works`
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE INDEX
            `index_captured_works_sourceType_sourceId`
            ON `captured_works` (`sourceType`, `sourceId`)
            """.trimIndent()
        )

        /*
         * 4. Recreate child tables against the FINAL
         *    parent-table name.
         */
        db.execSQL(
            """
            CREATE TABLE `captured_media` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `workId` INTEGER NOT NULL,
                `mediaIndex` INTEGER NOT NULL,
                `localUri` TEXT NOT NULL,
                `sourceUrl` TEXT NOT NULL,
                `mimeType` TEXT NOT NULL,
                `fileName` TEXT NOT NULL,
                FOREIGN KEY(`workId`)
                    REFERENCES `captured_works`(`id`)
                    ON UPDATE NO ACTION
                    ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            INSERT INTO `captured_media`
            (
                `id`,
                `workId`,
                `mediaIndex`,
                `localUri`,
                `sourceUrl`,
                `mimeType`,
                `fileName`
            )
            SELECT
                `id`,
                `workId`,
                `mediaIndex`,
                `localUri`,
                `sourceUrl`,
                `mimeType`,
                `fileName`
            FROM `captured_media_backup`
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE INDEX
            `index_captured_media_workId`
            ON `captured_media` (`workId`)
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE UNIQUE INDEX
            `index_captured_media_workId_mediaIndex`
            ON `captured_media` (`workId`, `mediaIndex`)
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE `captured_tags` (
                `workId` INTEGER NOT NULL,
                `position` INTEGER NOT NULL,
                `tag` TEXT NOT NULL,
                PRIMARY KEY(`workId`, `position`),
                FOREIGN KEY(`workId`)
                    REFERENCES `captured_works`(`id`)
                    ON UPDATE NO ACTION
                    ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            INSERT INTO `captured_tags`
            (
                `workId`,
                `position`,
                `tag`
            )
            SELECT
                `workId`,
                `position`,
                `tag`
            FROM `captured_tags_backup`
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE INDEX
            `index_captured_tags_workId`
            ON `captured_tags` (`workId`)
            """.trimIndent()
        )

        /*
         * 5. Remove temporary backup tables.
         */
        db.execSQL(
            "DROP TABLE `captured_media_backup`"
        )

        db.execSQL(
            "DROP TABLE `captured_tags_backup`"
        )
    }
}
val MIGRATION_10_11 =
    object : Migration(10, 11) {

        override fun migrate(
            db: SupportSQLiteDatabase
        ) {
            db.execSQL(
                """
                ALTER TABLE captured_works
                ADD COLUMN sessionId INTEGER
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS
                index_captured_works_sessionId
                ON captured_works(sessionId)
                """.trimIndent()
            )
        }
    }
