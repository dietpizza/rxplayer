package com.dietpizza.rxplayer.feature.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dietpizza.rxplayer.core.designsystem.theme.RxTheme
import com.dietpizza.rxplayer.core.netfs.fs.NetErrorKind
import com.dietpizza.rxplayer.core.netfs.model.NetProtocol

@Composable
internal fun ServerScreen(onBack: () -> Unit, viewModel: ServerViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.finished.collect { onBack() } }
    ServerContent(
        state = state,
        onBack = onBack,
        onUpdate = viewModel::update,
        onTest = viewModel::testConnection,
        onTrustNewKey = viewModel::trustNewHostKey,
        onSave = viewModel::save,
    )
}

@Composable
internal fun ServerContent(
    state: ServerUiState,
    onBack: () -> Unit,
    onUpdate: ((ServerForm) -> ServerForm) -> Unit,
    onTest: () -> Unit,
    onTrustNewKey: () -> Unit,
    onSave: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isNew) R.string.server_add_title else R.string.server_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.server_back))
                    }
                },
            )
        },
        bottomBar = {
            BottomActions(state, onTest = onTest, onTrustNewKey = onTrustNewKey, onSave = onSave)
        },
    ) { innerPadding ->
        if (state.loading) return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FormFields(state.form, state.errors, onUpdate)
        }
    }
}

@Composable
private fun FormFields(form: ServerForm, errors: Set<FieldError>, onUpdate: ((ServerForm) -> ServerForm) -> Unit) {
    ProtocolGroup(form.protocol) { p -> onUpdate { it.copy(protocol = p) } }

    Field(
        value = form.host,
        onValueChange = { v -> onUpdate { it.copy(host = v) } },
        label = stringResource(R.string.server_host),
        placeholder = stringResource(R.string.server_host_hint),
        error = stringResource(R.string.server_error_host_required).takeIf { FieldError.HOST_REQUIRED in errors },
        keyboardType = KeyboardType.Uri,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Field(
            value = form.path,
            onValueChange = { v -> onUpdate { it.copy(path = v) } },
            label = stringResource(if (form.protocol == NetProtocol.SMB) R.string.server_share else R.string.server_path),
            placeholder = stringResource(if (form.protocol == NetProtocol.SMB) R.string.server_share_hint else R.string.server_path_hint),
            error = stringResource(R.string.server_error_share_required).takeIf { FieldError.SHARE_REQUIRED in errors },
            keyboardType = KeyboardType.Uri,
            modifier = Modifier.weight(1f),
        )
        Field(
            value = form.port,
            onValueChange = { v -> onUpdate { it.copy(port = v.filter(Char::isDigit).take(5)) } },
            label = stringResource(R.string.server_port),
            placeholder = form.defaultPort.toString(),
            error = stringResource(R.string.server_error_port).takeIf { FieldError.PORT_INVALID in errors },
            keyboardType = KeyboardType.Number,
            modifier = Modifier.widthIn(max = 112.dp),
        )
    }
    Field(
        value = form.name,
        onValueChange = { v -> onUpdate { it.copy(name = v) } },
        label = stringResource(R.string.server_name),
        placeholder = stringResource(R.string.server_name_hint),
    )

    if (form.protocol != NetProtocol.SFTP) {
        SwitchRow(stringResource(R.string.server_anonymous), form.anonymous) { v -> onUpdate { it.copy(anonymous = v) } }
    }
    if (form.usesCredentials) {
        Field(
            value = form.username,
            onValueChange = { v -> onUpdate { it.copy(username = v) } },
            label = stringResource(R.string.server_username),
            error = stringResource(R.string.server_error_user_required).takeIf { FieldError.USER_REQUIRED in errors },
        )
        PasswordField(form, onUpdate)
    }

    when (form.protocol) {
        NetProtocol.SMB -> Field(
            value = form.domain,
            onValueChange = { v -> onUpdate { it.copy(domain = v) } },
            label = stringResource(R.string.server_domain),
            placeholder = stringResource(R.string.server_domain_hint),
        )
        NetProtocol.FTP, NetProtocol.FTPS -> {
            SwitchRow(stringResource(R.string.server_ftp_tls), form.ftpTls) { v -> onUpdate { it.copy(ftpTls = v) } }
            SwitchRow(stringResource(R.string.server_ftp_passive), form.passive) { v -> onUpdate { it.copy(passive = v) } }
        }
        NetProtocol.WEBDAV ->
            SwitchRow(stringResource(R.string.server_webdav_https), form.https) { v -> onUpdate { it.copy(https = v) } }
        NetProtocol.SFTP -> Column(Modifier.padding(horizontal = 4.dp)) {
            Text(stringResource(R.string.server_host_key), style = MaterialTheme.typography.labelLarge)
            Text(
                form.hostKey ?: stringResource(R.string.server_host_key_unknown),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Spacer(Modifier.size(8.dp))
}

/** Connected button group (M3 Expressive), one per protocol. */
@Composable
private fun ProtocolGroup(selected: NetProtocol, onSelect: (NetProtocol) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        FormProtocols.forEachIndexed { index, protocol ->
            ToggleButton(
                checked = protocol == selected,
                onCheckedChange = { onSelect(protocol) },
                modifier = Modifier
                    .weight(1f)
                    .semantics { role = Role.RadioButton },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    FormProtocols.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) {
                Text(protocol.label, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next, autoCorrectEnabled = false),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun PasswordField(form: ServerForm, onUpdate: ((ServerForm) -> ServerForm) -> Unit) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = form.password,
        onValueChange = { v -> onUpdate { it.copy(password = v, passwordEdited = true) } },
        label = { Text(stringResource(R.string.server_password)) },
        placeholder = if (form.hasStoredPassword && !form.passwordEdited) {
            { Text(stringResource(R.string.server_password_unchanged)) }
        } else {
            null
        },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = stringResource(if (visible) R.string.server_hide_password else R.string.server_show_password),
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Label + switch. The row itself isn't clickable or highlighted; only the switch shows state (decision 34). */
@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun BottomActions(state: ServerUiState, onTest: () -> Unit, onTrustNewKey: () -> Unit, onSave: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TestResult(state.test, onTrustNewKey)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onTest,
                    enabled = !state.loading && state.test !is TestState.Running,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(if (state.test is TestState.Running) R.string.server_testing else R.string.server_test))
                }
                Button(
                    onClick = onSave,
                    enabled = !state.loading && !state.saving,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.server_save))
                }
            }
        }
    }
}

