package app.kreate.database

import androidx.room.Dao
import androidx.room.Query
import app.kreate.database.models.ListeningEvent
import app.kreate.database.table.DatabaseTable
import kotlinx.coroutines.flow.Flow

/**
 * Reads over the append-only event log.
 *
 * Note what is **absent**: there is no update and no per-row edit. The only deletions are the
 * retention window and the user's own "forget" controls, and both delete whole rows rather than
 * rewriting them. If a query here ever needs to change a stored event, the design has gone wrong —
 * derive a new value instead.
 */
@Dao
interface ListeningEventTable : DatabaseTable<ListeningEvent> {

    override val tableName: String
        get() = "listening_events"

    /** Oldest first: replay has to see the log in the order it happened. */
    @Query("SELECT * FROM listening_events ORDER BY ts ASC, id ASC")
    suspend fun allChronological(): List<ListeningEvent>

    @Query("SELECT * FROM listening_events WHERE ts >= :since ORDER BY ts ASC, id ASC")
    suspend fun since( since: Long ): List<ListeningEvent>

    @Query("SELECT * FROM listening_events WHERE item_id = :itemId ORDER BY ts ASC")
    suspend fun forItem( itemId: String ): List<ListeningEvent>

    @Query("SELECT * FROM listening_events WHERE session_id = :sessionId ORDER BY ts ASC")
    suspend fun forSession( sessionId: String ): List<ListeningEvent>

    @Query("SELECT COUNT(*) FROM listening_events")
    suspend fun countNow(): Long

    @Query("SELECT COUNT(*) FROM listening_events")
    fun count(): Flow<Long>

    /**
     * The retention window: raw events live 90 days, then hard delete.
     *
     * Derived affinity is *not* recomputed from scratch afterwards — it carries its own decay — so
     * pruning does not silently erase a preference. That separation is what makes a short raw
     * retention honest rather than a euphemism.
     */
    @Query("DELETE FROM listening_events WHERE ts < :before")
    suspend fun pruneBefore( before: Long ): Int

    /** "Forget the last 24 hours / 7 days / 30 days." */
    @Query("DELETE FROM listening_events WHERE ts >= :since")
    suspend fun forgetSince( since: Long ): Int

    @Query("DELETE FROM listening_events WHERE item_id = :itemId")
    suspend fun forgetItem( itemId: String ): Int

    @Query("DELETE FROM listening_events")
    suspend fun forgetAll(): Int
}
