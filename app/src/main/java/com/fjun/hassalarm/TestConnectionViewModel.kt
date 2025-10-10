package com.fjun.hassalarm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.fjun.hassalarm.history.AppDatabase
import com.fjun.hassalarm.history.Publish
import com.fjun.hassalarm.history.PublishDao
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import okhttp3.ResponseBody
import retrofit2.Response

class TestConnectionViewModel(application: Application) : AndroidViewModel(application) {

    val log = MutableStateFlow("")
    val status = MutableStateFlow("")
    val isRunning = MutableStateFlow(false)
    val isSuccessful = MutableStateFlow<Boolean?>(null)

    private var updateRequest: UpdateRequest? = null

    fun runTest(
        host: String,
        token: String,
        entityId: String,
        accessType: AccessType?,
        entityIdIsLegacy: Boolean
    ) {
        log.value = ""
        status.value = getApplication<Application>().getString(R.string.status_running)
        isRunning.value = true
        isSuccessful.value = null

        log.value += when (accessType) {
            AccessType.LONG_LIVED_TOKEN -> getApplication<Application>().getString(R.string.log_using_token)
            AccessType.WEB_HOOK -> getApplication<Application>().getString(R.string.log_using_webhook)
            else -> getApplication<Application>().getString(R.string.log_using_api_key)
        } + '\n'

        if (accessType !== AccessType.WEB_HOOK) {
            log.value += if (entityIdIsLegacy) {
                getApplication<Application>().getString(R.string.log_entity_id_is_legacy_sensor)
            } else {
                getApplication<Application>().getString(R.string.log_entity_id_is_input_datetime)
            } + '\n'
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                updateRequest = NextAlarmUpdaterJob.createRequest(
                    getApplication(), host, token, entityId, accessType, entityIdIsLegacy, HashSet()
                )
                val call = updateRequest!!.call
                val triggerTimestamp = updateRequest!!.triggerTimestamp
                val creatorPackage = updateRequest!!.creatorPackage
                log.value += getApplication<Application>().getString(
                    R.string.using_url,
                    call.request().method(),
                    call.request().url().toString()
                ) + '\n'
                log.value += getApplication<Application>().getString(
                    R.string.headers,
                    call.request().headers().toString()
                ) + '\n'
                log.value += getApplication<Application>().getString(
                    R.string.body,
                    requestBodyToString(call.request())
                ) + '\n'

                val response: Response<ResponseBody> = try {
                    call.execute()
                } catch (e: IOException) {
                    val message = e.message
                    log.value += getApplication<Application>().getString(
                        R.string.connection_failure,
                        message
                    ) + '\n'
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
                    return@launch
                }

                var wasSuccessful = false
                var message: String?
                try {
                    response.body().use { body ->
                        if (body != null) {
                            message = body.string()
                            log.value += getApplication<Application>().getString(
                                R.string.connection_ok,
                                message
                            ) + '\n'
                            wasSuccessful = true
                        } else if (response.errorBody() != null) {
                            message = response.errorBody()?.string().orEmpty()
                            log.value += getApplication<Application>().getString(
                                R.string.connection_failure,
                                message
                            ) + '\n'
                        } else {
                            message = response.code().toString()
                            log.value += getApplication<Application>().getString(
                                R.string.connection_failure_code,
                                message
                            ) + '\n'
                        }
                    }
                } catch (e: IOException) {
                    message = e.message
                    log.value += getApplication<Application>().getString(
                        R.string.connection_failure,
                        message
                    ) + '\n'
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

            } catch (e: IllegalArgumentException) {
                val message = e.message
                log.value += message + '\n'
                insertPublish(Publish(System.currentTimeMillis(), false, 0L, message, null))
                markAsDone(false)
            }
        }
    }

    private fun markAsDone(successful: Boolean) {
        isRunning.value = false
        isSuccessful.value = successful
        status.value = if (successful) {
            getApplication<Application>().getString(R.string.status_done_ok)
        } else {
            getApplication<Application>().getString(R.string.status_done_failed)
        }
    }

    private fun database(): PublishDao {
        return AppDatabase.getDatabase(getApplication()).publishDao()
    }

    private suspend fun insertPublish(publish: Publish) {
        database().insertAll(publish)
    }

    private fun requestBodyToString(request: okhttp3.Request): String {
        return try {
            val buffer = okio.Buffer()
            request.body()?.writeTo(buffer)
            buffer.readUtf8()
        } catch (e: IOException) {
            "Unable to get request body: ${e.message}"
        }
    }

    override fun onCleared() {
        super.onCleared()
        updateRequest?.call?.cancel()
    }
}