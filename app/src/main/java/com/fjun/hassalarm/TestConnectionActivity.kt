package com.fjun.hassalarm

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.AsyncTask
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.ContextMenu
import android.view.ContextMenu.ContextMenuInfo
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import com.fjun.hassalarm.NextAlarmUpdaterJob.Companion.createRequest
import com.fjun.hassalarm.databinding.ActivityTestConnectionBinding
import com.fjun.hassalarm.history.AppDatabase.Companion.getDatabase
import com.fjun.hassalarm.history.Publish
import com.fjun.hassalarm.history.PublishDao
import java.io.IOException
import okhttp3.Request
import okhttp3.ResponseBody
import okio.Buffer
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

private const val KEY_LAST_SUCCESSFUL = "last_run_successful"
private const val EXTRA_HOST = "host"
private const val EXTRA_TOKEN = "token"
private const val EXTRA_ENTITY_ID = "entity_id"
private const val EXTRA_ACCESS_TYPE = "access_type"
private const val EXTRA_ENTITY_ID_IS_LEGACY = "entity_id_is_legacy"

class TestConnectionActivity : AppCompatActivity() {
    private lateinit var host: String
    private lateinit var token: String
    private lateinit var entityId: String
    private var strippedLog: String? = null
    private var accessType: AccessType? = null
    private var entityIdIsLegacy: Boolean = false
    private var updateRequest: UpdateRequest? = null
    private var lastRunWasSuccessful: Boolean? = null
    private lateinit var binding: ActivityTestConnectionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTestConnectionBinding.inflate(
            layoutInflater
        )
        setContentView(binding.root)
        setupInsets(binding.root)
        if (savedInstanceState != null) {
            lastRunWasSuccessful = savedInstanceState.getBoolean(KEY_LAST_SUCCESSFUL, false)
        }
        binding.close.setOnClickListener { finish() }
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
        binding.log.movementMethod = ScrollingMovementMethod()
        binding.log.setOnLongClickListener { obj: View -> obj.showContextMenu() }
        runTest()
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo)
        if (v.id == R.id.log) {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            menu.add(R.string.copy_full_log)
                .setOnMenuItemClickListener {
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText(
                            "Hassalarm full connection log", binding.log.text.toString()
                        )
                    )
                    true
                }
            if (!strippedLog.isNullOrEmpty()) {
                menu.add(R.string.copy_anonymous_log)
                    .setOnMenuItemClickListener {
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText(
                                "Hassalarm anonymous connection log",
                                strippedLog
                            )
                        )
                        true
                    }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        updateRequest?.call?.cancel()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        lastRunWasSuccessful?.let {
            outState.putBoolean(KEY_LAST_SUCCESSFUL, it)
        }
        outState.putString(EXTRA_HOST, host)
        outState.putString(EXTRA_TOKEN, token)
        outState.putString(EXTRA_ENTITY_ID, entityId)
        outState.putSerializable(EXTRA_ACCESS_TYPE, accessType)
        outState.putBoolean(EXTRA_ENTITY_ID_IS_LEGACY, entityIdIsLegacy)
    }

    private fun runTest() {
        binding.progress.visibility = View.VISIBLE
        binding.statusDrawable.visibility = View.GONE
        binding.status.setText(R.string.status_running)
        binding.log.text = ""
        strippedLog = ""

        binding.log.append(
            when (accessType) {
                AccessType.LONG_LIVED_TOKEN -> getString(R.string.log_using_token)
                AccessType.WEB_HOOK -> getString(R.string.log_using_webhook)
                else -> getString(R.string.log_using_api_key)
            } + '\n'
        )

        if (accessType !== AccessType.WEB_HOOK) {
            binding.log.append(
                if (entityIdIsLegacy) {
                    getString(R.string.log_entity_id_is_legacy_sensor)
                } else {
                    getString(R.string.log_entity_id_is_input_datetime)
                } + '\n'
            )
        }
        try {
            updateRequest = createRequest(
                this, host, token, entityId, accessType, entityIdIsLegacy, HashSet()
            )
            val call = updateRequest!!.call
            val triggerTimestamp = updateRequest!!.triggerTimestamp
            val creatorPackage = updateRequest!!.creatorPackage
            binding.log.append(
                getString(
                    R.string.using_url,
                    call.request().method(),
                    call.request().url().toString()
                ) + '\n'
            )
            binding.log.append(
                getString(
                    R.string.headers,
                    call.request().headers().toString()
                ) + '\n'
            )
            binding.log.append(getString(R.string.body, requestBodyToString(call.request())) + '\n')
            call.enqueue(
                object : Callback<ResponseBody> {
                    override fun onResponse(
                        call: Call<ResponseBody>,
                        response: Response<ResponseBody>
                    ) {
                        var wasSuccessful = false
                        var message: String?
                        try {
                            response.body().use { body ->
                                if (body != null) {
                                    message = body.string()
                                    binding.log.append(
                                        getString(
                                            R.string.connection_ok,
                                            message
                                        ) + '\n'
                                    )
                                    wasSuccessful = true
                                } else if (response.errorBody() != null) {
                                    message = response.errorBody()?.string().orEmpty()
                                    binding.log.append(
                                        getString(
                                            R.string.connection_failure,
                                            message
                                        ) + '\n'
                                    )
                                } else {
                                    message = response.code().toString()
                                    binding.log.append(
                                        getString(
                                            R.string.connection_failure_code,
                                            message
                                        ) + '\n'
                                    )
                                }
                            }
                        } catch (e: IOException) {
                            message = e.message
                            binding.log.append(
                                getString(
                                    R.string.connection_failure,
                                    message
                                ) + '\n'
                            )
                        }
                        insertPublish(
                            Publish(
                                System.currentTimeMillis(),
                                wasSuccessful,
                                triggerTimestamp,
                                message,
                                creatorPackage
                            )
                        )
                        markAsDone(wasSuccessful)
                    }

                    override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                        val message = t.message
                        binding.log.append(getString(R.string.connection_failure, message) + '\n')
                        insertPublish(
                            Publish(
                                System.currentTimeMillis(),
                                false,
                                triggerTimestamp,
                                message,
                                creatorPackage
                            )
                        )
                        markAsDone(false)
                    }
                })
        } catch (e: IllegalArgumentException) {
            val message = e.message
            binding.log.append(message + '\n')
            insertPublish(Publish(System.currentTimeMillis(), false, 0L, message, null))
            markAsDone(false)
        }
        strippedLog =
            binding.log.text.toString().replace(host, "<host>").replace(token, "<token>")
    }

    private fun markAsDone(successful: Boolean) {
        binding.progress.visibility = View.GONE
        binding.statusDrawable.visibility = View.VISIBLE
        if (successful) {
            binding.status.setText(R.string.status_done_ok)
            binding.statusDrawable.setImageDrawable(
                AppCompatResources.getDrawable(
                    this,
                    R.drawable.ic_check_green_24dp
                )
            )
        } else {
            binding.status.setText(R.string.status_done_failed)
            binding.statusDrawable.setImageDrawable(
                AppCompatResources.getDrawable(
                    this,
                    R.drawable.ic_error_outline_red_24dp
                )
            )
        }
        lastRunWasSuccessful = successful
        val intent = Intent()
        intent.putExtra(KEY_LAST_SUCCESSFUL, lastRunWasSuccessful)
        setResult(RESULT_OK, intent)
        registerForContextMenu(binding.log)
    }

    private fun database(): PublishDao {
        return getDatabase(applicationContext).publishDao()
    }

    private fun insertPublish(publish: Publish) {
        AsyncTask.execute { database().insertAll(publish) }
    }

    private fun requestBodyToString(request: Request): String {
        return try {
            val buffer = Buffer()
            request.body()?.writeTo(buffer)
            buffer.readUtf8()
        } catch (e: IOException) {
            "Unable to get request body"
        }
    }

    companion object {
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