package com.dietpizza.rxplayer.feature.network

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dietpizza.rxplayer.core.netfs.RemoteFileSystemFactory
import com.dietpizza.rxplayer.core.netfs.RemotePath
import com.dietpizza.rxplayer.core.netfs.ServerRepository
import com.dietpizza.rxplayer.core.netfs.fs.HostKeyMismatchException
import com.dietpizza.rxplayer.core.netfs.fs.NetErrorKind
import com.dietpizza.rxplayer.core.netfs.fs.NetFsErrors
import com.dietpizza.rxplayer.core.netfs.model.NetProtocol
import com.dietpizza.rxplayer.core.netfs.model.NetServer
import com.dietpizza.rxplayer.core.netfs.model.PasswordChange
import com.dietpizza.rxplayer.core.netfs.model.ServerDraft
import com.dietpizza.rxplayer.core.netfs.model.ServerOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The four protocol choices in the form; FTPS is FTP + the TLS switch. */
internal val FormProtocols = listOf(NetProtocol.SMB, NetProtocol.SFTP, NetProtocol.FTP, NetProtocol.WEBDAV)

@Immutable
data class ServerForm(
    /** One of [FormProtocols]. */
    val protocol: NetProtocol = NetProtocol.SMB,
    val name: String = "",
    val host: String = "",
    /** Blank = the protocol's default port. */
    val port: String = "",
    val path: String = "",
    val username: String = "",
    val password: String = "",
    val passwordEdited: Boolean = false,
    val hasStoredPassword: Boolean = false,
    val anonymous: Boolean = false,
    val domain: String = "",
    val ftpTls: Boolean = false,
    val passive: Boolean = true,
    val https: Boolean = false,
    /** SFTP: fingerprint to trust when saving (from the stored config, a test, or "Trust new key"). */
    val hostKey: String? = null,
) {
    val effectiveProtocol: NetProtocol get() = if (protocol == NetProtocol.FTP && ftpTls) NetProtocol.FTPS else protocol
    val defaultPort: Int get() = effectiveProtocol.defaultPort(https)
    val usesCredentials: Boolean get() = protocol == NetProtocol.SFTP || !anonymous
}

@Immutable
sealed interface TestState {
    data object Idle : TestState
    data object Running : TestState
    data class Ok(val itemCount: Int, val newHostKey: String?) : TestState
    data class Failed(val kind: NetErrorKind, val detail: String, val mismatch: HostKeyMismatchException? = null) : TestState
}

enum class FieldError { HOST_REQUIRED, PORT_INVALID, SHARE_REQUIRED, USER_REQUIRED }

@Immutable
data class ServerUiState(
    val isNew: Boolean,
    val loading: Boolean,
    val form: ServerForm = ServerForm(),
    val errors: Set<FieldError> = emptySet(),
    val test: TestState = TestState.Idle,
    val saving: Boolean = false,
)

