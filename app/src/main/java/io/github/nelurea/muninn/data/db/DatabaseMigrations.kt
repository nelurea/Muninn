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

val MIGRATION_11_12 =
    object : Migration(11, 12) {

        override fun migrate(
            db: SupportSQLiteDatabase
        ) {
            db.execSQL(
                """
                ALTER TABLE captured_works
                ADD COLUMN publishedAt TEXT
                """.trimIndent()
            )

            db.execSQL(
                """
                ALTER TABLE captured_works
                ADD COLUMN discoveryMode TEXT
                """.trimIndent()
            )

            db.execSQL(
                """
                ALTER TABLE captured_works
                ADD COLUMN discoveryQuery TEXT
                """.trimIndent()
            )
        }
    }

val MIGRATION_12_13 =
    object : Migration(12, 13) {

        override fun migrate(
            db: SupportSQLiteDatabase
        ) {
            db.execSQL(
                """
                ALTER TABLE captured_media
                ADD COLUMN isHighlighted INTEGER NOT NULL DEFAULT 0
                """.trimIndent()
            )
        }
    }

val MIGRATION_13_14 =
    object : Migration(13, 14) {

        override fun migrate(
            db: SupportSQLiteDatabase
        ) {
            db.execSQL(
                """
                CREATE TABLE `state_vocabulary` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `label` TEXT NOT NULL
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE UNIQUE INDEX
                `index_state_vocabulary_label`
                ON `state_vocabulary` (`label`)
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE `session_states` (
                    `sessionId` INTEGER NOT NULL,
                    `stateVocabularyId` INTEGER NOT NULL,
                    PRIMARY KEY(
                        `sessionId`,
                        `stateVocabularyId`
                    ),
                    FOREIGN KEY(`sessionId`)
                        REFERENCES `sessions`(`id`)
                        ON UPDATE NO ACTION
                        ON DELETE CASCADE,
                    FOREIGN KEY(`stateVocabularyId`)
                        REFERENCES `state_vocabulary`(`id`)
                        ON UPDATE NO ACTION
                        ON DELETE CASCADE
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE INDEX
                `index_session_states_sessionId`
                ON `session_states` (`sessionId`)
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE INDEX
                `index_session_states_stateVocabularyId`
                ON `session_states` (`stateVocabularyId`)
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE `purpose_vocabulary` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `label` TEXT NOT NULL
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE UNIQUE INDEX
                `index_purpose_vocabulary_label`
                ON `purpose_vocabulary` (`label`)
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE `captured_work_purposes` (
                    `workId` INTEGER NOT NULL,
                    `purposeVocabularyId` INTEGER NOT NULL,
                    PRIMARY KEY(
                        `workId`,
                        `purposeVocabularyId`
                    ),
                    FOREIGN KEY(`workId`)
                        REFERENCES `captured_works`(`id`)
                        ON UPDATE NO ACTION
                        ON DELETE CASCADE,
                    FOREIGN KEY(`purposeVocabularyId`)
                        REFERENCES `purpose_vocabulary`(`id`)
                        ON UPDATE NO ACTION
                        ON DELETE CASCADE
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE INDEX
                `index_captured_work_purposes_workId`
                ON `captured_work_purposes` (`workId`)
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE INDEX
                `index_captured_work_purposes_purposeVocabularyId`
                ON `captured_work_purposes` (`purposeVocabularyId`)
                """.trimIndent()
            )
        }
    }

val MIGRATION_14_15 =
    object : Migration(14, 15) {

        override fun migrate(
            db: SupportSQLiteDatabase
        ) {
            db.execSQL(
                """
                CREATE TABLE `attraction_vocabulary` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `dimension` TEXT NOT NULL,
                    `label` TEXT NOT NULL
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE UNIQUE INDEX
                `index_attraction_vocabulary_dimension_label`
                ON `attraction_vocabulary` (
                    `dimension`,
                    `label`
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE `captured_work_attractions` (
                    `workId` INTEGER NOT NULL,
                    `attractionVocabularyId` INTEGER NOT NULL,
                    PRIMARY KEY(
                        `workId`,
                        `attractionVocabularyId`
                    ),
                    FOREIGN KEY(`workId`)
                        REFERENCES `captured_works`(`id`)
                        ON UPDATE NO ACTION
                        ON DELETE CASCADE,
                    FOREIGN KEY(`attractionVocabularyId`)
                        REFERENCES `attraction_vocabulary`(`id`)
                        ON UPDATE NO ACTION
                        ON DELETE CASCADE
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE INDEX
                `index_captured_work_attractions_workId`
                ON `captured_work_attractions` (`workId`)
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE INDEX
                `index_captured_work_attractions_attractionVocabularyId`
                ON `captured_work_attractions` (`attractionVocabularyId`)
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE `captured_media_attractions` (
                    `mediaId` INTEGER NOT NULL,
                    `attractionVocabularyId` INTEGER NOT NULL,
                    PRIMARY KEY(
                        `mediaId`,
                        `attractionVocabularyId`
                    ),
                    FOREIGN KEY(`mediaId`)
                        REFERENCES `captured_media`(`id`)
                        ON UPDATE NO ACTION
                        ON DELETE CASCADE,
                    FOREIGN KEY(`attractionVocabularyId`)
                        REFERENCES `attraction_vocabulary`(`id`)
                        ON UPDATE NO ACTION
                        ON DELETE CASCADE
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE INDEX
                `index_captured_media_attractions_mediaId`
                ON `captured_media_attractions` (`mediaId`)
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE INDEX
                `index_captured_media_attractions_attractionVocabularyId`
                ON `captured_media_attractions` (`attractionVocabularyId`)
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE `aesthetic_response_vocabulary` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `label` TEXT NOT NULL
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE UNIQUE INDEX
                `index_aesthetic_response_vocabulary_label`
                ON `aesthetic_response_vocabulary` (`label`)
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE `captured_work_responses` (
                    `workId` INTEGER NOT NULL,
                    `responseVocabularyId` INTEGER NOT NULL,
                    PRIMARY KEY(
                        `workId`,
                        `responseVocabularyId`
                    ),
                    FOREIGN KEY(`workId`)
                        REFERENCES `captured_works`(`id`)
                        ON UPDATE NO ACTION
                        ON DELETE CASCADE,
                    FOREIGN KEY(`responseVocabularyId`)
                        REFERENCES `aesthetic_response_vocabulary`(`id`)
                        ON UPDATE NO ACTION
                        ON DELETE CASCADE
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE INDEX
                `index_captured_work_responses_workId`
                ON `captured_work_responses` (`workId`)
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE INDEX
                `index_captured_work_responses_responseVocabularyId`
                ON `captured_work_responses` (`responseVocabularyId`)
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE `media_focus` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `mediaId` INTEGER NOT NULL,
                    `attractionVocabularyId` INTEGER,
                    `note` TEXT,
                    `regionLeft` REAL,
                    `regionTop` REAL,
                    `regionRight` REAL,
                    `regionBottom` REAL,
                    FOREIGN KEY(`mediaId`)
                        REFERENCES `captured_media`(`id`)
                        ON UPDATE NO ACTION
                        ON DELETE CASCADE,
                    FOREIGN KEY(`attractionVocabularyId`)
                        REFERENCES `attraction_vocabulary`(`id`)
                        ON UPDATE NO ACTION
                        ON DELETE SET NULL
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE INDEX
                `index_media_focus_mediaId`
                ON `media_focus` (`mediaId`)
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE INDEX
                `index_media_focus_attractionVocabularyId`
                ON `media_focus` (`attractionVocabularyId`)
                """.trimIndent()
            )
        }
    }
