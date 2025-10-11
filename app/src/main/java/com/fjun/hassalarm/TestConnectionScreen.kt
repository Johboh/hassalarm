package com.fjun.hassalarm

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun TestConnectionScreen(
    viewModel: TestConnectionViewModel = viewModel(),
    host: String,
    token: String,
    entityId: String,
    accessType: AccessType?,
    entityIdIsLegacy: Boolean
) {
    val context = LocalContext.current
    val log by viewModel.log.collectAsState()
    val status by viewModel.status.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val isSuccessful by viewModel.isSuccessful.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.runTest(host, token, entityId, accessType, entityIdIsLegacy)
    }

    Scaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars),
        topBar = {
            TopAppBar(
                backgroundColor = MaterialTheme.colors.primary,
                title = { Text(text = stringResource(id = R.string.test_connection)) },
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
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 15.dp)
                ) {
                    if (isRunning) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        val allGood = isSuccessful == true
                        Icon(
                            imageVector = if (allGood) Icons.Filled.CheckCircle else Icons.Filled.Error,
                            contentDescription = if (allGood) "Success" else "Error",
                            tint = if (allGood) Color.Green else Color.Red,
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = status)
                }
                SelectionContainer {
                    Text(
                        text = log,
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    )
                }
                Spacer(modifier = Modifier.height(5.dp))
                Button(
                    onClick = { (context as? Activity)?.finish() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(id = R.string.button_close))
                }
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun TestConnectionScreenPreview() {
    TestConnectionScreen(
        host = "http://localhost:8123",
        token = "token",
        entityId = "input_datetime.next_alarm",
        accessType = AccessType.LONG_LIVED_TOKEN,
        entityIdIsLegacy = false
    )
}