@Composable
private fun TestResult(test: TestState, onTrustNewKey: () -> Unit) {
    when (test) {
        TestState.Idle -> Unit
        TestState.Running -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LoadingIndicator(Modifier.size(32.dp))
            Text(stringResource(R.string.server_testing), style = MaterialTheme.typography.bodyMedium)
        }
        is TestState.Ok -> ResultLine(
            ok = true,
            title = if (test.newHostKey != null) {
                stringResource(R.string.server_test_ok_new_key, test.newHostKey)
            } else {
                stringResource(R.string.server_test_ok, test.itemCount)
            },
            detail = null,
        )
        is TestState.Failed -> Column {
            val mismatch = test.mismatch
            ResultLine(
                ok = false,
                title = stringResource(test.kind.titleRes()),
                detail = if (mismatch != null) {
                    stringResource(R.string.error_host_key_body, mismatch.expected, mismatch.actual)
                } else {
                    test.detail.takeIf { test.kind == NetErrorKind.OTHER || test.kind == NetErrorKind.CONFIG || test.kind == NetErrorKind.NOT_FOUND }
                },
            )
            if (mismatch != null) {
                TextButton(onClick = onTrustNewKey, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text(stringResource(R.string.server_trust_new_key))
                }
            }
        }
    }
}

@Composable
private fun ResultLine(ok: Boolean, title: String, detail: String?) {
    val color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp),
        )
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = color)
            if (detail != null) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ServerScreenPreview() {
    RxTheme {
        ServerContent(
            state = ServerUiState(
                isNew = true,
                loading = false,
                form = ServerForm(host = "nas.local", path = "/Media", username = "rohan"),
                test = TestState.Ok(12, null),
            ),
            onBack = {}, onUpdate = {}, onTest = {}, onTrustNewKey = {}, onSave = {},
        )
    }
}
