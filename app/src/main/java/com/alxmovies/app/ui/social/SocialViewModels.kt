package com.alxmovies.app.ui.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alxmovies.app.data.ContentNotification
import com.alxmovies.app.data.ContentNotifications
import com.alxmovies.app.data.DiscoverRow
import com.alxmovies.app.data.FriendLists
import com.alxmovies.app.data.FriendMessage
import com.alxmovies.app.data.FriendNotification
import com.alxmovies.app.data.Friends
import com.alxmovies.app.data.SocialSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

data class FriendsState(
    val loading: Boolean = true,
    val error: String? = null,
    val settings: SocialSettings? = null,
    val lists: FriendLists = FriendLists(emptyList(), emptyList(), emptyList(), emptyList()),
    val suggestions: List<DiscoverRow> = emptyList(),
    val suggestionsLoading: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
)

class FriendsViewModel : ViewModel() {
    private val _state = MutableStateFlow(FriendsState())
    val state: StateFlow<FriendsState> = _state.asStateFlow()
    private var pid: String? = null
    private var pollJob: Job? = null
    private var searchJob: Job? = null

    fun start(profileId: String) {
        if (pid == profileId) return
        pid = profileId
        refresh()
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) { delay(15_000); silentRefresh() }
        }
    }

    private suspend fun silentRefresh() {
        val p = pid ?: return
        runCatching { Friends.lists(p) }.onSuccess { l -> _state.update { it.copy(lists = l) } }
    }

    fun refresh() {
        val p = pid ?: return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val s = Friends.settings(p)
                val l = if (s.friendsEnabled == true) Friends.lists(p) else _state.value.lists
                _state.update { it.copy(loading = false, settings = s, lists = l) }
                if (s.friendsEnabled == true) loadSuggestions("")
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Erro ao carregar") }
            }
        }
    }

    fun consumeMessage() { _state.update { it.copy(message = null) } }

    private fun act(okMessage: String? = null, block: suspend (String) -> Unit) {
        val p = pid ?: return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                block(p)
                val l = Friends.lists(p)
                _state.update { it.copy(busy = false, lists = l, message = okMessage) }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, message = e.message ?: "Algo deu errado") }
            }
        }
    }

    fun enableFriends() {
        val p = pid ?: return
        viewModelScope.launch {
            try {
                val s = Friends.updateSettings(p, friendsEnabled = true, acceptRequests = true)
                _state.update { it.copy(settings = s) }
                refresh()
            } catch (e: Exception) {
                _state.update { it.copy(message = e.message) }
            }
        }
    }

    fun respond(id: String, accept: Boolean) = act(if (accept) "Pedido aceito!" else "Pedido recusado") { Friends.respond(it, id, accept) }
    fun remove(id: String) = act("Removido") { Friends.remove(it, id) }
    fun block(id: String) = act("Bloqueado") { Friends.block(it, id) }
    fun unblock(id: String) = act("Desbloqueado") { Friends.unblock(it, id) }
    fun sendCode(code: String) = act("Pedido enviado!") { Friends.sendRequest(it, code.trim()) }
    fun requestTo(target: String) {
        act("Pedido enviado!") { Friends.sendRequestTo(it, target) }
        _state.update { s -> s.copy(suggestions = s.suggestions.filterNot { it.profileId == target }) }
    }

    fun regenerate() {
        val p = pid ?: return
        viewModelScope.launch {
            try { val s = Friends.regenerateCode(p); _state.update { it.copy(settings = s, message = "Novo código gerado") } }
            catch (e: Exception) { _state.update { it.copy(message = e.message) } }
        }
    }

    fun setDiscoverable(v: Boolean) {
        val p = pid ?: return
        viewModelScope.launch {
            runCatching { Friends.setDiscoverable(p, v) }
                .onSuccess { _state.update { it.copy(settings = it.settings?.copy(discoverable = v)) } }
                .onFailure { e -> _state.update { it.copy(message = e.message) } }
        }
    }

    fun search(q: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(400)
            loadSuggestions(q)
        }
    }

    private suspend fun loadSuggestions(q: String) {
        val p = pid ?: return
        if (q.isNotBlank() && q.trim().length < 2) return
        _state.update { it.copy(suggestionsLoading = true) }
        val list = runCatching { Friends.discover(p, q) }.getOrDefault(emptyList())
        _state.update { it.copy(suggestions = list, suggestionsLoading = false) }
    }
}

data class ChatState(
    val messages: List<FriendMessage> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val sending: Boolean = false,
)

class ChatViewModel : ViewModel() {
    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()
    private var pid: String? = null
    private var fid: String? = null
    private var job: Job? = null

    fun start(profileId: String, friendshipId: String) {
        if (fid == friendshipId) return
        pid = profileId
        fid = friendshipId
        job?.cancel()
        job = viewModelScope.launch {
            var first = true
            while (true) {
                try {
                    val list = Friends.messages(friendshipId)
                    val changed = list.lastOrNull()?.id != _state.value.messages.lastOrNull()?.id || list.size != _state.value.messages.size
                    _state.update { it.copy(messages = list, loading = false, error = null) }
                    if (first || changed) runCatching { Friends.markRead(profileId, friendshipId) }
                    first = false
                } catch (e: Exception) {
                    _state.update { it.copy(loading = false, error = e.message) }
                }
                delay(3_000)
            }
        }
    }

    fun send(text: String, kind: String = "text", payload: JsonObject? = null) {
        val p = pid ?: return
        val f = fid ?: return
        if (kind == "text" && text.isBlank()) return
        _state.update { it.copy(sending = true) }
        viewModelScope.launch {
            try {
                Friends.sendMessage(p, f, text.trim(), kind, payload)
                val list = Friends.messages(f)
                _state.update { it.copy(messages = list, sending = false) }
            } catch (e: Exception) {
                _state.update { it.copy(sending = false, error = e.message) }
            }
        }
    }
}

data class NotificationsState(
    val loading: Boolean = true,
    val friend: List<FriendNotification> = emptyList(),
    val content: List<ContentNotification> = emptyList(),
    val contentLoading: Boolean = true,
    val message: String? = null,
)

class NotificationsViewModel : ViewModel() {
    private val _state = MutableStateFlow(NotificationsState())
    val state: StateFlow<NotificationsState> = _state.asStateFlow()
    private var pid: String? = null

    fun start(profileId: String) {
        if (pid == profileId) return
        pid = profileId
        viewModelScope.launch {
            val f = runCatching { Friends.notifications(profileId) }.getOrDefault(emptyList())
            _state.update { it.copy(loading = false, friend = f) }
        }
        viewModelScope.launch {
            val c = runCatching { ContentNotifications.get(profileId) }.getOrDefault(emptyList())
            _state.update { it.copy(contentLoading = false, content = c) }
        }
    }

    fun respond(n: FriendNotification, accept: Boolean) {
        val p = pid ?: return
        viewModelScope.launch {
            try {
                Friends.respond(p, n.friendshipId, accept)
                _state.update { s -> s.copy(friend = s.friend.filterNot { it.id == n.id }) }
            } catch (e: Exception) {
                _state.update { it.copy(message = e.message) }
            }
        }
    }

    fun markAllRead() {
        val p = pid ?: return
        viewModelScope.launch { runCatching { Friends.markNotificationsRead(p) } }
    }

    fun consumeMessage() { _state.update { it.copy(message = null) } }
}
