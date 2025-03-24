package com.fjun.hassalarm

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.fjun.hassalarm.databinding.ActivityEditConnectionBinding
import com.fjun.hassalarm.databinding.ContentEditConnectionBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

private const val REQUEST_CODE_TEST_CONNECTION = 12
private const val KEY_LAST_SUCCESSFUL = "last_run_successful"

class EditConnectionActivity : AppCompatActivity() {
    private var lastRunWasSuccessful: Boolean? = null
    private lateinit var binding: ContentEditConnectionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityEditConnectionBinding.inflate(
            layoutInflater
        )
        setContentView(binding.root)
        setupInsets(binding.root)
        setSupportActionBar(binding.toolbar)
        this.binding = binding.content
        if (savedInstanceState != null) {
            lastRunWasSuccessful = savedInstanceState.getBoolean(KEY_LAST_SUCCESSFUL, false)
        }

        // Set current saved host and api key.
        val sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        with(binding.content) {
            testButton.setOnClickListener {
                val host = hostInput.text.toString().trim()
                val token = apiKeyInput.text.toString().trim()
                val entityId = entityIdInput.text.toString().trim()
                val entityIdIsLegacy = isEntityLegacy.isChecked
                lastRunWasSuccessful = null
                startActivityForResult(
                    TestConnectionActivity.createIntent(
                        this@EditConnectionActivity,
                        host,
                        token,
                        entityId,
                        accessType,
                        entityIdIsLegacy
                    ),
                    REQUEST_CODE_TEST_CONNECTION
                )
            }

            hostInput.setText(sharedPreferences.getString(KEY_PREFS_HOST, ""))
            apiKeyInput.setText(sharedPreferences.getString(KEY_PREFS_API_KEY, ""))

            keyType.setOnCheckedChangeListener { _, checkedId: Int ->
                isEntityLegacy.isEnabled = checkedId != keyIsWebhook.id

                apiKey.hint = when (checkedId) {
                    keyIsToken.id -> keyIsToken.text
                    keyIsWebhook.id -> keyIsWebhook.text
                    keyIsLegacy.id -> keyIsLegacy.text
                    else -> ""
                }
            }
        }

        val activeRadioButton: Int
        val accessType = Migration.getAccessType(sharedPreferences)
        activeRadioButton = when (accessType) {
            AccessType.LONG_LIVED_TOKEN -> binding.content.keyIsToken.id
            AccessType.WEB_HOOK -> binding.content.keyIsWebhook.id
            else -> binding.content.keyIsLegacy.id
        }
        binding.content.keyType.check(activeRadioButton)

        // Migration of old versions to new versions
        with(binding.content) {
            entityIdInput.setText(Migration.getEntityId(sharedPreferences))
            isEntityLegacy.isChecked = Migration.entityIdIsLegacy(sharedPreferences)
            save.setOnClickListener {
                val type: AccessType = if (keyIsToken.isChecked) {
                    AccessType.LONG_LIVED_TOKEN
                } else if (keyIsWebhook.isChecked) {
                    AccessType.WEB_HOOK
                } else {
                    AccessType.LEGACY_API_KEY
                }
                sharedPreferences
                    .edit()
                    .putString(KEY_PREFS_HOST, hostInput.text.toString().trim())
                    .putString(KEY_PREFS_API_KEY, apiKeyInput.text.toString().trim())
                    .putString(KEY_PREFS_ENTITY_ID, entityIdInput.text.toString().trim())
                    .putString(KEY_PREFS_ACCESS_TYPE, type.name)
                    .putBoolean(KEY_PREFS_IS_ENTITY_ID_LEGACY, isEntityLegacy.isChecked)
                    .putLong(LAST_PUBLISHED_TRIGGER_TIMESTAMP, 0)
                    .apply()
                Toast.makeText(
                    this@EditConnectionActivity,
                    R.string.toast_saved,
                    Toast.LENGTH_SHORT
                ).show()
                lastRunWasSuccessful?.let {
                    // On change, reset trigger time.
                    NextAlarmUpdaterJob.markAsDone(this@EditConnectionActivity, it, 0)
                }
                finish()
            }
        }

    }

    override fun onBackPressed() {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if ((sharedPreferences
                .getString(
                    KEY_PREFS_HOST,
                    ""
                ) != binding.hostInput.text.toString().trim()
                    || sharedPreferences
                .getString(
                    KEY_PREFS_API_KEY,
                    ""
                ) != binding.apiKeyInput.text.toString().trim()
                    || Migration.getEntityId(sharedPreferences) != binding.entityIdInput.text.toString()
                .trim()) || Migration.entityIdIsLegacy(sharedPreferences) != binding.isEntityLegacy.isChecked || Migration.getAccessType(
                sharedPreferences
            ) != accessType
        ) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.unsaved_changes_title)
                .setMessage(R.string.unsaved_changes_message)
                .setPositiveButton(R.string.unsaved_changes_discard) { _, _ -> finish() }
                .setNeutralButton(R.string.unsaved_changes_cancel, null)
                .show()
        } else {
            super.onBackPressed()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        lastRunWasSuccessful?.let {
            outState.putBoolean(KEY_LAST_SUCCESSFUL, it)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_about) {
            startActivity(AboutActivity.createIntent(this))
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_TEST_CONNECTION) {
            lastRunWasSuccessful = getLastRunWasSuccessful(data)
        }
    }

    private fun getLastRunWasSuccessful(data: Intent?): Boolean? {
        if (data == null) {
            return null
        }
        val extras = data.extras
        return if (extras != null && extras.containsKey(KEY_LAST_SUCCESSFUL)) data.getBooleanExtra(
            KEY_LAST_SUCCESSFUL, false
        ) else null
    }

    private val accessType: AccessType
        get() {
            if (binding.keyIsWebhook.isChecked) {
                return AccessType.WEB_HOOK
            }
            return if (binding.keyIsLegacy.isChecked) {
                AccessType.LEGACY_API_KEY
            } else AccessType.LONG_LIVED_TOKEN
        }

    companion object {
        fun createIntent(context: Context?): Intent =
            Intent(context, EditConnectionActivity::class.java)
    }
}