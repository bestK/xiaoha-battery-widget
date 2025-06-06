package com.xiaoha.batterywidget.api

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface BatteryService {
   
    fun getBatteryInfo(batteryNo: String, cityCode: String, token: String): BatteryResponse?
} 