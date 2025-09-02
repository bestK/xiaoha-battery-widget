package com.xiaoha.batterywidget.examples

import android.util.Log
import batteryencryption.Batteryencryption
import com.xiaoha.batterywidget.utils.LocalEncryptionUtil

/**
 * 本地加密库使用示例
 * 
 * 这个类展示了如何使用 batteryencryption.Batteryencryption 原生库
 * 以及封装的 LocalEncryptionUtil 工具类
 */
object EncryptionExample {
    private const val TAG = "EncryptionExample"
    
    /**
     * 基本加解密示例
     */
    fun basicEncryptionExample() {
        Log.d(TAG, "=== 基本加解密示例 ===")
        
        val originalData = "这是需要加密的敏感数据"
        Log.d(TAG, "原始数据: $originalData")
        
        // 使用原生库直接加密
        val encrypted = Batteryencryption.encryptData(originalData)
        if (encrypted.isEmpty()) {
            Log.e(TAG, "加密失败")
            return
        }
        Log.d(TAG, "加密结果: $encrypted")
        
        // 使用原生库直接解密
        val decrypted = Batteryencryption.decryptData(encrypted)
        if (decrypted.isEmpty()) {
            Log.e(TAG, "解密失败")
            return
        }
        Log.d(TAG, "解密结果: $decrypted")
        
        // 验证结果
        if (originalData == decrypted) {
            Log.d(TAG, "✅ 加解密测试通过")
        } else {
            Log.e(TAG, "❌ 加解密测试失败")
        }
    }
    
    /**
     * 使用工具类的加解密示例
     */
    fun utilityEncryptionExample() {
        Log.d(TAG, "=== 工具类加解密示例 ===")
        
        val originalData = "使用工具类进行加密的数据"
        Log.d(TAG, "原始数据: $originalData")
        
        // 使用工具类加密
        val encrypted = LocalEncryptionUtil.encryptData(originalData)
        if (encrypted == null) {
            Log.e(TAG, "工具类加密失败")
            return
        }
        Log.d(TAG, "加密结果: $encrypted")
        
        // 使用工具类解密
        val decrypted = LocalEncryptionUtil.decryptData(encrypted)
        if (decrypted == null) {
            Log.e(TAG, "工具类解密失败")
            return
        }
        Log.d(TAG, "解密结果: $decrypted")
        
        // 验证结果
        if (originalData == decrypted) {
            Log.d(TAG, "✅ 工具类加解密测试通过")
        } else {
            Log.e(TAG, "❌ 工具类加解密测试失败")
        }
    }
    
    /**
     * MD5哈希示例
     */
    fun hashExample() {
        Log.d(TAG, "=== MD5哈希示例 ===")
        
        val data = "需要计算哈希的数据"
        Log.d(TAG, "原始数据: $data")
        
        // 使用原生库计算MD5
        val hash1 = Batteryencryption.mD5Hash(data)
        Log.d(TAG, "原生库MD5: $hash1")
        
        // 使用工具类计算MD5
        val hash2 = LocalEncryptionUtil.generateMD5Hash(data)
        Log.d(TAG, "工具类MD5: $hash2")
        
        if (hash1 == hash2 && hash1.isNotEmpty()) {
            Log.d(TAG, "✅ MD5哈希测试通过")
        } else {
            Log.e(TAG, "❌ MD5哈希测试失败")
        }
    }
    
    /**
     * 数字签名示例
     */
    fun signatureExample() {
        Log.d(TAG, "=== 数字签名示例 ===")
        
        val data = "需要签名的数据"
        val timestamp = System.currentTimeMillis().toString()
        val nonce = java.util.UUID.randomUUID().toString().replace("-", "")
        
        Log.d(TAG, "数据: $data")
        Log.d(TAG, "时间戳: $timestamp")
        Log.d(TAG, "随机数: $nonce")
        
        // 使用原生库生成签名
        val signature1 = Batteryencryption.generateSignature(data, timestamp, nonce)
        Log.d(TAG, "原生库签名: $signature1")
        
        // 使用工具类生成签名
        val signature2 = LocalEncryptionUtil.generateSignature(data, timestamp, nonce)
        Log.d(TAG, "工具类签名: $signature2")
        
        if (signature1 == signature2 && signature1.isNotEmpty()) {
            Log.d(TAG, "✅ 数字签名测试通过")
        } else {
            Log.e(TAG, "❌ 数字签名测试失败")
        }
    }
    
    /**
     * 创建加密数据包示例
     */
    fun encryptedPackageExample() {
        Log.d(TAG, "=== 加密数据包示例 ===")
        
        val originalData = """
            {
                "batteryNo": "ABC123456",
                "token": "user_token_here",
                "requestTime": "${System.currentTimeMillis()}"
            }
        """.trimIndent()
        
        val userToken = "user_authentication_token"
        
        Log.d(TAG, "原始JSON数据: $originalData")
        Log.d(TAG, "用户Token: $userToken")
        
        // 创建加密数据包
        val encryptedPackage = LocalEncryptionUtil.createEncryptedPackage(originalData, userToken)
        if (encryptedPackage == null) {
            Log.e(TAG, "创建加密数据包失败")
            return
        }
        
        Log.d(TAG, "加密数据包创建成功:")
        Log.d(TAG, "  加密数据: ${encryptedPackage.encryptedData}")
        Log.d(TAG, "  签名: ${encryptedPackage.signature}")
        Log.d(TAG, "  时间戳: ${encryptedPackage.timestamp}")
        Log.d(TAG, "  随机数: ${encryptedPackage.nonce}")
        Log.d(TAG, "  Token: ${encryptedPackage.token}")
        
        // 验证：解密数据包中的数据
        val decryptedData = LocalEncryptionUtil.decryptData(encryptedPackage.encryptedData)
        if (decryptedData == originalData) {
            Log.d(TAG, "✅ 加密数据包测试通过")
        } else {
            Log.e(TAG, "❌ 加密数据包测试失败")
            Log.d(TAG, "解密结果: $decryptedData")
        }
    }
    
    /**
     * 运行所有示例
     */
    fun runAllExamples() {
        Log.d(TAG, "开始运行所有加密示例...")
        
        try {
            // 检查加密库是否可用
            if (!LocalEncryptionUtil.isEncryptionLibraryAvailable()) {
                Log.e(TAG, "加密库不可用，跳过示例")
                return
            }
            
            basicEncryptionExample()
            utilityEncryptionExample()
            hashExample()
            signatureExample()
            encryptedPackageExample()
            
            Log.d(TAG, "所有加密示例运行完成")
            
        } catch (e: Exception) {
            Log.e(TAG, "运行加密示例时发生异常", e)
        }
    }
}