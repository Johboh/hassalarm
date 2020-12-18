package com.fjun.hassalarm;
/**
 * Represent a input.set_datetime body for the hass.io service API
 */
class Datetime {
    final String entity_id;
    final long timestamp; // UTC unix timestamp. (in seconds)

    Datetime(String entityId, long timestamp) {
        this.entity_id = entityId;
        this.timestamp = timestamp;
    }
}
