package com.xiaoha.batterywidget

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import batteryencryption.Batteryencryption
import com.xiaoha.batterywidget.utils.LocalEncryptionUtil

/**
 * 加密测试活动 - 用于测试本地加密库的各种功能
 */
class EncryptionTestActivity : AppCompatActivity() {
    
    private lateinit var inputText: EditText
    private lateinit var outputText: TextView
    private lateinit var encryptButton: Button
    private lateinit var decryptButton: Button
    private lateinit var hashButton: Button
    private lateinit var signatureButton: Button
    private lateinit var clearButton: Button
    
    companion object {
        private const val TAG = "EncryptionTest"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_encryption_test)
        
        initViews()
        setupClickListeners()
        
        // 测试加密库是否可用
        testEncryptionLibrary()
    }
    
    private fun initViews() {
        inputText = findViewById(R.id.input_text)
        outputText = findViewById(R.id.output_text)
        encryptButton = findViewById(R.id.encrypt_button)
        decryptButton = findViewById(R.id.decrypt_button)
        hashButton = findViewById(R.id.hash_button)
        signatureButton = findViewById(R.id.signature_button)
        clearButton = findViewById(R.id.clear_button)
        
        // 设置默认测试文本
        inputText.setText("Hello, Battery Widget Encryption Test!")
    }
    
    private fun setupClickListeners() {
        encryptButton.setOnClickListener {
            val input = inputText.text.toString()
            if (input.isEmpty()) {
                Toast.makeText(this, "请输入要加密的文本", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            try {
                val encrypted = Batteryencryption.encryptData(input)
                if (encrypted.isEmpty()) {
                    outputText.text = "加密失败：返回空字符串"
                } else {
                    outputText.text = "加密结果：\n$encrypted"
                    Log.d(TAG, "加密成功，长度: ${encrypted.length}")
                }
            } catch (e: Exception) {
                outputText.text = "加密异常：${e.message}"
                Log.e(TAG, "加密异常", e)
            }
        }
        
        decryptButton.setOnClickListener {
            val input = inputText.text.toString()
            if (input.isEmpty()) {
                Toast.makeText(this, "请输入要解密的文本", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            try {
                val decrypted = Batteryencryption.decryptData(input)
                if (decrypted.isEmpty()) {
                    outputText.text = "解密失败：返回空字符串"
                } else {
                    outputText.text = "解密结果：\n$decrypted"
                    Log.d(TAG, "解密成功，长度: ${decrypted.length}")
                }
            } catch (e: Exception) {
                outputText.text = "解密异常：${e.message}"
                Log.e(TAG, "解密异常", e)
            }
        }
        
        hashButton.setOnClickListener {
            val input = inputText.text.toString()
            if (input.isEmpty()) {
                Toast.makeText(this, "请输入要计算哈希的文本", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            try {
                val hash = Batteryencryption.mD5Hash(input)
                if (hash.isEmpty()) {
                    outputText.text = "哈希计算失败：返回空字符串"
                } else {
                    outputText.text = "MD5哈希：\n$hash"
                    Log.d(TAG, "MD5哈希计算成功")
                }
            } catch (e: Exception) {
                outputText.text = "哈希计算异常：${e.message}"
                Log.e(TAG, "哈希计算异常", e)
            }
        }
        
        signatureButton.setOnClickListener {
            val input = inputText.text.toString()
            if (input.isEmpty()) {
                Toast.makeText(this, "请输入要签名的文本", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            try {
                val timestamp = LocalEncryptionUtil.getCurrentTimestamp()
                val nonce = LocalEncryptionUtil.generateNonce()
                val signature = Batteryencryption.generateSignature(input, timestamp, nonce)
                
                if (signature.isEmpty()) {
                    outputText.text = "签名生成失败：返回空字符串"
                } else {
                    outputText.text = "签名结果：\n" +
                            "数据: $input\n" +
                            "时间戳: $timestamp\n" +
                            "随机数: $nonce\n" +
                            "签名: $signature"
                    Log.d(TAG, "签名生成成功")
                }
            } catch (e: Exception) {
                outputText.text = "签名生成异常：${e.message}"
                Log.e(TAG, "签名生成异常", e)
            }
        }
        
        clearButton.setOnClickListener {
            inputText.setText("")
            outputText.text = ""
        }
    }
    
    private fun testEncryptionLibrary() {
        try {
            Log.d(TAG, "开始测试加密库...")
            
            // 测试基本加解密
            val testData = "Test Data 123"
            val encrypted = Batteryencryption.encryptData(testData)
            val decrypted = Batteryencryption.decryptData(encrypted)
            
            if (testData == decrypted && encrypted.isNotEmpty()) {
                Log.d(TAG, "加密库测试通过")
                Toast.makeText(this, "加密库可用", Toast.LENGTH_SHORT).show()
            } else {
                Log.e(TAG, "加密库测试失败：数据不匹配")
                Toast.makeText(this, "加密库测试失败", Toast.LENGTH_LONG).show()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "加密库测试异常", e)
            Toast.makeText(this, "加密库不可用：${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}