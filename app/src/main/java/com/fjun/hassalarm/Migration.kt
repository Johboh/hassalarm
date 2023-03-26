package com.fjun.hassalarm

import android.content.SharedPreferences

/**
 * Helper class for migration logic.
 */
internal object Migration {
    /**
     * Migration logic for if entity ID is a legacy entity ID or an input one.
     */
    fun entityIdIsLegacy(sharedPreferences: SharedPreferences): Boolean {
        // Migration logic from sensor as default to using input_datetime as default.
        // If we have anything else saved, but not the new input setting, default to
        // using legacy sensor.
        return if (sharedPreferences.contains(KEY_PREFS_HOST) &&
            !sharedPreferences.contains(KEY_PREFS_IS_ENTITY_ID_LEGACY)
        ) {
            true
        } else {
            sharedPreferences.getBoolean(KEY_PREFS_IS_ENTITY_ID_LEGACY, false)
        }
    }

    /**
     * Migration logic for default entity id.
     */
    fun getEntityId(sharedPreferences: SharedPreferences): String {
        val entityId = sharedPreferences.getString(KEY_PREFS_ENTITY_ID, null)
        return if (entityId.isNullOrEmpty()) {
            // Nothing saved at any point, not even "" or DEFAULT_LEGACY_ENTITY_ID
            // Set to use the new default one.
            DEFAULT_ENTITY_ID
        } else {
            // There is something else saved, use that. If an empty string was saved,
            // use the legacy default one.
            entityId
        }
    }

    /**
     * Migration logic for getting the access type.
     */
    fun getAccessType(sharedPreferences: SharedPreferences): AccessType {
        // If we have the access token prefs, use that one first.
        if (sharedPreferences.contains(KEY_PREFS_ACCESS_TYPE)) {
            return try {
                AccessType.valueOf(
                    sharedPreferences.getString(
                        KEY_PREFS_ACCESS_TYPE,
                        AccessType.LONG_LIVED_TOKEN.name
                    ) ?: AccessType.LONG_LIVED_TOKEN.name
                )
            } catch (e: IllegalArgumentException) {
                AccessType.LONG_LIVED_TOKEN
            }
        }

        // If token exists we should respect that setting and the access type
        // is either the long lived token or the legacy API key.
        return if (sharedPreferences.contains(KEY_PREFS_IS_TOKEN)) {
            if (sharedPreferences.getBoolean(KEY_PREFS_IS_TOKEN, true)) {
                AccessType.LONG_LIVED_TOKEN
            } else {
                AccessType.LEGACY_API_KEY
            }
        } else {
            // Fallback on default installations.
            AccessType.LONG_LIVED_TOKEN
        }
    }
}