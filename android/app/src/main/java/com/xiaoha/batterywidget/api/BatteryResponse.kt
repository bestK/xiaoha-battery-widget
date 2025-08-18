package com.xiaoha.batterywidget.api

// 预处理参数响应
data class PreparamsResponse(
    val data: PreparamsData
)

data class PreparamsData(
    val url: String,
    val body: String,
    val headers: Map<String, String>
)

// 解码响应
data class DecodeResponse(
    val data: DecodeData
)

data class DecodeData(
    val data: BatteryResponseData
)

data class BatteryResponseData(
    val bindBatteries: List<BatteryInfo>
)

data class BatteryInfo(
    val batteryLife: Int,
    val reportTime: String
) 