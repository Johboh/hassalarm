package com.fjun.hassalarm

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Divider
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fjun.hassalarm.history.AppDatabase
import com.fjun.hassalarm.history.Publish
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = viewModel(
        factory = HistoryViewModelFactory(
            AppDatabase.getDatabase(LocalContext.current).publishDao()
        )
    )
) {
    val context = LocalContext.current
    val history by viewModel.list.collectAsState()
    val openDialog = remember { mutableStateOf<Publish?>(null) }

    if (openDialog.value != null) {
        AlertDialog(
            onDismissRequest = { openDialog.value = null },
            title = { Text(text = stringResource(id = R.string.history_error_dialog_title)) },
            text = { Text(text = openDialog.value?.errorMessage ?: "") },
            confirmButton = {
                Button(
                    onClick = { openDialog.value = null }
                ) {
                    Text(text = stringResource(id = R.string.history_error_dialog_button_ok))
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars),
        topBar = {
            TopAppBar(
                backgroundColor = MaterialTheme.colors.primary,
                title = { Text(text = stringResource(id = R.string.action_history)) },
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
                    .padding(18.dp)
            ) {
                Text(text = stringResource(id = R.string.history_info))
                Spacer(modifier = Modifier.height(10.dp))
                Button(onClick = {
                    viewModel.clearHistory()
                    Toast.makeText(context, R.string.history_cleared, Toast.LENGTH_SHORT).show()
                }) {
                    Text(text = stringResource(id = R.string.history_clear))
                }
                Spacer(modifier = Modifier.height(10.dp))
                LazyColumn {
                    itemsIndexed(history) { index, publish ->
                        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        val date = Date(publish.timestamp)
                        val alarmDate = if ((publish.triggerTimestamp ?: 0) > 0) {
                            Date(publish.triggerTimestamp ?: 0)
                        } else {
                            null
                        }
                        Row(
                            modifier = Modifier
                                .clickable {
                                    if (publish.errorMessage != null) {
                                        openDialog.value = publish
                                    }
                                }
                                .background(
                                    if (index % 2 == 0) Color.LightGray.copy(alpha = 0.3f) else Color.Transparent
                                )
                                .padding(vertical = 8.dp, horizontal = 4.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (publish.successful) Icons.Filled.CheckCircle else Icons.Filled.Error,
                                contentDescription = if (publish.successful) "Success" else "Error",
                                tint = if (publish.successful) Color.Green else Color.Red,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Column {
                                Text(text = "Published at: ${sdf.format(date)}")
                                if (alarmDate != null) {
                                    Text(text = "Alarm at: ${sdf.format(alarmDate)}")
                                }
                                if (!publish.successful && !publish.errorMessage.isNullOrEmpty()) {
                                    Text(text = "Error: ${publish.errorMessage}")
                                }
                            }
                        }
                        Divider()
                    }
                }
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun HistoryScreenPreview() {
    HistoryScreen()
}
