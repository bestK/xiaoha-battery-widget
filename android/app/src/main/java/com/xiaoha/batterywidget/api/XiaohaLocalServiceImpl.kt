package com.xiaoha.batterywidget.api

import android.util.Base64
import java.nio.charset.Charset
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.*

object XiaohaLocalServiceImpl : BatteryService{
    private const val AES_KEY = "0199bec97dfa5e0d"
    private const val AES_IV = "0199bec97dfa5e0d"
    private const val SIGN_SECRET = "386a09ea946d46f1b4ca3b4e3df8de45"
    private val gson = Gson()
    private val client = OkHttpClient()

    // AES加密
    private fun encryptData(plainObj: Any): String {
        val jsonStr = gson.toJson(plainObj)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val keySpec = SecretKeySpec(AES_KEY.toByteArray(Charsets.UTF_8), "AES")
        val ivSpec = IvParameterSpec(AES_IV.toByteArray(Charsets.UTF_8))
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        val encrypted = cipher.doFinal(jsonStr.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    // AES解密
    private fun decryptData(cipherText: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val keySpec = SecretKeySpec(AES_KEY.toByteArray(Charsets.UTF_8), "AES")
        val ivSpec = IvParameterSpec(AES_IV.toByteArray(Charsets.UTF_8))
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        val decoded = Base64.decode(cipherText, Base64.NO_WRAP)
        val decrypted = cipher.doFinal(decoded)
        return String(decrypted, Charsets.UTF_8)
    }

    // 生成签名
    private fun generateSignature(dataStr: String, timestamp: String, nonce: String): String {
        val signStr = dataStr + timestamp + nonce + SIGN_SECRET
        val md5 = MessageDigest.getInstance("MD5")
        val digest = md5.digest(signStr.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    // 获取电池信息
    override fun getBatteryInfo(
        batteryNo: String,
        cityCode: String = "0755",
        token: String = "2d0eda3d763b4764b26c73ee830ed364"
    ): BatteryResponse? {
        val url = "https://switchpowerapi.hellobike.com/api?switchpower.user.getBatteryLifeInfo"
        val bodyMap = mapOf(
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
        val encryptedData = encryptData(bodyMap)
        val timestamp = System.currentTimeMillis().toString()
        val nonce = (100000..999999).random().toString()
        val signature = generateSignature(encryptedData, timestamp, nonce)

        val mediaType = "application/json".toMediaTypeOrNull()
        val requestBody = RequestBody.create(mediaType, encryptedData)
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Host", "switchpowerapi.hellobike.com")
            .addHeader("Connection", "keep-alive")
            .addHeader("chaos", "true")
            .addHeader("x-chaos-env", "pro-1.1.3-beta.1")
            .addHeader("content-type", "application/json")
            .addHeader("signature", signature)
            .addHeader("timestamp", timestamp)
            .addHeader("systemCode", "A4")
            .addHeader("nonce", nonce)
            .addHeader("Accept-Encoding", "gzip,compress,br,deflate")
            .addHeader(
                "User-Agent",
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 MicroMessenger/8.0.56(0x1800383b) NetType/WIFI Language/zh_CN"
            )
            .addHeader(
                "Referer",
                "https://servicewechat.com/wxc99bea6e25f8ab86/111/page-frame.html"
            )
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Unexpected code $response")
            val respBody = response.body?.string() ?: return null
            // 解密
            val decrypted = decryptData(respBody)
            // 反序列化为 BatteryResponse
            return gson.fromJson(decrypted, BatteryResponse::class.java)
        }
    }

    // 格式化时间戳
    fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
        sdf.timeZone = TimeZone.getTimeZone("Asia/Shanghai")
        return sdf.format(Date(timestamp))
    }
}
