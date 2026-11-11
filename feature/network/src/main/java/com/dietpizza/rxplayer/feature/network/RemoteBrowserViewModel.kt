package com.dietpizza.rxplayer.feature.network

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dietpizza.rxplayer.core.netfs.NetFsPool
import com.dietpizza.rxplayer.core.netfs.RemotePath
import com.dietpizza.rxplayer.core.netfs.ServerRepository
import com.dietpizza.rxplayer.core.netfs.fs.NetErrorKind
import com.dietpizza.rxplayer.core.netfs.fs.NetFsErrors
import com.dietpizza.rxplayer.core.netfs.model.NetProtocol
import com.dietpizza.rxplayer.core.netfs.model.RemoteEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
sealed interface Listing {
    data object Loading : Listing
    /** [subtitles]: each entry's "size · date" line by path, formatted off the main thread. */
    data class Loaded(val entries: List<RemoteEntry>, val subtitles: Map<String, String> = emptyMap()) : Listing
    data class Failed(val kind: NetErrorKind, val detail: String) : Listing
}

@Immutable
data class BrowserUiState(
    val serverName: String,
    val protocol: NetProtocol? = null,
    val root: String = RemotePath.ROOT,
    val path: String = RemotePath.ROOT,
    val listing: Listing = Listing.Loading,
    val refreshing: Boolean = false,
    val showAll: Boolean = false,
    val serverMissing: Boolean = false,
) {
    // Derived once per state (not part of equals); sorting a big folder on every read would add up.
    val rows: List<RemoteEntry> by lazy(LazyThreadSafetyMode.NONE) {
        (listing as? Listing.Loaded)?.entries?.let { BrowserListing.visible(it, showAll) }.orEmpty()
    }

    val hidesFiles: Boolean by lazy(LazyThreadSafetyMode.NONE) {
        !showAll && (listing as? Listing.Loaded)?.entries?.let(BrowserListing::hidesFiles) == true
    }

    val crumbs: List<RemotePath.Crumb> by lazy(LazyThreadSafetyMode.NONE) { RemotePath.breadcrumbs(root, path, serverName) }

    val canGoUp: Boolean get() = path != root
}

sealed interface BrowserEffect {
    data class Failed(val rename: Boolean, val kind: NetErrorKind, val detail: String) : BrowserEffect
    data class RefreshFailed(val kind: NetErrorKind) : BrowserEffect
}

@HiltViewModel
class RemoteBrowserViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val servers: ServerRepository,
    private val pool: NetFsPool,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val serverId: Long = savedState[RemoteBrowserActivity.EXTRA_SERVER_ID] ?: 0L

    private val _uiState = MutableStateFlow(
        BrowserUiState(
            serverName = savedState[RemoteBrowserActivity.EXTRA_SERVER_NAME] ?: "",
            showAll = savedState[KEY_SHOW_ALL] ?: false,
        ),
    )
    val uiState: StateFlow<BrowserUiState> = _uiState

    private val _effects = Channel<BrowserEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    /** Listings already fetched this session, so going back up is instant. */
    private val cache = HashMap<String, Listing.Loaded>()
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            val server = servers.get(serverId)
            if (server == null) {
                _uiState.update { it.copy(serverMissing = true, listing = Listing.Failed(NetErrorKind.CONFIG, "")) }
                return@launch
            }
            servers.markUsed(serverId)
            val root = server.basePath
            val restored = savedState.get<String>(KEY_PATH)?.takeIf { RemotePath.isWithin(root, it) }
            _uiState.update { it.copy(serverName = server.displayName, protocol = server.protocol, root = root) }
            open(restored ?: root)
        }
    }

    fun open(path: String) {
        val p = RemotePath.normalize(path)
        savedState[KEY_PATH] = p
        val cached = cache[p]
        _uiState.update {
            it.copy(path = p, listing = cached ?: Listing.Loading, refreshing = false)
        }
        if (cached == null) load(p, refreshing = false)
    }

    /** Back: up one folder; false when already at the root (the activity finishes). */
    fun up(): Boolean {
        val state = _uiState.value
        if (!state.canGoUp) return false
        open(RemotePath.parent(state.path) ?: state.root)
        return true
    }

    fun refresh() = load(_uiState.value.path, refreshing = true)

    fun retry() {
        _uiState.update { it.copy(listing = Listing.Loading) }
        load(_uiState.value.path, refreshing = false)
    }

    fun setShowAll(showAll: Boolean) {
        savedState[KEY_SHOW_ALL] = showAll
        _uiState.update { it.copy(showAll = showAll) }
    }

    private fun load(path: String, refreshing: Boolean) {
        if (_uiState.value.serverMissing) return
        loadJob?.cancel()
        if (refreshing) _uiState.update { it.copy(refreshing = true) }
        loadJob = viewModelScope.launch {
            val listing = try {
                val entries = pool.get(serverId).list(path)
                val subtitles = withContext(Dispatchers.Default) { entrySubtitles(context, entries) }
                Listing.Loaded(entries, subtitles).also { cache[path] = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Listing.Failed(NetFsErrors.classify(e), NetFsErrors.message(e))
            }
            val current = _uiState.value
            if (current.path != path) return@launch
            if (refreshing && listing is Listing.Failed && current.listing is Listing.Loaded) {
                // A failed pull-to-refresh keeps the rows already on screen and says why.
                _uiState.update { it.copy(refreshing = false) }
                _effects.send(BrowserEffect.RefreshFailed(listing.kind))
            } else {
                _uiState.update { it.copy(listing = listing, refreshing = false) }
            }
        }
    }

    fun rename(entry: RemoteEntry, newName: String) {
        val name = newName.trim()
        if (name == entry.name || !RemotePath.isValidName(name)) return
        mutate(rename = true) { pool.get(serverId).rename(entry.path, name) }
    }

    fun delete(entry: RemoteEntry) {
        mutate(rename = false) { pool.get(serverId).delete(entry.path, entry.isDirectory) }
    }

    private fun mutate(rename: Boolean, op: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                op()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effects.send(BrowserEffect.Failed(rename, NetFsErrors.classify(e), NetFsErrors.message(e)))
            }
            // Paths below a renamed/deleted folder are stale; re-list where we are.
            cache.clear()
            load(_uiState.value.path, refreshing = false)
        }
    }

    private companion object {
        const val KEY_PATH = "browser.path"
        const val KEY_SHOW_ALL = "browser.showAll"
    }
}
