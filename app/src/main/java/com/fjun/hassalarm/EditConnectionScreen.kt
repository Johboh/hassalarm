package com.fjun.hassalarm

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.RadioButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun EditConnectionScreen(viewModel: EditConnectionViewModel = viewModel()) {
    val context = LocalContext.current
    val host by viewModel.host.collectAsState()
    val token by viewModel.token.collectAsState()
    val entityId by viewModel.entityId.collectAsState()
    val accessType by viewModel.accessType.collectAsState()
    val isEntityLegacy by viewModel.isEntityLegacy.collectAsState()

    val openDialog = remember { mutableStateOf(false) }

    BackHandler(onBack = {
        if (viewModel.isDirty()) {
            openDialog.value = true
        } else {
            (context as? Activity)?.finish()
        }
    })

    if (openDialog.value) {
        AlertDialog(
            onDismissRequest = { openDialog.value = false },
            title = { Text(text = stringResource(id = R.string.unsaved_changes_title)) },
            text = { Text(text = stringResource(id = R.string.unsaved_changes_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        openDialog.value = false
                        (context as? Activity)?.finish()
                    }
                ) {
                    Text(text = stringResource(id = R.string.unsaved_changes_discard))
                }
            },
            dismissButton = {
                Button(
                    onClick = { openDialog.value = false }
                ) {
                    Text(text = stringResource(id = R.string.unsaved_changes_cancel))
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars),
        topBar = {
            TopAppBar(
                backgroundColor = MaterialTheme.colors.primary,
                title = { Text(text = stringResource(id = R.string.edit_connection)) },
                navigationIcon = {
                    IconButton(onClick = { (context as? Activity)?.finish() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        content = { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { viewModel.host.value = it },
                    label = { Text(text = stringResource(id = R.string.hass_io_host_name_ip)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = stringResource(id = R.string.key_type))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = accessType == AccessType.LONG_LIVED_TOKEN,
                        onClick = { viewModel.accessType.value = AccessType.LONG_LIVED_TOKEN },
                        modifier = Modifier.selectable(
                            selected = accessType == AccessType.LONG_LIVED_TOKEN,
                            onClick = { viewModel.accessType.value = AccessType.LONG_LIVED_TOKEN }
                        )
                    )
                    Text(text = stringResource(id = R.string.key_is_token))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = accessType == AccessType.WEB_HOOK,
                        onClick = { viewModel.accessType.value = AccessType.WEB_HOOK },
                        modifier = Modifier.selectable(
                            selected = accessType == AccessType.WEB_HOOK,
                            onClick = { viewModel.accessType.value = AccessType.WEB_HOOK }
                        )
                    )
                    Text(text = stringResource(id = R.string.key_is_webhook))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = accessType == AccessType.LEGACY_API_KEY,
                        onClick = { viewModel.accessType.value = AccessType.LEGACY_API_KEY },
                        modifier = Modifier.selectable(
                            selected = accessType == AccessType.LEGACY_API_KEY,
                            onClick = { viewModel.accessType.value = AccessType.LEGACY_API_KEY }
                        )
                    )
                    Text(text = stringResource(id = R.string.key_is_legacy))
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = token,
                    onValueChange = { viewModel.token.value = it },
                    label = {
                        val text = when (accessType) {
                            AccessType.LONG_LIVED_TOKEN -> stringResource(id = R.string.key_is_token)
                            AccessType.WEB_HOOK -> stringResource(id = R.string.key_is_webhook)
                            AccessType.LEGACY_API_KEY -> stringResource(id = R.string.key_is_legacy)
                        }
                        Text(text = text)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (accessType == AccessType.LONG_LIVED_TOKEN || accessType == AccessType.LEGACY_API_KEY) PasswordVisualTransformation() else PasswordVisualTransformation(
                        mask = '\u0000'
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = entityId,
                    onValueChange = { viewModel.entityId.value = it },
                    label = { Text(text = stringResource(id = R.string.hass_io_entity_id)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(
                        checked = isEntityLegacy,
                        onCheckedChange = { viewModel.isEntityLegacy.value = it },
                        enabled = accessType != AccessType.WEB_HOOK
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(id = R.string.is_input_legacy))
                }
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = {
                        val intent = TestConnectionActivity.createIntent(
                            context,
                            host,
                            token,
                            entityId,
                            accessType,
                            isEntityLegacy
                        )
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(id = R.string.test_connection))
                }
                Spacer(modifier = Modifier.height(5.dp))
                Button(
                    onClick = {
                        viewModel.save()
                        Toast.makeText(context, R.string.toast_saved, Toast.LENGTH_SHORT).show()
                        (context as? Activity)?.finish()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Save and close")
                }
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun EditConnectionScreenPreview() {
    EditConnectionScreen()
}
