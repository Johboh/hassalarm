package com.fjun.hassalarm;

import android.content.SharedPreferences;
import android.text.TextUtils;

import static com.fjun.hassalarm.Constants.DEFAULT_ENTITY_ID;
import static com.fjun.hassalarm.Constants.DEFAULT_LEGACY_ENTITY_ID;
import static com.fjun.hassalarm.Constants.KEY_PREFS_ACCESS_TYPE;
import static com.fjun.hassalarm.Constants.KEY_PREFS_ENTITY_ID;
import static com.fjun.hassalarm.Constants.KEY_PREFS_HOST;
import static com.fjun.hassalarm.Constants.KEY_PREFS_IS_ENTITY_ID_LEGACY;
import static com.fjun.hassalarm.Constants.KEY_PREFS_IS_TOKEN;

/**
 * Helper class for migration logic.
 */
class Migration {

    /**
     * Migration logic for if entity ID is a legacy entity ID or an input one.
     */
    static boolean entityIdIsLegacy(SharedPreferences sharedPreferences) {
        // Migration logic from sensor as default to using input_datetime as default.
        // If we have anything else saved, but not the new input setting, default to
        // using legacy sensor.
        if (sharedPreferences.contains(KEY_PREFS_HOST) && !sharedPreferences.contains(KEY_PREFS_IS_ENTITY_ID_LEGACY)) {
            return true;
        } else {
            return sharedPreferences.getBoolean(KEY_PREFS_IS_ENTITY_ID_LEGACY, false);
        }
    }

    /**
     * Migration logic for default entity id.
     */
    static String getEntityId(SharedPreferences sharedPreferences) {
        String entityId = sharedPreferences.getString(KEY_PREFS_ENTITY_ID, null);
        if (entityId == null) {
            // Nothing saved at any point, not even "" or DEFAULT_LEGACY_ENTITY_ID
            // Set to use the new default one.
            entityId = DEFAULT_ENTITY_ID;
        } else {
            // There is something else saved, use that. If an empty string was saved,
            // use the legacy default one.
            entityId = TextUtils.isEmpty(entityId) ? DEFAULT_LEGACY_ENTITY_ID : entityId;
        }
        return entityId;
    }

    /**
     * Migration logic for getting the access type.
     */
    static Constants.AccessType getAccessType(SharedPreferences sharedPreferences) {
        // If we have the access token prefs, use that one first.
        if (sharedPreferences.contains(KEY_PREFS_ACCESS_TYPE)) {
            try {
                return Constants.AccessType.valueOf(sharedPreferences.getString(KEY_PREFS_ACCESS_TYPE, Constants.AccessType.LONG_LIVED_TOKEN.name()));
            } catch (IllegalArgumentException e) {
                return Constants.AccessType.LONG_LIVED_TOKEN;
            }
        }

        // If token exists we should respect that setting and the access type
        // is either the long lived token or the legacy API key.
        if (sharedPreferences.contains(KEY_PREFS_IS_TOKEN)) {
            return sharedPreferences.getBoolean(KEY_PREFS_IS_TOKEN, true) ? Constants.AccessType.LONG_LIVED_TOKEN : Constants.AccessType.LEGACY_API_KEY;
        }

        // Fallback on default installations.
        return Constants.AccessType.LONG_LIVED_TOKEN;
    }
}
