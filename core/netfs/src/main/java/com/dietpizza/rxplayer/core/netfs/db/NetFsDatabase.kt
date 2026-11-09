package com.dietpizza.rxplayer.core.netfs.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Server configs (plan §7.2 `network_server`). Lives in its own small database, `netfs.db`
 * (decision 37), so `:core:netfs` doesn't depend on `RxDatabase`.
 */
@Entity(tableName = "network_server")
class NetworkServerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** SMB | WEBDAV | FTP | FTPS | SFTP */
    val protocol: String,
    val name: String,
    val host: String,
    val port: Int,
    @ColumnInfo(name = "base_path") val basePath: String,
    val username: String?,
    /** AES-GCM ciphertext under the Keystore key; never plaintext. */
    @ColumnInfo(name = "secret_ct", typeAffinity = ColumnInfo.BLOB) val secretCt: ByteArray?,
    @ColumnInfo(name = "secret_iv", typeAffinity = ColumnInfo.BLOB) val secretIv: ByteArray?,
    @ColumnInfo(name = "options_json") val optionsJson: String,
    @ColumnInfo(name = "last_used_at") val lastUsedAt: Long,
)

@Dao
interface NetworkServerDao {
    @Query("SELECT * FROM network_server ORDER BY name COLLATE NOCASE, host COLLATE NOCASE, id")
    fun observeAll(): Flow<List<NetworkServerEntity>>

    @Query("SELECT * FROM network_server WHERE id = :id")
    suspend fun get(id: Long): NetworkServerEntity?

    /** Blocking variant for Media3 loader threads. Never call on the main thread. */
    @Query("SELECT * FROM network_server WHERE id = :id")
    fun getBlocking(id: Long): NetworkServerEntity?

    @Insert
    suspend fun insert(entity: NetworkServerEntity): Long

    @Update
    suspend fun update(entity: NetworkServerEntity)

    @Query("DELETE FROM network_server WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE network_server SET last_used_at = :at WHERE id = :id")
    suspend fun touch(id: Long, at: Long)

    @Query("UPDATE network_server SET options_json = :optionsJson WHERE id = :id")
    suspend fun updateOptions(id: Long, optionsJson: String)
}

@Database(entities = [NetworkServerEntity::class], version = 1, exportSchema = true)
abstract class NetFsDatabase : RoomDatabase() {
    abstract fun serverDao(): NetworkServerDao

    companion object {
        const val NAME = "netfs.db"
    }
}
