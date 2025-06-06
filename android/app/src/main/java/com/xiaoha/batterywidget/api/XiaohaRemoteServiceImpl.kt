package com.xiaoha.batterywidget.api

import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// Retrofit接口
interface ProxyBatteryService {
    @GET("/")
    fun getBatteryInfo(
        @Query("batteryNo") batteryNo: String,
        @Query("cityCode") cityCode: String,
        @Query("format") format: String = "json"
    ): Call<BatteryResponse>
}

// 远程实现
class XiaohaRemoteServiceImpl(private val retrofit: Retrofit) : BatteryService {
    private val api = retrofit.create(ProxyBatteryService::class.java)

    override fun getBatteryInfo(batteryNo: String, cityCode: String): BatteryResponse? {
        val call = api.getBatteryInfo(batteryNo, cityCode)
        val response = call.execute()
        return if (response.isSuccessful) response.body() else null
    }
}