package app.kreate.database

import androidx.room.Dao
import androidx.room.Query
import app.kreate.database.models.ListeningSignal
import app.kreate.database.models.Song
import app.kreate.database.table.DatabaseTable
import kotlinx.coroutines.flow.Flow

@Dao
interface ListeningSignalTable : DatabaseTable<ListeningSignal> {

    override val tableName: String
        get() = "listening_signals"

    @Query("SELECT * FROM listening_signals WHERE song_id = :songId")
    suspend fun find( songId: String ): ListeningSignal?

    @Query("SELECT * FROM listening_signals")
    fun all(): Flow<List<ListeningSignal>>

    /**
     * Songs the app has quietly stopped offering, newest decision first.
     *
     * The thresholds are duplicated from [ListeningSignal.isSuppressed] because SQLite cannot call
     * into Kotlin. Kept adjacent in review terms: if one changes, the other must.
     */
    @Query("""
        SELECT s.* FROM songs s
        JOIN listening_signals sig ON sig.song_id = s.id
        WHERE sig.user_override = 0
          AND ( sig.fast_skips + sig.removals ) >= 3
        ORDER BY sig.updated_at DESC
    """)
    fun suppressedSongs(): Flow<List<Song>>

    /** Songs the app is confident the user enjoys. */
    @Query("""
        SELECT s.* FROM songs s
        JOIN listening_signals sig ON sig.song_id = s.id
        WHERE sig.replays >= 2 OR sig.completions >= 4
        ORDER BY ( sig.replays * 3 + sig.completions ) DESC
    """)
    fun lovedSongs(): Flow<List<Song>>

    /** Ids the recommender should not surface unprompted. */
    @Query("""
        SELECT song_id FROM listening_signals
        WHERE user_override = 0 AND ( fast_skips + removals ) >= 3
    """)
    suspend fun suppressedIds(): List<String>

    @Query("DELETE FROM listening_signals WHERE song_id = :songId")
    suspend fun forget( songId: String ): Int

    /** Reset every learned preference. The user's escape hatch. */
    @Query("DELETE FROM listening_signals")
    suspend fun forgetAll(): Int

    @Query("SELECT COUNT(*) FROM listening_signals")
    fun count(): Flow<Long>
}
