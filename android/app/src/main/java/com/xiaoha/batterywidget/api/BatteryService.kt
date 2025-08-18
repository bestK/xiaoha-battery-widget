package com.xiaoha.batterywidget.api

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

interface BatteryService {
    @POST("preparams")
    fun getPreparams(
        @Query("batteryNo") batteryNo: String,
        @Body token: RequestBody
    ): Call<PreparamsResponse>
    
    @POST
    fun getBatteryData(
        @Url url: String,
        @Body body: RequestBody
    ): Call<ResponseBody>
    
    @POST("decode")
    fun decodeBatteryData(
        @Body encryptedData: RequestBody
    ): Call<DecodeResponse>
} 