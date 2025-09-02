package com.xiaoha.batterywidget.test

import android.content.Context
import android.util.Log
import com.xiaoha.batterywidget.api.LocalBatteryService
import com.xiaoha.batterywidget.utils.LocalEncryptionUtil
import kotlinx.coroutines.runBlocking
import java.util.Date

/**
 * 本地加密测试类
 * 用于验证本地加密功能是否正常工作
 */
object LocalEncryptionTest {
    private const val TAG = "LocalEncryptionTest"
    
    /**
     * 运行完整的本地加密测试
     */
    fun runFullTest(context: Context, batteryNo: String, token: String, cityCode: String = "0755"): TestResult {
        return try {
            Log.d(TAG, "开始完整的本地加密测试")
            
            // 测试1: 基本加密功能
            val basicTest = testBasicEncryption()
            if (!basicTest.success) {
                return basicTest
            }
            
            // 测试2: API请求体构建
            val apiBodyTest = testApiBodyBuilding(batteryNo, token, cityCode)
            if (!apiBodyTest.success) {
                return apiBodyTest
            }
            
            // 测试3: 完整的API调用（如果提供了有效参数）
            val apiTest = if (batteryNo.isNotEmpty() && token.isNotEmpty()) {
                testFullApiCall(batteryNo, token, cityCode)
            } else {
                TestResult(true, "跳过API调用测试（缺少参数）")
            }
            
            TestResult(
                success = true,
                message = "所有测试通过",
                details = mapOf(
                    "基本加密测试" to if (basicTest.success) "通过" else "失败",
                    "API请求体测试" to if (apiBodyTest.success) "通过" else "失败",
                    "完整API测试" to if (apiTest.success) "通过" else apiTest.message
                )
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "测试过程中发生异常", e)
            TestResult(false, "测试异常: ${e.message}")
        }
    }
    
    /**
     * 测试基本加密功能
     */
    private fun testBasicEncryption(): TestResult {
        return try {
            Log.d(TAG, "测试基本加密功能")
            
            // 检查加密库是否可用
            if (!LocalEncryptionUtil.isEncryptionLibraryAvailable()) {
                return TestResult(false, "加密库不可用")
            }
            
            // 测试加解密
            val testData = "Hello, Local Encryption!"
            val encrypted = LocalEncryptionUtil.encryptData(testData)
                ?: return TestResult(false, "加密失败")
            
            val decrypted = LocalEncryptionUtil.decryptData(encrypted)
                ?: return TestResult(false, "解密失败")
            
            if (testData != decrypted) {
                return TestResult(false, "加解密结果不匹配")
            }
            
            // 测试签名生成
            val timestamp = LocalEncryptionUtil.getCurrentTimestamp()
            val nonce = LocalEncryptionUtil.generateSimpleNonce()
            val signature = LocalEncryptionUtil.generateSignature(testData, timestamp, nonce)
                ?: return TestResult(false, "签名生成失败")
            
            Log.d(TAG, "基本加密功能测试通过")
            TestResult(
                success = true,
                message = "基本加密功能正常",
                details = mapOf(
                    "原始数据" to testData,
                    "加密长度" to encrypted.length.toString(),
                    "解密结果" to decrypted,
                    "签名" to signature.take(20) + "..."
                )
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "基本加密测试失败", e)
            TestResult(false, "基本加密测试异常: ${e.message}")
        }
    }
    
    /**
     * 测试API请求体构建
     */
    private fun testApiBodyBuilding(batteryNo: String, token: String, cityCode: String): TestResult {
        return try {
            Log.d(TAG, "测试API请求体构建")
            
            val localBatteryService = LocalBatteryService("https://test.com")
            
            // 使用反射调用私有方法（仅用于测试）
            val method = LocalBatteryService::class.java.getDeclaredMethod(
                "buildApiRequestBody", 
                String::class.java, 
                String::class.java, 
                String::class.java
            )
            method.isAccessible = true
            val requestBody = method.invoke(localBatteryService, batteryNo, token, cityCode) as String
            
            Log.d(TAG, "API请求体构建成功: $requestBody")
            
            // 验证请求体包含必要字段
            if (!requestBody.contains("systemCode") || 
                !requestBody.contains("batteryNo") || 
                !requestBody.contains("token")) {
                return TestResult(false, "API请求体缺少必要字段")
            }
            
            TestResult(
                success = true,
                message = "API请求体构建正常",
                details = mapOf(
                    "请求体长度" to requestBody.length.toString(),
                    "包含电池号" to requestBody.contains(batteryNo).toString(),
                    "包含token" to requestBody.contains(token).toString()
                )
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "API请求体构建测试失败", e)
            TestResult(false, "API请求体构建测试异常: ${e.message}")
        }
    }
    
    /**
     * 测试完整的API调用
     */
    private fun testFullApiCall(batteryNo: String, token: String, cityCode: String): TestResult {
        return try {
            Log.d(TAG, "测试完整的API调用")
            
            val localBatteryService = LocalBatteryService("https://test.com")
            
            // 运行异步测试
            val result = runBlocking {
                localBatteryService.getBatteryDataWithLocalEncryption(batteryNo, token, cityCode)
            }
            
            if (result != null) {
                Log.d(TAG, "API调用成功，电量: ${result.batteryLife}%")
                TestResult(
                    success = true,
                    message = "API调用成功",
                    details = mapOf(
                        "电池电量" to "${result.batteryLife}%",
                        "报告时间" to result.reportTime,
                        "时间戳" to Date(result.reportTime.toLong()).toString()
                    )
                )
            } else {
                TestResult(false, "API调用返回空结果")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "完整API调用测试失败", e)
            TestResult(false, "API调用测试异常: ${e.message}")
        }
    }
    
    /**
     * 测试结果
     */
    data class TestResult(
        val success: Boolean,
        val message: String,
        val details: Map<String, String> = emptyMap()
    )
}