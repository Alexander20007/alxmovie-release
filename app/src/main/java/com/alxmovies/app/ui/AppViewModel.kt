package com.alxmovies.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alxmovies.app.data.Api
import com.alxmovies.app.data.Config
import com.alxmovies.app.data.Friends
import com.alxmovies.app.data.Graph
import com.alxmovies.app.data.Profile
import com.alxmovies.app.data.Repo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Estado compartilhado do app: login, perfis e perfil ativo. */
class AppViewModel : ViewModel() {
    private val session = Graph.session

    val loggedIn: StateFlow<Boolean> = session.loggedInFlow

    private val _profiles = MutableStateFlow<List<Profile>>(emptyList())
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _activeId = MutableStateFlow(session.profileId)
    val activeId: StateFlow<String?> = _activeId.asStateFlow()

    /** Aviso para a tela de login (ex.: sessão de convidado expirada). */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private val _badges = MutableStateFlow(Badges())
    val badges: StateFlow<Badges> = _badges.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                checkGuestExpired()
                refreshBadgesNow()
                delay(60_000)
            }
        }
    }

    private suspend fun refreshBadgesNow() {
        val pid = _activeId.value ?: return
        if (!session.loggedIn) return
        val m = runCatching { Friends.unreadTotal(pid) }.getOrDefault(0)
        val n = runCatching { Friends.notifications(pid).size }.getOrDefault(0)
        _badges.value = Badges(m, n)
    }

    fun refreshBadges() { viewModelScope.launch { refreshBadgesNow() } }

    private suspend fun checkGuestExpired() {
        if (!session.loggedIn || !session.isAnonymous) return
        val started = session.guestStartedAt
        if (started > 0 && System.currentTimeMillis() - started > Config.GUEST_SESSION_MS) {
            signOutNow()
            _notice.value = "Sua sessão de convidado expirou depois de 24 horas. Entre de novo ou crie uma nova sessão de convidado."
        }
    }

    fun consumeNotice() { _notice.value = null }

    fun onLoggedIn() {
        _profiles.value = emptyList()
        _loaded.value = false
        _error.value = null
        session.profileId = null
        _activeId.value = null
    }

    fun loadProfiles() {
        val uid = session.userId ?: return
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                _profiles.value = Repo.fetchProfiles(uid)
                _loaded.value = true
            } catch (e: Exception) {
                _error.value = e.message ?: "Não foi possível carregar os perfis."
            } finally {
                _loading.value = false
            }
        }
    }

    fun selectProfile(id: String) {
        session.profileId = id
        _activeId.value = id
        _badges.value = Badges()
        refreshBadges()
    }

    fun clearProfile() {
        session.profileId = null
        _activeId.value = null
    }

    private suspend fun signOutNow() {
        Api.signOut()
        _profiles.value = emptyList()
        _loaded.value = false
        _activeId.value = null
    }

    fun signOut() {
        viewModelScope.launch { signOutNow() }
    }
}
