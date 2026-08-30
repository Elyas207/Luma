package app.kreate.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import app.kreate.database.models.PersistentQueue
import app.kreate.database.table.DatabaseTable
import kotlinx.coroutines.flow.Flow

@Dao
@RewriteQueriesToDropUnusedColumns
interface QueuedMediaItemTable: DatabaseTable<PersistentQueue> {

    override val tableName: String
        get() = "persistent_queue"

    @Query("""
        SELECT * 
        FROM persistent_queue
        JOIN songs ON song_id = id
        LIMIT :limit
    """)
    fun blockingItems( limit: Int = Int.MAX_VALUE ): List<PersistentQueue.Item>

    /**
     * The item playback was in the middle of, if there is one.
     *
     * Exactly one row carries a non-null position — the one the playhead was inside — so this is
     * "where you were" rather than "what is in the queue". Home needs it because its hero reads
     * play *history*, and history only gains a row when a track ends: an app killed three minutes
     * into a two-hour recitation has no history entry at all, so the screen said "Nothing playing
     * yet" while the position sat in this table the whole time.
     */
    @Query("""
        SELECT *
        FROM persistent_queue
        JOIN songs ON song_id = id
        WHERE position IS NOT NULL
        LIMIT 1
    """)
    fun inProgress(): Flow<PersistentQueue.Item?>

    @Query("DELETE FROM persistent_queue")
    fun deleteAll(): Int
}