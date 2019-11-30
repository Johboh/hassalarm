package com.fjun.hassalarm;

/**
 * Constants shared across the app.
 */
interface Constants {
    String LOG_TAG = "hassalarm";
    String PREFS_NAME = "hassalarm";
    String KEY_PREFS_API_KEY = "api_key";
    String KEY_PREFS_HOST = "host";
    String KEY_PREFS_ENTITY_ID = "entity_id";
    String KEY_PREFS_IS_TOKEN = "is_token";
    String DEFAULT_ENTITY_ID = "sensor.next_alarm";
    int DEFAULT_PORT = 8123;
}
