package com.fjun.hassalarm

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.regex.Pattern

@Composable
fun BanScreen(viewModel: BanViewModel = viewModel()) {
    val bannedPackages by viewModel.bannedPackages.collectAsState()
    val nextAlarmPackage by viewModel.nextAlarmPackage.collectAsState()
    var manualPackage by remember { mutableStateOf("") }
    val isManualPackageValid = remember(manualPackage) {
        isValidPackageName(manualPackage)
    }
    val context = LocalContext.current

    Scaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars),
        topBar = {
            TopAppBar(
                backgroundColor = MaterialTheme.colors.primary,
                title = { Text(text = stringResource(id = R.string.action_ban)) },
                navigationIcon = {
                    IconButton(onClick = { (context as? Activity)?.finish() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues) // Apply padding from Scaffold
                    .padding(18.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.ban_intro),
                    style = MaterialTheme.typography.body1
                )
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = stringResource(id = R.string.ban_howto),
                    style = MaterialTheme.typography.body1
                )
                Spacer(modifier = Modifier.height(15.dp))
                Text(
                    text = "Package name for next scheduled alarm",
                    style = MaterialTheme.typography.h6.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.addPackage(nextAlarmPackage) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = nextAlarmPackage.isNotEmpty() && nextAlarmPackage != stringResource(id = R.string.ban_no_alarm_set)
                ) {
                    Text(text = stringResource(id = R.string.ban_button_add_to_ignore_list))
                }
                Spacer(modifier = Modifier.height(15.dp))
                OutlinedTextField(
                    value = manualPackage,
                    onValueChange = { manualPackage = it },
                    label = { Text(text = stringResource(id = R.string.ban_manual_package_name_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        viewModel.addPackage(manualPackage)
                        manualPackage = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isManualPackageValid
                ) {
                    Text(text = stringResource(id = R.string.ban_button_add_manual_to_ignore_list))
                }
                Spacer(modifier = Modifier.height(15.dp))
                Text(
                    text = stringResource(id = R.string.ban_ignored),
                    style = MaterialTheme.typography.h6.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(5.dp))
                LazyColumn {
                    items(bannedPackages) { packageName ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = packageName)
                            Button(onClick = { viewModel.removePackage(packageName) }) {
                                Text(text = stringResource(id = R.string.ban_row_remove))
                            }
                        }
                    }
                }
            }
        }
    )
}

private fun isValidPackageName(packageName: String): Boolean {
    return Pattern.matches("^[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)+$", packageName)
}

@Preview(showBackground = true)
@Composable
fun BanScreenPreview() {
    BanScreen()
}