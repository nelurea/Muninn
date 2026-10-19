package io.github.nelurea.muninn.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

object AppDatabaseProvider {

    @Volatile
    private var instance: AppDatabase? = null

    fun get(
        context: Context
    ): AppDatabase {
        return instance
            ?: synchronized(this) {
                instance
                    ?: Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "muninn.db"
                    )
                        .addMigrations(
                            MIGRATION_8_9,
                            MIGRATION_9_10,
                            MIGRATION_10_11,
                            MIGRATION_11_12,
                            MIGRATION_12_13,
                            MIGRATION_13_14,
                            MIGRATION_14_15,
                            MIGRATION_15_16,
                            MIGRATION_16_17,
                            MIGRATION_17_18,
                            MIGRATION_18_19,
                            MIGRATION_19_20,
                            MIGRATION_20_21,
                            MIGRATION_21_22,
                            MIGRATION_22_23
                        )
                        .addCallback(
                            object : RoomDatabase.Callback() {
                                override fun onCreate(db: SupportSQLiteDatabase) {
                                    // The migration installs these for old databases; fresh
                                    // databases need them before their first entity insert.
                                    installDurableIdentityTriggers(db)
                                }
                            }
                        )
                        .build()
                        .also {
                            instance = it
                        }
            }
    }
}
