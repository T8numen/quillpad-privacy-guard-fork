package org.qosp.notes.components.security

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.qosp.notes.preferences.AppLockMode
import org.qosp.notes.preferences.PreferenceRepository

class AppLockManager(
    preferenceRepository: PreferenceRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val initialMode = runBlocking(Dispatchers.IO) {
        preferenceRepository.get<AppLockMode>().first()
    }
    private val unlockedState = MutableStateFlow(initialMode == AppLockMode.DISABLED)

    val appLockMode: StateFlow<AppLockMode> = preferenceRepository
        .get<AppLockMode>()
        .stateIn(scope, SharingStarted.Eagerly, initialMode)

    val isEnabled: StateFlow<Boolean> = appLockMode
        .combine(unlockedState) { mode, _ -> mode == AppLockMode.BIOMETRIC }
        .stateIn(scope, SharingStarted.Eagerly, initialMode == AppLockMode.BIOMETRIC)

    val isContentVisible: StateFlow<Boolean> = combine(appLockMode, unlockedState) { mode, unlocked ->
        mode == AppLockMode.DISABLED || unlocked
    }.stateIn(scope, SharingStarted.Eagerly, initialMode == AppLockMode.DISABLED)

    val isLocked: StateFlow<Boolean> = isContentVisible
        .combine(isEnabled) { contentVisible, enabled -> enabled && !contentVisible }
        .stateIn(scope, SharingStarted.Eagerly, initialMode == AppLockMode.BIOMETRIC)

    init {
        scope.launch {
            appLockMode.collect { mode ->
                if (mode == AppLockMode.DISABLED) {
                    unlockedState.value = true
                }
            }
        }
    }

    fun unlock() {
        unlockedState.value = true
    }

    fun lock() {
        if (appLockMode.value == AppLockMode.BIOMETRIC) {
            unlockedState.value = false
        }
    }
}
