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