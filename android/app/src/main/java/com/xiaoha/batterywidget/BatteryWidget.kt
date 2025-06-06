package com.xiaoha.batterywidget

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
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
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
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
            val baseUrl = prefs.getString("base_url", "https://xiaoha.linkof.link")!!


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
            setAlarmManager(context, batteryNo, prefs.getInt("refreshInterval_$appWidgetId", 30))
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
        val cityCode = prefs.getString("cityCode_$appWidgetId", "0755") ?: "0755"
        val refreshInterval = prefs.getInt("refreshInterval_$appWidgetId", 30)

        if (batteryNo.isEmpty()) {
            Log.d(TAG, "No battery number configured for widget ID: $appWidgetId")
            updateErrorState(views, "点击配置")
            appWidgetManager.updateAppWidget(appWidgetId, views)
            return
        }

        // 更新小部件
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = withTimeout(5000) {
                    apiService?.getBatteryInfo(batteryNo, cityCode)?.execute()
                }

                withContext(Dispatchers.Main) {
                    if (response != null) {
                        if (response.isSuccessful) {
                            val batteryResponse = response.body()
                            if (batteryResponse?.code == 0) {
                                val batteryLife = batteryResponse.data.batteryLife
                                val reportTime = Date(batteryResponse.data.reportTime)
                                val formattedTime = dateFormat.format(reportTime)

                                views.setProgressBar(R.id.progress_circle, 100, batteryLife, false)
                                views.setTextViewText(R.id.percent_text, "$batteryLife%")
                                views.setTextViewText(R.id.battery_no, batteryNo)
                                views.setTextViewText(R.id.update_time, formattedTime)

                            } else {
                                Log.e(TAG, "Error in API response: $batteryResponse")
                                updateErrorState(views, "数据错误")
                            }
                        } else {
                            Log.e(
                                TAG,
                                "Network request failed: ${response.code()} ${
                                    Companion.prefs.getString(
                                        "base_url",
                                        ""
                                    )
                                }"
                            )
                            updateErrorState(views, "网络错误")
                        }
                    }
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
} 