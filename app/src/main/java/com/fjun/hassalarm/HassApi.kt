package com.fjun.hassalarm;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Path;

/**
 * API for hass.io
 * Supports updating an entity with a new {@link State}.
 * A API key or longed-lived token is required.
 */
public interface HassApi {
    @POST("/api/states/{entity_id}")
    Call<ResponseBody> updateStateUsingApiKey(@Body State state, @Path("entity_id") String entity_id, @Header("x-ha-access") String apiKey);

    @POST("/api/states/{entity_id}")
    Call<ResponseBody> updateStateUsingToken(@Body State state, @Path("entity_id") String entity_id, @Header("Authorization") String token);

    @POST("/api/services/input_datetime/set_datetime")
    Call<ResponseBody> setInputDatetimeUsingApiKey(@Body Datetime datetime, @Header("x-ha-access") String apiKey);

    @POST("/api/services/input_datetime/set_datetime")
    Call<ResponseBody> setInputDatetimeUsingToken(@Body Datetime datetime, @Header("Authorization") String token);

    @Headers("Content-type: application/json")
    @POST("/api/webhook/{webhook_id}")
    Call<ResponseBody> updateStateUsingWebhook(@Body Datetime datetime, @Path("webhook_id") String webhook_id);
}
