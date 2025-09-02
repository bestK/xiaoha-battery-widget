package com.xiaoha.batterywidget.service

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.xiaoha.batterywidget.api.BatteryInfo
import com.xiaoha.batterywidget.utils.LocalEncryptionUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 本地电池服务，使用本地加密库处理电池数据
 */
class LocalBatteryService(private val baseUrl: String) {
    companion object {
        private const val TAG = "LocalBatteryService"
        private const val TIMEOUT_SECONDS = 30L
    }
    
    private val gson = Gson()
    private val okHttpClient: OkHttpClient
    
    init {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        okHttpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }
    
    /**
     * 获取电池数据（使用本地加密）
     * @param batteryNo 电池编号
     * @param token 用户token
     * @param cityCode 城市代码，默认为0755
     * @return 电池信息，失败返回null
     */
    suspend fun getBatteryDataWithLocalEncryption(
        batteryNo: String, 
        token: String,
        cityCode: String = "0755"
    ): BatteryInfo? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始获取电池数据 - 电池编号: $batteryNo, 城市代码: $cityCode")
            
            // 检查加密库是否可用
            if (!LocalEncryptionUtil.isEncryptionLibraryAvailable()) {
                throw Exception("本地加密库不可用")
            }

            // 提取 token
            val decryptTokenJson = LocalEncryptionUtil.decryptData(token)
            Log.d(TAG, "decryptToken: $decryptTokenJson")

            val tokenFromJson = try {
                val jsonObject = gson.fromJson(decryptTokenJson, com.google.gson.JsonObject::class.java)
                jsonObject.get("token")?.asString ?: token
            } catch (e: Exception) {
                Log.e(TAG, "解析token JSON失败: ${e.message}")
                token
            }


            Log.d(TAG, "token: $tokenFromJson")
            
            // 步骤1: 构建API请求数据
            val requestBody = buildApiRequestBody(batteryNo, tokenFromJson, cityCode)
            Log.d(TAG, "构建API请求体: $requestBody")
            
            // 步骤2: 使用本地加密库加密请求数据
            val encryptedData = LocalEncryptionUtil.encryptData(requestBody)
                ?: throw Exception("加密请求数据失败")
            
            Log.d(TAG, "数据加密成功，长度: ${encryptedData.length}")
            
            // 步骤3: 生成签名和其他请求参数
            val timestamp = System.currentTimeMillis().toString()
            val nonce = LocalEncryptionUtil.generateSimpleNonce()
            val signature = LocalEncryptionUtil.generateSignature(encryptedData, timestamp, nonce)
                ?: throw Exception("生成签名失败")
            
            Log.d(TAG, "签名生成成功: $signature")
            
            // 步骤4: 发送请求到真实API
            val encryptedResponse = sendDirectApiRequest(encryptedData, signature, timestamp, nonce)
            Log.d(TAG, "收到加密响应，长度: ${encryptedResponse.length}")
            
            // 步骤5: 使用本地加密库解密响应
            val decryptedResponse = LocalEncryptionUtil.decryptData(encryptedResponse)
                ?: throw Exception("解密响应数据失败")
            
            Log.d(TAG, "响应解密成功，长度: ${decryptedResponse.length}")
            Log.d(TAG, "响应解密成功，数据: $decryptedResponse")

            // 步骤6: 解析解密后的JSON数据
            val apiResponse = try {
                gson.fromJson(decryptedResponse, ApiResponse::class.java)
            } catch (e: JsonSyntaxException) {
                Log.e(TAG, "JSON解析失败: ${e.message}")
                Log.d(TAG, "原始响应: $decryptedResponse")
                throw Exception("响应数据格式错误")
            }
            
