package com.fjun.hassalarm

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.fjun.hassalarm.ui.theme.HassAlarmTheme

class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HassAlarmTheme {
                AboutScreen()
            }
        }
    }

    companion object {
        fun createIntent(context: Context?): Intent = Intent(context, AboutActivity::class.java)
    }
}