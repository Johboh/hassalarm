package com.fjun.hassalarm

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * API for hass.io
 * Supports updating an entity with a new [State].
 * A API key or longed-lived token is required.
 */
interface HassApi {
    @POST("/api/states/{entity_id}")
    fun updateStateUsingApiKey(
        @Body state: State?,
        @Path("entity_id") entity_id: String?,
        @Header("x-ha-access") apiKey: String?
    ): Call<ResponseBody>

    @POST("/api/states/{entity_id}")
    fun updateStateUsingToken(
        @Body state: State,
        @Path("entity_id") entity_id: String?,
        @Header("Authorization") token: String?
    ): Call<ResponseBody>

    @POST("/api/services/input_datetime/set_datetime")
    fun setInputDatetimeUsingApiKey(
        @Body datetime: Datetime?,
        @Header("x-ha-access") apiKey: String?
    ): Call<ResponseBody>

    @POST("/api/services/input_datetime/set_datetime")
    fun setInputDatetimeUsingToken(
        @Body datetime: Datetime?,
        @Header("Authorization") token: String?
    ): Call<ResponseBody>

    @Headers("Content-type: application/json")
    @POST("/api/webhook/{webhook_id}")
    fun updateStateUsingWebhook(
        @Body datetime: Datetime?,
        @Path("webhook_id") webhook_id: String?
    ): Call<ResponseBody>
}