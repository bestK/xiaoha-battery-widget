package com.xiaoha.batterywidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 检查是否已经添加了小部件
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val widgetComponent = ComponentName(this, BatteryWidget::class.java)
        val widgetIds = appWidgetManager.getAppWidgetIds(widgetComponent)
            .filter { it != AppWidgetManager.INVALID_APPWIDGET_ID && it != 0 }
            .toIntArray()

        // 检查是否是从小部件配置启动的
        val isFromWidget = intent?.hasExtra(AppWidgetManager.EXTRA_APPWIDGET_ID) == true
        
        if (!isFromWidget && widgetIds.isNotEmpty()) {
            // 如果是从桌面图标启动且已有小部件，打开配置页面
            val intent = Intent(this, BatteryWidgetConfigureActivity::class.java)
            startActivity(intent)
            finish()
        } else if (!isFromWidget) {
            // 如果是从桌面图标启动且没有小部件，显示提示
            AlertDialog.Builder(this)
                .setTitle(R.string.add_widget_title)
                .setMessage(R.string.add_widget_tip)
                .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
                .setCancelable(false)
                .show()
        } else {
            // 如果是从小部件配置启动，直接关闭
            finish()
        }
    }
}