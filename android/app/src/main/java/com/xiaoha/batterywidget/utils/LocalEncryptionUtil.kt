package com.xiaoha.batterywidget.utils

import android.util.Log
import batteryencryption.Batteryencryption
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

/**
 * 本地加密工具类，使用原生Go库进行加解密操作
 */
object LocalEncryptionUtil {
    private const val TAG = "LocalEncryptionUtil"
    
    /**
     * 加密数据
     * @param plaintext 明文数据
     * @return 加密后的base64字符串，失败返回null
     */
    fun encryptData(plaintext: String): String? {
        return try {
            val result = Batteryencryption.encryptData(plaintext)
            if (result.isEmpty()) {
                Log.e(TAG, "加密失败：返回空字符串")
                null
            } else {
                Log.d(TAG, "数据加密成功，长度: ${result.length}")
                result
            }
        } catch (e: Exception) {
            Log.e(TAG, "加密过程中发生异常", e)
            null
        }
    }
    
    /**
     * 解密数据
     * @param ciphertext base64编码的密文
     * @return 解密后的明文，失败返回null
     */
    fun decryptData(ciphertext: String): String? {
        return try {
            val result = Batteryencryption.decryptData(ciphertext)
            if (result.isEmpty()) {
                Log.e(TAG, "解密失败：返回空字符串")
                null
            } else {
                Log.d(TAG, "数据解密成功，长度: ${result.length}")
                result
            }
        } catch (e: Exception) {
            Log.e(TAG, "解密过程中发生异常", e)
            null
        }
    }
    
    /**
     * 生成签名
     * @param data 数据
     * @param timestamp 时间戳
     * @param nonce 随机数
     * @return 签名字符串，失败返回null
     */
    fun generateSignature(data: String, timestamp: String, nonce: String): String? {
        return try {
            val result = Batteryencryption.generateSignature(data, timestamp, nonce)
            if (result.isEmpty()) {
                Log.e(TAG, "签名生成失败：返回空字符串")
                null
            } else {
                Log.d(TAG, "签名生成成功")
                result
            }
        } catch (e: Exception) {
            Log.e(TAG, "签名生成过程中发生异常", e)
            null
        }
    }
    
    /**
     * 生成MD5哈希
     * @param input 输入字符串
     * @return MD5哈希值，失败返回null
     */
    fun generateMD5Hash(input: String): String? {
        return try {
            val result = Batteryencryption.mD5Hash(input)
            if (result.isEmpty()) {
                Log.e(TAG, "MD5哈希生成失败：返回空字符串")
                null
            } else {
                Log.d(TAG, "MD5哈希生成成功")
                result
            }
        } catch (e: Exception) {
            Log.e(TAG, "MD5哈希生成过程中发生异常", e)
            null
        }
    }
    
    /**
     * 生成当前时间戳（毫秒）
     */
    fun getCurrentTimestamp(): String {
        return System.currentTimeMillis().toString()
    }
    
    /**
     * 生成随机nonce
     */
    fun generateNonce(): String {
        return UUID.randomUUID().toString().replace("-", "")
    }
    
    /**
     * 验证加密库是否可用
     * @return true表示可用，false表示不可用
     */
    fun isEncryptionLibraryAvailable(): Boolean {
        return try {
            // 尝试进行一个简单的加密测试
            val testData = "test"
            val encrypted = Batteryencryption.encryptData(testData)
            val decrypted = Batteryencryption.decryptData(encrypted)
            
            val isWorking = testData == decrypted && encrypted.isNotEmpty()
            Log.d(TAG, "加密库可用性测试: $isWorking")
            isWorking
        } catch (e: Exception) {
            Log.e(TAG, "加密库不可用", e)
            false
        }
    }
    
    /**
     * 创建用于API请求的加密数据包
     * @param data 原始数据
     * @param token 用户token
     * @return 包含加密数据和签名的数据包，失败返回null
     */
    fun createEncryptedPackage(data: String, token: String): EncryptedPackage? {
        return try {
            val timestamp = getCurrentTimestamp()
            val nonce = generateNonce()
            
            // 加密数据
            val encryptedData = encryptData(data) ?: return null
            
            // 生成签名
            val signature = generateSignature(encryptedData, timestamp, nonce) ?: return null
            
            EncryptedPackage(
                encryptedData = encryptedData,
                signature = signature,
                timestamp = timestamp,
                nonce = nonce,
                token = token
            )
        } catch (e: Exception) {
            Log.e(TAG, "创建加密数据包失败", e)
            null
        }
    }
    
    /**
     * 生成简单的随机nonce（6位数字）
     */
    fun generateSimpleNonce(): String {
        return (Math.random() * 1000000).toInt().toString()
    }
    
    /**
     * 加密数据包
     */
    data class EncryptedPackage(
        val encryptedData: String,
        val signature: String,
        val timestamp: String,
        val nonce: String,
        val token: String
    )
}