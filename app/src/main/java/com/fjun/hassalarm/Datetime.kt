package com.fjun.hassalarm

/**
 * Represent a input.set_datetime body for the hass.io service API
 */
class Datetime(
    val entity_id: String?,
    val timestamp: Long, // UTC unix timestamp. (in seconds)
)