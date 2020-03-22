package com.fjun.hassalarm;

/**
 * Constants shared across the app.
 */
interface Constants {

    enum AccessType {
        LONG_LIVED_TOKEN,
        WEB_HOOK,
        // Deprecated.
        LEGACY_API_KEY
    }

    String LOG_TAG = "hassalarm";
    String PREFS_NAME = "hassalarm";
    String KEY_PREFS_API_KEY = "api_key";
    String KEY_PREFS_HOST = "host";
    String KEY_PREFS_ENTITY_ID = "entity_id";
    // Deprecated.
    String KEY_PREFS_IS_TOKEN = "is_token";
    String KEY_PREFS_ACCESS_TYPE = "access_type";
    // Deprecated.
    String KEY_PREFS_IS_ENTITY_ID_LEGACY = "is_entity_id_legacy";
    String DEFAULT_ENTITY_ID = "input_datetime.next_alarm";
    // Deprecated.
    String DEFAULT_LEGACY_ENTITY_ID = "sensor.next_alarm";
    String LAST_PUBLISH_ATTEMPT = "last_publish_attempt";
    String LAST_SUCCESSFUL_PUBLISH = "last_successful_publish";
    String LAST_PUBLISH_WAS_SUCCESSFUL = "last_publish_was_successful";
    String LAST_PUBLISHED_TRIGGER_TIMESTAMP = "last_published_trigger_timestamp";
    int DEFAULT_PORT = 8123;
}
