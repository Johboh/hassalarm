package com.fjun.hassalarm

import okhttp3.ResponseBody
import retrofit2.Call

data class UpdateRequest(
    val triggerTimestamp: Long,
    val call: Call<ResponseBody>,
    val creatorPackage: String? = null,
)