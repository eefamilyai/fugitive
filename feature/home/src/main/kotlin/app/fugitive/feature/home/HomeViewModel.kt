package app.fugitive.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fugitive.core.data.backend.JoinException
import app.fugitive.core.data.prefs.AppPreferences
import app.fugitive.core.data.prefs.UserPreferences
import app.fugitive.core.data.session.GameSessionManager
import app.fugitive.core.data.session.SessionState
import app.fugitive.model.PlayerProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val prefs: AppPreferences = AppPreferences(),
    val busyMessage: String? = null,
    val error: String? = null,
    val onlineAvailable: Boolean = false,
)

class HomeViewModel(
    private val sessions: GameSessionManager,
    private val prefs: UserPreferences,
) : ViewModel() {
    private val error = MutableStateFlow<String?>(null)

    val state: StateFlow<HomeUiState> = combine(prefs.preferences, sessions.state, error, sessions.notice) { p, s, e, notice ->
        HomeUiState(
            prefs = p,
            busyMessage = (s as? SessionState.Connecting)?.message,
            error = e ?: notice,
            onlineAvailable = sessions.onlineAvailable,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(onlineAvailable = sessions.onlineAvailable))

    fun host() = launch { sessions.create(practice = false) }
    fun practice() = launch { sessions.create(practice = true) }
    fun join(code: String) = launch { sessions.join(code) }
    fun rejoin() = launch {
        val last = prefs.current().lastLobby ?: return@launch Result.failure<Unit>(IllegalStateException("Nothing to rejoin"))
        sessions.rejoin(last)
    }
    fun forgetLastLobby() = viewModelScope.launch { prefs.setLastLobby(null) }

    fun setPractice(bots: Int, difficulty: Int) = viewModelScope.launch { prefs.setPractice(bots, difficulty) }
    fun saveProfile(profile: PlayerProfile) = viewModelScope.launch { prefs.setProfile(profile) }

    fun dismissError() { error.value = null; sessions.consumeNotice() }

    private fun launch(block: suspend () -> Result<*>) = viewModelScope.launch {
        error.value = null
        block().onFailure { e ->
            error.value = when (e) {
                is JoinException -> e.message
                else -> e.message ?: "Something went wrong"
            }
        }
    }
}

class OnboardingViewModel(private val prefs: UserPreferences) : ViewModel() {
    val preferences = prefs.preferences.stateIn(viewModelScope, SharingStarted.Eagerly, AppPreferences())

    /** Persists the profile, then calls [then]. Navigation must wait, or leaving the screen cancels the write. */
    fun finish(profile: PlayerProfile, then: () -> Unit) = viewModelScope.launch {
        prefs.setProfile(profile)
        prefs.setOnboarded(true)
        then()
    }
}
