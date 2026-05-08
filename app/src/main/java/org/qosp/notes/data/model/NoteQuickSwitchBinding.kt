package org.qosp.notes.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "note_quick_switch_bindings",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceNoteId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["targetNoteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["sourceNoteId"], unique = true),
        Index(value = ["targetNoteId"]),
    ]
)
data class NoteQuickSwitchBinding(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val sourceNoteId: Long,
    val targetNoteId: Long,
    val colorPresetIndex: Int,
)
