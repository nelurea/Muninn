package io.github.nelurea.muninn.data.db

import android.content.Context
import androidx.room.Room

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
                            MIGRATION_11_12
                        )
                        .fallbackToDestructiveMigration()
                        .build()
                        .also {
                            instance = it
                        }
            }
    }
}
