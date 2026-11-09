package com.dietpizza.rxplayer.core.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import java.io.File
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM-level migration test (plan §7.2 "each schema change gets a test"). Room's MigrationTestHelper
 * needs an Android Instrumentation (Robolectric), which this project doesn't use, so instead we:
 * - run the generated 1→2 AutoMigration against a recording connection, and
 * - check its SQL against the exported v1/v2 schemas.
 */
class MigrationTest {

    private val schemaDir = File("schemas/com.dietpizza.rxplayer.core.database.RxDatabase")
    private val v1 = File(schemaDir, "1.json").readText()
    private val v2 = File(schemaDir, "2.json").readText()

    private fun runMigration1To2(): List<String> {
        val executed = ArrayList<String>()
        val statement = Proxy.newProxyInstance(
            SQLiteStatement::class.java.classLoader,
            arrayOf(SQLiteStatement::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "step" -> false
                else -> if (method.returnType == Boolean::class.javaPrimitiveType) false else null
            }
        } as SQLiteStatement
        val connection = Proxy.newProxyInstance(
            SQLiteConnection::class.java.classLoader,
            arrayOf(SQLiteConnection::class.java),
        ) { _, method, args ->
            when (method.name) {
                "prepare" -> {
                    executed += args[0] as String
                    statement
                }
                "inTransaction" -> false
                else -> null
            }
        } as SQLiteConnection
        RxDatabase_AutoMigration_1_2_Impl().migrate(connection)
        return executed
    }

    /** The `createSql` strings of an exported schema, keyed by table name. */
    private fun createSqlByTable(schema: String): Map<String, String> {
        val regex = Regex("\"tableName\":\\s*\"(\\w+)\",\\s*\"createSql\":\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
        return regex.findAll(schema).associate { m ->
            val table = m.groupValues[1]
            table to m.groupValues[2].replace("\\\"", "\"").replace("\${TABLE_NAME}", table)
        }
    }

    @Test
    fun schemaVersionsExported() {
        assertTrue(Regex("\"version\":\\s*1,").containsMatchIn(v1))
        assertTrue(Regex("\"version\":\\s*2,").containsMatchIn(v2))
    }

    @Test
    fun playbackStateIsUnchangedBetweenVersions() {
        val t1 = createSqlByTable(v1)
        val t2 = createSqlByTable(v2)
        assertEquals(setOf("playback_state"), t1.keys)
        assertEquals(t1.getValue("playback_state"), t2.getValue("playback_state"))
    }

    @Test
    fun v2AddsTheLibraryTables() {
        assertEquals(
            setOf("playback_state", "favourite", "album", "album_item", "playlist", "playlist_item"),
            createSqlByTable(v2).keys,
        )
    }

    @Test
    fun autoMigrationCreatesExactlyTheNewTablesAsExported() {
        val sql = runMigration1To2()
        val creates = sql.filter { it.startsWith("CREATE TABLE") }
        val expected = createSqlByTable(v2) - "playback_state"
        assertEquals(expected.size, creates.size)
        for ((table, create) in expected) {
            assertTrue("missing $table", creates.contains(create))
        }
        // It must never touch existing data.
        assertFalse(sql.any { it.contains("playback_state") })
        assertFalse(sql.any { it.startsWith("DROP") || it.startsWith("ALTER") })
    }

    @Test
    fun childTablesCascadeAndIndexMediaKey() {
        val sql = runMigration1To2()
        val albumItem = sql.single { it.contains("TABLE IF NOT EXISTS `album_item`") }
        val playlistItem = sql.single { it.contains("TABLE IF NOT EXISTS `playlist_item`") }
        assertTrue(albumItem.contains("REFERENCES `album`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE"))
        assertTrue(albumItem.contains("PRIMARY KEY(`album_id`, `media_key`)"))
        assertTrue(playlistItem.contains("REFERENCES `playlist`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE"))
        assertTrue(playlistItem.contains("PRIMARY KEY(`playlist_id`, `position`)"))
        // media_key indexes keep the rename/move key rewrite (plan §7.2) off a full scan.
        assertTrue(sql.contains("CREATE INDEX IF NOT EXISTS `index_album_item_media_key` ON `album_item` (`media_key`)"))
        assertTrue(sql.contains("CREATE INDEX IF NOT EXISTS `index_playlist_item_media_key` ON `playlist_item` (`media_key`)"))
    }
}
