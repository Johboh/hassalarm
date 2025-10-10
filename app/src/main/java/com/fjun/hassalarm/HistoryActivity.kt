package com.fjun.hassalarm

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material.MaterialTheme

class HistoryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                HistoryScreen()
            }
        }
    }

    companion object {
        fun createIntent(context: Context) = Intent(context, HistoryActivity::class.java)
    }
}