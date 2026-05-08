package org.qosp.notes.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.qosp.notes.data.model.NoteQuickSwitchBinding

@Dao
interface NoteQuickSwitchBindingDao {
    @Query("SELECT * FROM note_quick_switch_bindings ORDER BY id ASC")
    fun getAll(): Flow<List<NoteQuickSwitchBinding>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(binding: NoteQuickSwitchBinding)

    @Query("SELECT * FROM note_quick_switch_bindings WHERE sourceNoteId = :sourceNoteId LIMIT 1")
    suspend fun getBySourceId(sourceNoteId: Long): NoteQuickSwitchBinding?

    @Query("SELECT COUNT(*) FROM note_quick_switch_bindings")
    suspend fun getCount(): Int

    @Query("DELETE FROM note_quick_switch_bindings WHERE sourceNoteId = :sourceNoteId")
    suspend fun deleteBySourceId(sourceNoteId: Long)
}
