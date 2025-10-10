package com.fjun.hassalarm

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import com.fjun.hassalarm.Migration.entityIdIsLegacy
import com.fjun.hassalarm.Migration.getAccessType
import com.fjun.hassalarm.Migration.getEntityId
import kotlinx.coroutines.flow.MutableStateFlow

class EditConnectionViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPreferences: SharedPreferences =
        application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val host = MutableStateFlow("")
    val token = MutableStateFlow("")
    val entityId = MutableStateFlow("")
    val accessType = MutableStateFlow(AccessType.LONG_LIVED_TOKEN)
    val isEntityLegacy = MutableStateFlow(false)

    init {
        host.value = sharedPreferences.getString(KEY_PREFS_HOST, "") ?: ""
        token.value = sharedPreferences.getString(KEY_PREFS_API_KEY, "") ?: ""
        entityId.value = getEntityId(sharedPreferences)
        accessType.value = getAccessType(sharedPreferences)
        isEntityLegacy.value = entityIdIsLegacy(sharedPreferences)
    }

    fun save() {
        sharedPreferences
            .edit {
                putString(KEY_PREFS_HOST, host.value.trim())
                    .putString(KEY_PREFS_API_KEY, token.value.trim())
                    .putString(KEY_PREFS_ENTITY_ID, entityId.value.trim())
                    .putString(KEY_PREFS_ACCESS_TYPE, accessType.value.name)
                    .putBoolean(KEY_PREFS_IS_ENTITY_ID_LEGACY, isEntityLegacy.value)
                    .putLong(LAST_PUBLISHED_TRIGGER_TIMESTAMP, 0)
            }
    }

    fun isDirty(): Boolean {
        return sharedPreferences.getString(KEY_PREFS_HOST, "") != host.value.trim() ||
                sharedPreferences.getString(KEY_PREFS_API_KEY, "") != token.value.trim() ||
                getEntityId(sharedPreferences) != entityId.value.trim() ||
                entityIdIsLegacy(sharedPreferences) != isEntityLegacy.value ||
                getAccessType(sharedPreferences) != accessType.value
    }
}
