package org.qosp.notes.components.security

import androidx.annotation.ColorInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.qosp.notes.data.model.NoteQuickSwitchBinding
import org.qosp.notes.data.repo.NoteQuickSwitchRepository
import kotlin.random.Random

class NoteQuickSwitchManager(
    private val repository: NoteQuickSwitchRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val bindingsState = MutableStateFlow<List<NoteQuickSwitchBinding>>(emptyList())
    private val pendingBindingState = MutableStateFlow<PendingBinding?>(null)
    private val currentEditorNoteId = MutableStateFlow<Long?>(null)

    val bindings: StateFlow<List<NoteQuickSwitchBinding>> = bindingsState

    val pendingBinding: StateFlow<PendingBinding?> = pendingBindingState

    val currentTargetNoteId: StateFlow<Long?> = combine(currentEditorNoteId, bindingsState) { noteId, bindings ->
        bindings.firstOrNull { it.sourceNoteId == noteId }?.targetNoteId
    }.stateIn(scope, SharingStarted.Eagerly, null)

    init {
        scope.launch {
            repository.observeAll().collect { bindings ->
                bindingsState.value = bindings
            }
        }
    }

    fun setCurrentEditorNoteId(noteId: Long?) {
        currentEditorNoteId.value = noteId
    }

    fun clearPendingBinding() {
        pendingBindingState.value = null
    }

    fun getShareState(noteId: Long): ShareState {
        val bindings = bindingsState.value
        val incomingBindings = bindings.filter { it.targetNoteId == noteId }
        val sourceBinding = bindings.firstOrNull { it.sourceNoteId == noteId }
        val pendingBinding = pendingBindingState.value

        val highlightColor = when {
            pendingBinding?.sourceNoteId == noteId -> presetColorFor(pendingBinding.colorPresetIndex)
            sourceBinding != null -> presetColorFor(sourceBinding.colorPresetIndex)
            else -> null
        }

        return ShareState(
            highlightColor = highlightColor,
            incomingBindingCount = incomingBindings.size,
            incomingBindingColors = incomingBindings.map { presetColorFor(it.colorPresetIndex) },
        )
    }

    fun handleShareLongPress(noteId: Long, isPrivatePage: Boolean): ShareInteractionResult {
        val currentBindings = bindingsState.value
        val pendingBinding = pendingBindingState.value
        val existingBinding = currentBindings.firstOrNull { it.sourceNoteId == noteId }
        val hasIncomingBindings = currentBindings.any { it.targetNoteId == noteId }

        if (pendingBinding != null) {
            if (pendingBinding.sourceNoteId == noteId) {
                pendingBindingState.value = null
                return ShareInteractionResult.Updated(current = getShareState(noteId))
            }

            if (existingBinding != null) {
                return ShareInteractionResult.Ignored
            }

            if (isPrivatePage) {
                return ShareInteractionResult.Ignored
            }

            val updatedBinding = NoteQuickSwitchBinding(
                sourceNoteId = pendingBinding.sourceNoteId,
                targetNoteId = noteId,
                colorPresetIndex = pendingBinding.colorPresetIndex,
            )

            bindingsState.value = currentBindings
                .filterNot { it.sourceNoteId == pendingBinding.sourceNoteId } + updatedBinding
            pendingBindingState.value = null

            scope.launch {
                repository.upsert(updatedBinding)
            }

            return ShareInteractionResult.Updated(
                current = getShareState(noteId),
                transientHighlightColor = presetColorFor(updatedBinding.colorPresetIndex),
            )
        }

        if (existingBinding != null) {
            bindingsState.value = currentBindings.filterNot { it.sourceNoteId == noteId }

            scope.launch {
                repository.deleteBySourceId(noteId)
            }

            return ShareInteractionResult.Updated(current = getShareState(noteId))
        }

        if (hasIncomingBindings || currentBindings.size >= MAX_BINDINGS) {
            return ShareInteractionResult.Ignored
        }

        val colorPresetIndex = Random.nextInt(PRESET_COLORS.size)
        pendingBindingState.value = PendingBinding(noteId, colorPresetIndex)

        return ShareInteractionResult.Updated(
            current = getShareState(noteId),
            transientHighlightColor = presetColorFor(colorPresetIndex),
        )
    }

    @ColorInt
    fun presetColorFor(index: Int): Int = PRESET_COLORS[index.coerceIn(0, PRESET_COLORS.lastIndex)]

    data class PendingBinding(
        val sourceNoteId: Long,
        val colorPresetIndex: Int,
    )

    data class ShareState(
        @ColorInt val highlightColor: Int? = null,
        val incomingBindingCount: Int = 0,
        val incomingBindingColors: List<Int> = emptyList(),
    )

    sealed interface ShareInteractionResult {
        data object Ignored : ShareInteractionResult
        data class Updated(
            val current: ShareState,
            @ColorInt val transientHighlightColor: Int? = null,
        ) : ShareInteractionResult
    }

    companion object {
        const val MAX_BINDINGS = 10

        // Ten intentionally distinct preset colors used for one-way quick-switch bindings.
        private val PRESET_COLORS = listOf(
            0xFFE57373.toInt(),
            0xFFF06292.toInt(),
            0xFFBA68C8.toInt(),
            0xFF9575CD.toInt(),
            0xFF7986CB.toInt(),
            0xFF4FC3F7.toInt(),
            0xFF4DB6AC.toInt(),
            0xFF81C784.toInt(),
            0xFFFFB74D.toInt(),
            0xFFA1887F.toInt(),
        )
    }
}