            // 步骤7: 验证并返回电池信息
            if (apiResponse?.code == 0 && apiResponse.data?.bindBatteries?.isNotEmpty() == true) {
                val batteryData = apiResponse.data.bindBatteries[0]
                
                // 检查电池数据是否成功
//                if (batteryData.reasonCode != 0) {
//                    throw Exception("电池数据获取失败: ${batteryData.reasonCodeText}")
//                }
                
                val batteryInfo = BatteryInfo(
                    batteryLife = batteryData.batteryLife,
                    reportTime = batteryData.reportTime.toString()
                )
                Log.d(TAG, "获取电池数据成功 - 电量: ${batteryInfo.batteryLife}%, 时间: ${batteryData.reportTime}")
                batteryInfo
            } else {
                val errorMsg = if (apiResponse?.code != 0) {
                    "API返回错误码: ${apiResponse?.code}"
                } else {
                    "响应中没有电池数据"
                }
                throw Exception(errorMsg)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "获取电池数据失败", e)
            null
        }
    }
    
    /**
     * 构建API请求体（按照真实API格式）
     */
    private fun buildApiRequestBody(batteryNo: String, token: String, cityCode: String): String {
        val requestBody = mapOf(
            "systemCode" to "A4",
            "version" to "6.37.0",
            "appVersion" to "8.0.57",
            "action" to "switchpower.user.getBatteryLifeInfo",
            "waitBatteryLifeCmdResultFlag" to true,
            "cityCode" to cityCode,
            "batteries" to listOf(mapOf("batteryNo" to batteryNo)),
            "token" to token,
            "chaosChangeBattery" to true
        )
        return gson.toJson(requestBody)
    }
    
    /**
     * 发送请求到真实API
     */
    private suspend fun sendDirectApiRequest(
        encryptedData: String, 
        signature: String, 
        timestamp: String, 
        nonce: String
    ): String = withContext(Dispatchers.IO) {
        val apiUrl = "https://switchpowerapi.hellobike.com/api?switchpower.user.getBatteryLifeInfo"
        val requestBody = encryptedData.toRequestBody("application/json".toMediaType())
        
        val request = Request.Builder()
            .url(apiUrl)
            .post(requestBody)
            .addHeader("chaos", "true")
            .addHeader("x-chaos-env", "pro-1.1.3-beta.1")
            .addHeader("content-type", "application/json")
            .addHeader("signature", signature)
            .addHeader("timestamp", timestamp)
            .addHeader("systemCode", "A4")
            .addHeader("nonce", nonce)
            .addHeader("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)")
            .addHeader("Referer", "https://servicewechat.com/wxc99bea6e25f8ab86/111/page-frame.html")
            .build()
        
        val response = okHttpClient.newCall(request).execute()
        
        if (!response.isSuccessful) {
            throw IOException("API请求失败: ${response.code} ${response.message}")
        }
        
        response.body?.string() ?: throw IOException("响应体为空")
    }
    
    /**
     * 测试本地加密功能
     */
    suspend fun testLocalEncryption(): TestResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始测试本地加密功能")
            
            // 测试1: 检查加密库可用性
            if (!LocalEncryptionUtil.isEncryptionLibraryAvailable()) {
                return@withContext TestResult(false, "本地加密库不可用")
            }
            
            // 测试2: 测试基本加解密
            val testData = "Hello, Battery Widget!"
            val encrypted = LocalEncryptionUtil.encryptData(testData)
                ?: return@withContext TestResult(false, "加密测试失败")
            
            val decrypted = LocalEncryptionUtil.decryptData(encrypted)
                ?: return@withContext TestResult(false, "解密测试失败")
            
            if (testData != decrypted) {
                return@withContext TestResult(false, "加解密结果不匹配")
            }
            
            // 测试3: 测试签名生成
            val timestamp = LocalEncryptionUtil.getCurrentTimestamp()
            val nonce = LocalEncryptionUtil.generateNonce()
            val signature = LocalEncryptionUtil.generateSignature(testData, timestamp, nonce)
                ?: return@withContext TestResult(false, "签名生成失败")
            
            // 测试4: 测试MD5哈希
            val hash = LocalEncryptionUtil.generateMD5Hash(testData)
                ?: return@withContext TestResult(false, "MD5哈希生成失败")
            
            Log.d(TAG, "本地加密功能测试通过")
            TestResult(
                success = true,
                message = "所有测试通过",
                details = mapOf(
                    "原始数据" to testData,
                    "加密数据长度" to encrypted.length.toString(),
                    "解密数据" to decrypted,
                    "时间戳" to timestamp,
                    "随机数" to nonce,
                    "签名" to signature,
                    "MD5哈希" to hash
                )
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "本地加密测试失败", e)
            TestResult(false, "测试异常: ${e.message}")
        }
    }
    
    /**
     * API响应数据类
     */
    data class ApiResponse(
        val code: Int,
        val data: ApiResponseData?
    )
    
    data class ApiResponseData(
        val bindBatteries: List<ApiBatteryData>?
    )
    
    data class ApiBatteryData(
        val batteryNo: String,
        val batteryLife: Int,
        val level: Int,
        val reportTime: Long,
        val reasonCode: Int,
        val reasonCodeText: String
    )
    
    /**
     * 测试结果
     */
    data class TestResult(
        val success: Boolean,
        val message: String,
        val details: Map<String, String> = emptyMap()
    )
}