package com.fjun.hassalarm

import okhttp3.ResponseBody
import retrofit2.Call

data class UpdateRequest(
    @get:JvmName("triggerTimestamp")
    val triggerTimestamp: Long,
    @get:JvmName("call")
    val call: Call<ResponseBody>,
    @get:JvmName("creatorPackage")
    val creatorPackage: String? = null,
)