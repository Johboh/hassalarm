package com.fjun.hassalarm

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material.MaterialTheme
import com.fjun.hassalarm.HistoryActivity.Companion.createIntent

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                MainScreen(viewModel = viewModel, onMenuClick = {
                    when (it) {
                        R.id.action_about -> startActivity(AboutActivity.createIntent(this))
                        R.id.action_history -> startActivity(createIntent(this))
                        R.id.action_banlist -> startActivity(BanActivity.createIntent(this))
                    }
                })
            }
        }
        NextAlarmUpdaterJob.scheduleJob(this)
    }

    override fun onResume() {
        super.onResume()
        viewModel.updateView()
    }
}