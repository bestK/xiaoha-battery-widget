package com.xiaoha.batterywidget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * 通知管理器
 * 用于处理电池电量更新通知
 */
class NotificationManager(private val context: Context) {
    companion object {
        private const val CHANNEL_ID = "battery_update"
        private const val NOTIFICATION_ID = 1
        private const val PREF_NAME = "notification_prefs"
        private const val PREF_LAST_BATTERY = "last_battery_level"
        private const val PREF_NOTIFICATION_ENABLED = "notification_enabled"

        fun isNotificationEnabled(context: Context, batteryNo: String): Boolean {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean("${PREF_NOTIFICATION_ENABLED}_$batteryNo", false)
        }

        fun setNotificationEnabled(context: Context, batteryNo: String, enabled: Boolean) {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean("${PREF_NOTIFICATION_ENABLED}_$batteryNo", enabled).apply()
        }
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "电池电量更新"
            val descriptionText = "显示电池电量更新通知"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 发送电量更新通知
     * @param batteryNo 电池编号
     * @param batteryLife 当前电量
     * @param reportTime 更新时间
     * @param isTest 是否是测试通知，测试通知会忽略电量变化检查
     */
    fun showBatteryUpdateNotification(batteryNo: String, batteryLife: Int, reportTime: String, isTest: Boolean = false) {
        // 检查是否启用了通知
        if (!isNotificationEnabled(context, batteryNo)) {
            return
        }

        // 如果不是测试通知，检查电量是否有变化
        if (!isTest) {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val lastBattery = prefs.getInt("${PREF_LAST_BATTERY}_$batteryNo", -1)
            if (lastBattery == batteryLife) {
                return // 电量没有变化，不发送通知
            }

            // 保存当前电量
            prefs.edit().putInt("${PREF_LAST_BATTERY}_$batteryNo", batteryLife).apply()
        }

        // 创建点击通知时打开的Intent
        val intent = Intent(context, BatteryWidgetConfigureActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        // 构建通知
        val title = if (isTest) "测试通知" else "电池电量更新"
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_battery_full)
            .setContentTitle(title)
            .setContentText("电池 $batteryNo 当前电量: $batteryLife% (更新时间: $reportTime)")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        // 根据电量设置不同的图标
        val iconRes = when {
            batteryLife >= 80 -> R.drawable.ic_battery_full
            batteryLife >= 50 -> R.drawable.ic_battery_good
            batteryLife >= 20 -> R.drawable.ic_battery_medium
            else -> R.drawable.ic_battery_low
        }
        builder.setSmallIcon(iconRes)

        // 发送通知
        with(NotificationManagerCompat.from(context)) {
            try {
                notify(if (isTest) 0 else NOTIFICATION_ID, builder.build())
            } catch (e: SecurityException) {
                // 通知权限被禁用，可以在这里处理
            }
        }
    }
}