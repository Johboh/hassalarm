package com.fjun.hassalarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.Button
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun MainScreen(viewModel: MainViewModel = viewModel(), onMenuClick: (Int) -> Unit) {
    val context = LocalContext.current
    val publishStatus by viewModel.publishStatus.collectAsState()
    val lastPublishAt by viewModel.lastPublishAt.collectAsState()
    val lastSuccessfulPublishAt by viewModel.lastSuccessfulPublishAt.collectAsState()
    val nextAlarm by viewModel.nextAlarm.collectAsState()
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.app_name)) },
                actions = {
                    IconButton(onClick = { showMenu = !showMenu }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(onClick = {
                            onMenuClick(R.id.action_about)
                            showMenu = false
                        }) {
                            Text(text = stringResource(id = R.string.action_about))
                        }
                        DropdownMenuItem(onClick = {
                            onMenuClick(R.id.action_history)
                            showMenu = false
                        }) {
                            Text(text = stringResource(id = R.string.action_history))
                        }
                        DropdownMenuItem(onClick = {
                            onMenuClick(R.id.action_banlist)
                            showMenu = false
                        }) {
                            Text(text = stringResource(id = R.string.action_ban))
                        }
                    }
                }
            )
        },
        content = { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = publishStatus,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                if (lastPublishAt.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = lastPublishAt)
                }
                if (lastSuccessfulPublishAt.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = lastSuccessfulPublishAt)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = nextAlarm)
                Spacer(modifier = Modifier.height(10.dp))
                Button(onClick = {
                    context.startActivity(EditConnectionActivity.createIntent(context))
                }) {
                    Text(text = stringResource(id = R.string.edit_connection))
                }
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    MainScreen(onMenuClick = {})
}
