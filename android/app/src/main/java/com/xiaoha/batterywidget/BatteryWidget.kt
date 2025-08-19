package com.xiaoha.batterywidget

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import android.widget.RemoteViews
import com.xiaoha.batterywidget.api.BatteryService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class BatteryWidget : AppWidgetProvider() {
    companion object {
        private var logoBitmap: Bitmap? = null
        private val updateJobs = ConcurrentHashMap<Int, Job>()
        private val dateFormat = SimpleDateFormat("M/d HH:mm", Locale.US)
        private const val TAG = "BatteryWidget"
        private const val ACTION_REFRESH = "com.xiaoha.batterywidget.ACTION_REFRESH"
        private const val DOUBLE_CLICK_TIMEOUT = 500L // 双击超时时间（毫秒）
        private val lastClickTimes = mutableMapOf<Int, Long>() // 记录每个小部件的最后点击时间


        private var apiService: BatteryService? = null
        private lateinit var prefs: SharedPreferences
        private lateinit var retrofit: Retrofit

        fun init(context: Context) {
            prefs = context.getSharedPreferences("BatteryWidgetPrefs", Context.MODE_PRIVATE)
            val baseUrl = prefs.getString("base_url", "https://xiaoha.linkof.link/")!!

            // 添加日志拦截器
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY // 可选：BASIC、HEADERS、BODY
            }
            val okHttpClient = OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .build()

            retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .client(okHttpClient) // 使用带日志的 OkHttpClient
                .build()

            apiService = retrofit.create(BatteryService::class.java)
        }
    }

    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Log.d(TAG, "Widget enabled")
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "onUpdate called for widget IDs: ${appWidgetIds.joinToString()}")
        init(context)
        val prefs = context.getSharedPreferences("BatteryWidgetPrefs", Context.MODE_PRIVATE)
        for (appWidgetId in appWidgetIds) {
            if (appWidgetId == 0) {
                Log.d(TAG, "忽略无效的 widgetId: 0")
                continue
            }
            val batteryNo = prefs.getString("batteryNo_$appWidgetId", "") ?: ""
            if (batteryNo.isEmpty()) {
                Log.d(TAG, "widgetId $appWidgetId 未配置电池号，跳过刷新和定时任务")
                continue
            }
            // 1. 立即刷新
            updateAppWidget(context, appWidgetManager, appWidgetId, "onUpdate")
            // 2. 设置定时任务
            setAlarmManager(context, batteryNo, prefs.getInt("refreshInterval_$batteryNo", 5))
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        Log.d(TAG, "onDeleted called for widget IDs: ${appWidgetIds.joinToString()}")

        appWidgetIds.forEach { appWidgetId ->
            updateJobs[appWidgetId]?.cancel()
            updateJobs.remove(appWidgetId)

            // 清除配置
            context.getSharedPreferences("BatteryWidgetPrefs", Context.MODE_PRIVATE)
                .edit()
                .remove("batteryNo_$appWidgetId")
                .apply()
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Log.d(TAG, "Widget disabled")
        coroutineScope.cancel()
        updateJobs.clear()
        logoBitmap = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == ACTION_REFRESH) {
            Log.i(TAG, "onReceive: ACTION_REFRESH")
            val appWidgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val currentTime = SystemClock.elapsedRealtime()
                val lastClickTime = lastClickTimes[appWidgetId] ?: 0L

                if (currentTime - lastClickTime <= DOUBLE_CLICK_TIMEOUT) {
                    // 双击检测到，立即刷新
                    updateAppWidget(
                        context,
                        AppWidgetManager.getInstance(context),
                        appWidgetId,
                        "doubleClick"
                    )
                    lastClickTimes.remove(appWidgetId) // 清除点击记录
                } else {
                    // 单击，打开配置页面
                    val configIntent =
                        Intent(context, BatteryWidgetConfigureActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        }
                    context.startActivity(configIntent)
                    // 记录点击时间用于双击检测
                    lastClickTimes[appWidgetId] = currentTime
                }
            }
        } else if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            Log.i(TAG, "onReceive: ACTION_APPWIDGET_UPDATE")
            val batteryNo = intent.getStringExtra("batteryNo")
            if (!batteryNo.isNullOrEmpty()) {
                val prefs = context.getSharedPreferences("BatteryWidgetPrefs", Context.MODE_PRIVATE)
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val allWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, BatteryWidget::class.java))
                for (widgetId in allWidgetIds) {
                    val widgetBatteryNo = prefs.getString("batteryNo_$widgetId", "")
                    if (widgetBatteryNo == batteryNo) {
                        updateAppWidget(context, appWidgetManager, widgetId, "AlarmManager")
                    }
                }
            }
        }
    }

    private fun decodeLogo(): Bitmap? {
        return try {
            if (logoBitmap == null) {
                val logoBase64 =
                    "iVBORw0KGgoAAAANSUhEUgAAABwAAAAcCAMAAABF0y+mAAAAbFBMVEUAiP4Ah/4AhP4lj/4Agv4Af/6Qv//////R4/8Aff5Uov7w9//p8/9lqf/I3v/w9f+pzv9srP/4/P+82P+Huf7e6v9xsf99tf640/8ylP6hyf9Hnf6dxv8Ag/7k7/8Aev7X5/8Adv5AmP4RjP7GGMZdAAAA9klEQVR4AdXLBwKDIAxA0QSDwb23VKX3v2MZnUfodzAewJ+Gn1noy0T0WuL3aZKSgGJWAkFImaRZltsnK4S1sqrqpOG47aToq2po2pGbdsomi7KaS7Xwmmy8J3O18pgB6za6BZx2lXUH2dvbPGxccO6fJuCq0rW2TTgPKUMxTrYCIeB5daXNSI9LxcxtSU9UULsKjxGfy2a6m3xhw8MwtOpweB/NSkdeh5vjbpHk1Z0WN76T4a7nBR0OQ9bZ8zpRXdJzySQSwxwT2NCoLpLtWaxcCKzPlPou41iCD4kQzZDlk7ZzKag794XgKxSA+jkXpBF+Q/jzHpg8EYrSfggvAAAAAElFTkSuQmCC"
                val logoBytes = Base64.decode(logoBase64, Base64.DEFAULT)
                logoBitmap = BitmapFactory.decodeByteArray(logoBytes, 0, logoBytes.size)
            }
            logoBitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding logo", e)
            null
        }
    }

    @SuppressLint("RemoteViewLayout")
    fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        source: String = "unknown"
    ) {
        Log.d(TAG, "updateAppWidget called from $source for widgetId: $appWidgetId")

        val views = RemoteViews(context.packageName, R.layout.battery_widget)

        // 设置点击事件
        val refreshIntent = Intent(context, BatteryWidget::class.java).apply {
            action = ACTION_REFRESH
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        val refreshPendingIntent = PendingIntent.getBroadcast(
            context,
            appWidgetId,  // 使用 appWidgetId 作为请求码，确保每个小部件有唯一的 PendingIntent
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 将点击事件设置到整个小部件布局
        views.setOnClickPendingIntent(R.id.widget_layout, refreshPendingIntent)

        // 设置 logo
        decodeLogo()?.let { bitmap ->
            views.setImageViewBitmap(R.id.logo, bitmap)
        }

        // 获取配置
        val prefs = context.getSharedPreferences("BatteryWidgetPrefs", Context.MODE_PRIVATE)
        val batteryNo = prefs.getString("batteryNo_$appWidgetId", "") ?: ""
        val token = prefs.getString("token_$appWidgetId", "") ?: ""

        if (batteryNo.isEmpty() || token.isEmpty()) {
            Log.d(TAG, "No battery number or token configured for widget ID: $appWidgetId")
            updateErrorState(views, "点击配置")
            appWidgetManager.updateAppWidget(appWidgetId, views)
            return
        }

        // 更新小部件
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "开始获取电池数据，batteryNo: $batteryNo")
                
                // 步骤1: 获取预处理参数
                val tokenRequestBody = token.toRequestBody("text/plain".toMediaType())
                val preparamsResponse = withTimeout(10000) {
                    apiService?.getPreparams(batteryNo, tokenRequestBody)?.awaitResponse()
                }

                if (preparamsResponse == null || !preparamsResponse.isSuccessful) {
                    throw Exception("预处理参数请求失败: ${preparamsResponse?.code()}")
                }

                val preparamsData = preparamsResponse.body()?.data
                if (preparamsData == null) {
                    throw Exception("预处理参数响应数据无效")
                }

                Log.d(TAG, "预处理参数获取成功，url: ${preparamsData.url}")

                // 步骤2: 使用预处理参数获取电池数据
                val batteryRequestBody = preparamsData.body.toRequestBody("application/json".toMediaType())
                val batteryResponse = withTimeout(10000) {
                    // 创建新的OkHttpClient，设置headers
                    val loggingInterceptor = HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BODY
                    }
                    val batteryClient = OkHttpClient.Builder()
                        .addInterceptor { chain ->
                            val originalRequest = chain.request()
                            val newRequestBuilder = originalRequest.newBuilder()
                            
                            // 添加从preparams响应中获取的headers
                            preparamsData.headers.forEach { (key, value) ->
                                newRequestBuilder.addHeader(key, value)
                            }
                            
                            chain.proceed(newRequestBuilder.build())
                        }
                        .addInterceptor(loggingInterceptor)
                        .build()
                    
                    // 创建新的retrofit实例用于调用不同的base URL
                    val batteryRetrofit = Retrofit.Builder()
                        .baseUrl("https://dummy.base.url/") // 占位符，因为我们使用@Url
                        .addConverterFactory(GsonConverterFactory.create())
                        .client(batteryClient)
                        .build()
                    val batteryService = batteryRetrofit.create(BatteryService::class.java)
                    batteryService.getBatteryData(preparamsData.url, batteryRequestBody).awaitResponse()
                }

                if (batteryResponse == null || !batteryResponse.isSuccessful) {
                    throw Exception("电池数据请求失败: ${batteryResponse?.code()}")
                }

                val encryptedData = batteryResponse.body()
                if (encryptedData == null) {
                    throw Exception("电池数据响应为空")
                }

                // 一次性读取响应数据，避免重复读取
                val encryptedBytes = encryptedData.bytes()
                
                Log.d(TAG, "电池数据获取成功，开始解码")

                // 步骤3: 解码电池数据
                val decodeRequestBody = encryptedBytes.toRequestBody("application/octet-stream".toMediaType())
                val decodeResponse = withTimeout(10000) {
                    apiService?.decodeBatteryData(decodeRequestBody)?.awaitResponse()
                }

                if (decodeResponse == null || !decodeResponse.isSuccessful) {
                    throw Exception("解码请求失败: ${decodeResponse?.code()}")
                }

                val decodeData = decodeResponse.body()?.data?.data
                if (decodeData == null || decodeData.bindBatteries.isEmpty()) {
                    throw Exception("解码响应数据无效或电池数据为空")
                }

                val batteryInfo = decodeData.bindBatteries[0]
                Log.d(TAG, "解码成功，电池电量: ${batteryInfo.batteryLife}%")

                // 解析时间格式 (reportTime是时间戳)
                val reportTime = try {
                    // 尝试解析为时间戳（毫秒）
                    Date(batteryInfo.reportTime.toLong())
                } catch (e: Exception) {
                    try {
                        // 如果不是时间戳，尝试解析为ISO格式
                        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(batteryInfo.reportTime) 
                            ?: Date()
                    } catch (e2: Exception) {
                        Log.w(TAG, "时间解析失败，使用当前时间: ${batteryInfo.reportTime}", e)
                        Date()
                    }
                }
                
                val formattedTime = dateFormat.format(reportTime)

                withContext(Dispatchers.Main) {
                    views.setProgressBar(R.id.progress_circle, 100, batteryInfo.batteryLife, false)
                    views.setTextViewText(R.id.percent_text, "${batteryInfo.batteryLife}%")
                    views.setTextViewText(R.id.battery_no, batteryNo)
                    views.setTextViewText(R.id.update_time, formattedTime)
                    Log.d(TAG, "小部件更新成功")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating widget", e)
                withContext(Dispatchers.Main) {
                    updateErrorState(views, "更新失败")
                }
            } finally {
                withContext(Dispatchers.Main) {
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }
        }
    }


    private fun updateErrorState(views: RemoteViews, message: String) {
        views.setProgressBar(R.id.progress_circle, 100, 0, false)
        views.setTextViewText(R.id.percent_text, message)
        views.setTextViewText(R.id.battery_no, "")
        views.setTextViewText(R.id.update_time, "")
        views.setImageViewResource(R.id.logo, R.drawable.ic_battery_unknown)
    }

    private fun setAlarmManager(context: Context, batteryNo: String, refreshInterval: Int) {
        Log.i(TAG, "setAlarmManager: add job batteryNo: $batteryNo refreshInterval: $refreshInterval")
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val updateIntent = Intent(context, BatteryWidget::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra("batteryNo", batteryNo)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            batteryNo.hashCode(), // 用 batteryNo 的 hashCode 作为唯一标识
            updateIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        alarmManager.setExact(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + refreshInterval * 60 * 1000L,
            pendingIntent
        )
    }

    // 扩展函数：将Retrofit Call转换为挂起函数
    private suspend fun <T> Call<T>.awaitResponse(): Response<T> {
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation {
                cancel()
            }
            enqueue(object : Callback<T> {
                override fun onResponse(call: Call<T>, response: Response<T>) {
                    continuation.resume(response)
                }

                override fun onFailure(call: Call<T>, t: Throwable) {
                    continuation.resumeWithException(t)
                }
            })
        }
    }
} 