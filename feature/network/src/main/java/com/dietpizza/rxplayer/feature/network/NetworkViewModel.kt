package com.dietpizza.rxplayer.feature.network

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dietpizza.rxplayer.core.netfs.ServerRepository
import com.dietpizza.rxplayer.core.netfs.model.NetProtocol
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class ServerRow(
    val id: Long,
    val name: String,
    val protocol: NetProtocol,
    /** "nas.local/Media" (port shown only when non-default). */
    val address: String,
    /** Epoch ms, 0 = never. */
    val lastUsedAt: Long,
)

@HiltViewModel
class NetworkViewModel @Inject constructor(private val servers: ServerRepository) : ViewModel() {

    /** null while the first load is in flight. */
    val rows: StateFlow<List<ServerRow>?> = servers.servers
        .map { list ->
            list.map { s ->
                val port = if (s.port == s.protocol.defaultPort(s.options.https)) "" else ":${s.port}"
                val path = if (s.basePath == "/") "" else s.basePath
                ServerRow(s.id, s.displayName, s.protocol, "${s.host}$port$path", s.lastUsedAt)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun delete(id: Long) {
        viewModelScope.launch { servers.delete(id) }
    }
}
