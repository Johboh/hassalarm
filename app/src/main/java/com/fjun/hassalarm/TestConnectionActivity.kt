package com.fjun.hassalarm

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material.MaterialTheme

class TestConnectionActivity : AppCompatActivity() {

    private lateinit var host: String
    private lateinit var token: String
    private lateinit var entityId: String
    private var accessType: AccessType? = null
    private var entityIdIsLegacy: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            host = savedInstanceState.getString(EXTRA_HOST, "").orEmpty()
            token = savedInstanceState.getString(EXTRA_TOKEN, "").orEmpty()
            entityId = savedInstanceState.getString(EXTRA_ENTITY_ID, "").orEmpty()
            accessType =
                savedInstanceState.getSerializable(EXTRA_ACCESS_TYPE) as AccessType?
            entityIdIsLegacy = savedInstanceState.getBoolean(EXTRA_ENTITY_ID_IS_LEGACY)
        } else {
            host = intent.getStringExtra(EXTRA_HOST).orEmpty()
            token = intent.getStringExtra(EXTRA_TOKEN).orEmpty()
            entityId = intent.getStringExtra(EXTRA_ENTITY_ID).orEmpty()
            accessType = intent.getSerializableExtra(EXTRA_ACCESS_TYPE) as AccessType?
            entityIdIsLegacy = intent.getBooleanExtra(EXTRA_ENTITY_ID_IS_LEGACY, false)
        }

        enableEdgeToEdge()

        setContent {
            MaterialTheme {
                TestConnectionScreen(
                    host = host,
                    token = token,
                    entityId = entityId,
                    accessType = accessType,
                    entityIdIsLegacy = entityIdIsLegacy
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(EXTRA_HOST, host)
        outState.putString(EXTRA_TOKEN, token)
        outState.putString(EXTRA_ENTITY_ID, entityId)
        outState.putSerializable(EXTRA_ACCESS_TYPE, accessType)
        outState.putBoolean(EXTRA_ENTITY_ID_IS_LEGACY, entityIdIsLegacy)
    }

    companion object {
        private const val EXTRA_HOST = "host"
        private const val EXTRA_TOKEN = "token"
        private const val EXTRA_ENTITY_ID = "entity_id"
        private const val EXTRA_ACCESS_TYPE = "access_type"
        private const val EXTRA_ENTITY_ID_IS_LEGACY = "entity_id_is_legacy"

        fun createIntent(
            context: Context,
            host: String,
            token: String,
            entityId: String,
            accessType: AccessType,
            entityIdIsLegacy: Boolean
        ): Intent {
            val intent = Intent(context, TestConnectionActivity::class.java)
            intent.putExtra(EXTRA_HOST, host)
            intent.putExtra(EXTRA_TOKEN, token)
            intent.putExtra(EXTRA_ENTITY_ID, entityId)
            intent.putExtra(EXTRA_ACCESS_TYPE, accessType)
            intent.putExtra(EXTRA_ENTITY_ID_IS_LEGACY, entityIdIsLegacy)
            return intent
        }
    }
}