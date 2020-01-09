package com.fjun.hassalarm;
/**
 * Represent a input.set_datetime body for the hass.io service API
 */
class Datetime {
    final String entity_id;
    final String datetime; // Hass format: %Y-%m-%d %H:%M:%S

    Datetime(String entityId, String datetime) {
        this.entity_id = entityId;
        this.datetime = datetime;
    }
}