@HiltViewModel
class ServerViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val servers: ServerRepository,
    private val factory: RemoteFileSystemFactory,
) : ViewModel() {

    private val serverId: Long = savedState[ServerActivity.EXTRA_SERVER_ID] ?: 0L
    private var loaded: NetServer? = null
    private var testJob: Job? = null

    private val _uiState = MutableStateFlow(ServerUiState(isNew = serverId == 0L, loading = serverId != 0L))
    val uiState: StateFlow<ServerUiState> = _uiState

    private val _finished = Channel<Unit>(Channel.CONFLATED)
    val finished = _finished.receiveAsFlow()

    init {
        if (serverId != 0L) {
            viewModelScope.launch {
                val s = servers.get(serverId)
                loaded = s
                if (s == null) {
                    _uiState.update { it.copy(isNew = true, loading = false) }
                    return@launch
                }
                val protocol = if (s.protocol == NetProtocol.FTPS) NetProtocol.FTP else s.protocol
                val form = ServerForm(
                    protocol = protocol,
                    name = s.name,
                    host = s.host,
                    port = if (s.port == s.protocol.defaultPort(s.options.https)) "" else s.port.toString(),
                    path = if (s.basePath == "/" && s.protocol != NetProtocol.SMB) "" else s.basePath,
                    username = s.username.orEmpty(),
                    hasStoredPassword = s.hasPassword,
                    anonymous = s.options.anonymous,
                    domain = s.options.domain.orEmpty(),
                    ftpTls = s.protocol == NetProtocol.FTPS,
                    passive = s.options.passive,
                    https = s.options.https,
                    hostKey = s.options.hostKey,
                )
                _uiState.update { it.copy(loading = false, form = form) }
            }
        }
    }

    fun update(transform: (ServerForm) -> ServerForm) {
        _uiState.update { state ->
            val before = state.form
            var after = transform(before)
            // A different machine has a different host key: forget the trusted one.
            if (after.host.trim() != before.host.trim() || after.port != before.port || after.protocol != before.protocol) {
                after = after.copy(hostKey = loaded?.options?.hostKey?.takeIf { sameEndpoint(after) })
            }
            val test = if (after != before && state.test !is TestState.Running) TestState.Idle else state.test
            state.copy(form = after, errors = state.errors - fieldsTouched(before, after), test = test)
        }
    }

    private fun sameEndpoint(form: ServerForm): Boolean {
        val l = loaded ?: return false
        val port = form.port.toIntOrNull() ?: form.defaultPort
        return l.protocol == NetProtocol.SFTP && form.protocol == NetProtocol.SFTP &&
            l.host.equals(form.host.trim(), ignoreCase = true) && l.port == port
    }

    private fun fieldsTouched(a: ServerForm, b: ServerForm): Set<FieldError> = buildSet {
        if (a.host != b.host) add(FieldError.HOST_REQUIRED)
        if (a.port != b.port) add(FieldError.PORT_INVALID)
        if (a.path != b.path || a.protocol != b.protocol) add(FieldError.SHARE_REQUIRED)
        if (a.username != b.username || a.protocol != b.protocol) add(FieldError.USER_REQUIRED)
    }

    private fun validate(form: ServerForm): Set<FieldError> = buildSet {
        if (form.host.isBlank()) add(FieldError.HOST_REQUIRED)
        if (form.port.isNotBlank() && form.port.toIntOrNull()?.takeIf { it in 1..65535 } == null) add(FieldError.PORT_INVALID)
        if (form.protocol == NetProtocol.SMB && RemotePath.segments(form.path).isEmpty()) add(FieldError.SHARE_REQUIRED)
        if (form.protocol == NetProtocol.SFTP && form.username.isBlank()) add(FieldError.USER_REQUIRED)
    }

    private fun draft(form: ServerForm): ServerDraft {
        val protocol = form.effectiveProtocol
        val password = when {
            !form.usesCredentials -> PasswordChange.Clear
            form.passwordEdited && form.password.isEmpty() -> PasswordChange.Clear
            form.passwordEdited -> PasswordChange.Set(form.password)
            else -> PasswordChange.Keep
        }
        return ServerDraft(
            id = loaded?.id ?: 0L,
            protocol = protocol,
            name = form.name,
            host = form.host.trim(),
            port = form.port.toIntOrNull() ?: form.defaultPort,
            basePath = form.path.ifBlank { "/" },
            username = if (form.usesCredentials) form.username.trim() else null,
            password = password,
            options = ServerOptions(
                anonymous = form.anonymous && protocol != NetProtocol.SFTP,
                domain = form.domain.trim().ifEmpty { null }.takeIf { protocol == NetProtocol.SMB },
                passive = form.passive,
                https = form.https && protocol == NetProtocol.WEBDAV,
                hostKey = form.hostKey.takeIf { protocol == NetProtocol.SFTP },
            ),
        )
    }

    fun testConnection() {
        val form = _uiState.value.form
        val errors = validate(form)
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(errors = errors) }
            return
        }
        testJob?.cancel()
        _uiState.update { it.copy(test = TestState.Running) }
        testJob = viewModelScope.launch {
            val draft = draft(form)
            var newKey: String? = null
            val result = try {
                val spec = servers.connectionSpec(draft)
                val fs = factory.create(spec) { newKey = it }
                try {
                    TestState.Ok(fs.testConnection(spec.basePath), newKey)
                } finally {
                    fs.close()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: HostKeyMismatchException) {
                TestState.Failed(e.kind, NetFsErrors.message(e), e)
            } catch (e: Exception) {
                TestState.Failed(NetFsErrors.classify(e), NetFsErrors.message(e))
            }
            _uiState.update { state ->
                // Ignore a result for a form the user has since edited.
                if (state.form != form) return@update state.copy(test = TestState.Idle)
                val ok = result as? TestState.Ok
                state.copy(
                    test = result,
                    form = if (ok?.newHostKey != null) state.form.copy(hostKey = ok.newHostKey) else state.form,
                )
            }
        }
    }

    /** After a host-key mismatch, the user explicitly trusts the new key. */
    fun trustNewHostKey() {
        val failed = _uiState.value.test as? TestState.Failed ?: return
        val actual = failed.mismatch?.actual ?: return
        _uiState.update { it.copy(form = it.form.copy(hostKey = actual), test = TestState.Idle) }
        testConnection()
    }

    fun save() {
        val state = _uiState.value
        if (state.saving || state.loading) return
        val errors = validate(state.form)
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(errors = errors) }
            return
        }
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            servers.save(draft(state.form))
            _finished.send(Unit)
        }
    }
}
