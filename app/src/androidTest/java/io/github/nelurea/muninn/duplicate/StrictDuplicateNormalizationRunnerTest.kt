package io.github.nelurea.muninn.duplicate

import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.Log
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.nelurea.muninn.data.db.AppDatabase
import io.github.nelurea.muninn.data.db.MIGRATION_10_11
import io.github.nelurea.muninn.data.db.MIGRATION_11_12
import io.github.nelurea.muninn.data.db.MIGRATION_12_13
import io.github.nelurea.muninn.data.db.MIGRATION_13_14
import io.github.nelurea.muninn.data.db.MIGRATION_14_15
import io.github.nelurea.muninn.data.db.MIGRATION_15_16
import io.github.nelurea.muninn.data.db.MIGRATION_16_17
import io.github.nelurea.muninn.data.db.MIGRATION_17_18
import io.github.nelurea.muninn.data.db.MIGRATION_8_9
import io.github.nelurea.muninn.data.db.MIGRATION_9_10
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Temporary, argument-driven real-device runner. Default mode is DRY_RUN.
 *
 * identities: JSON array of {"sourceType":"...","sourceId":"..."}; the complete unique repair scope.
 * mode: DRY_RUN or APPLY. APPLY additionally requires fingerprint=<64 lowercase hex chars>.
 */
@RunWith(AndroidJUnit4::class)
class StrictDuplicateNormalizationRunnerTest {
    @Test
    fun runScopedNormalization() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Temporary runner requires an explicit identities argument", args.containsKey("identities"))
        val mode = args.getString("mode", "DRY_RUN")
        require(mode == "DRY_RUN" || mode == "APPLY") { "mode must be DRY_RUN or APPLY" }
        val identities = parseIdentities(requireNotNull(args.getString("identities")) { "identities argument is required" })

        val path = context.getDatabasePath("muninn.db")
        require(path.isFile) { "muninn.db does not exist" }
        SQLiteDatabase.openDatabase(path.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { sqlite ->
            sqlite.rawQuery("PRAGMA user_version", null).use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("Refusing to open a database other than schema v18", 18, cursor.getInt(0))
            }
        }
        val openedName = if (mode == "DRY_RUN") copyDatabaseTriplet(context.getDatabasePath("muninn.db")) else "muninn.db"
        val database = Room.databaseBuilder(context, AppDatabase::class.java, openedName)
            .addMigrations(
                MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
                MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18
            ).build()
        try {
            val dao = database.duplicateNormalizationDao()
            val scoped = ScopedDuplicateNormalization(
                dao,
                DuplicateMediaReader { localUri -> context.contentResolver.openInputStream(Uri.parse(localUri)) },
                DuplicateCleanupService(
                    RoomDuplicateCleanupPersistence(database),
                    AndroidDuplicateCleanupDeleter(context)
                )
            )
            if (mode == "DRY_RUN") {
                val manifest = scoped.dryRun(identities)
                val identityResults = JSONObject(manifest.json).getJSONArray("identities")
                var verified = 0
                var rejected = 0
                (0 until identityResults.length()).forEach { index ->
                    val identity = identityResults.getJSONObject(index)
                    val status = identity.getString("status")
                    when (status) {
                        "VERIFIED" -> verified++
                        "REJECTED" -> rejected++
                    }
                    val details = if (identity.isNull("details")) "-" else identity.getString("details")
                    emit("DRY_RUN sourceType=${identity.getString("sourceType")} " +
                        "sourceId=${identity.getString("sourceId")} status=$status details=$details")
                }
                emit("DRY_RUN Verified=$verified Rejected=$rejected fingerprint=${manifest.fingerprint}")
            } else {
                val result = scoped.apply(identities, requireNotNull(args.getString("fingerprint")) {
                    "APPLY requires fingerprint"
                })
                emit("APPLY fingerprint=${result.manifest.fingerprint} normalized=${result.normalized} " +
                    "completed=${result.completed} cleanupPending=${result.cleanupPending} cleanupFailed=${result.cleanupFailed}")
                val identityResults = JSONObject(result.manifest.json).getJSONArray("identities")
                val verified = (0 until identityResults.length()).count {
                    identityResults.getJSONObject(it).getString("status") == "VERIFIED"
                }
                check(result.completed == verified) { "NORMALIZATION_INCOMPLETE" }
            }
        } finally {
            database.close()
            if (mode == "DRY_RUN") check(context.deleteDatabase(openedName)) {
                "Failed to delete temporary DRY_RUN database"
            }
        }
    }

    private fun parseIdentities(json: String): List<DuplicateIdentityKey> = JSONArray(json).let { array ->
        (0 until array.length()).map { index ->
            array.getJSONObject(index).let { DuplicateIdentityKey(it.getString("sourceType"), it.getString("sourceId")) }
        }
    }

    private fun emit(message: String) {
        Log.i("StrictDuplicateRunner", message)
        println("StrictDuplicateRunner: $message")
    }

    private fun copyDatabaseTriplet(source: File): String {
        val temporaryName = "muninn-dry-run-${UUID.randomUUID()}.db"
        val target = File(source.parentFile, temporaryName)
        listOf("", "-wal", "-shm").forEach { suffix ->
            val sourcePart = File(source.absolutePath + suffix)
            if (sourcePart.exists()) sourcePart.copyTo(File(target.absolutePath + suffix), overwrite = false)
        }
        check(target.isFile) { "Temporary DRY_RUN database copy was not created" }
        return temporaryName
    }
}
