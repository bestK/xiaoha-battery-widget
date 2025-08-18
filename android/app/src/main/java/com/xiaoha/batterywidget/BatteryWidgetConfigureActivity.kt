package com.xiaoha.batterywidget

import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BatteryWidgetConfigureActivity : AppCompatActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var batteryNoEdit: EditText
    private lateinit var cityCodeEdit: EditText
    private lateinit var tokenEdit: EditText
    private lateinit var baseUrlEdit: EditText
    private lateinit var refreshIntervalSpinner: Spinner
    private lateinit var addButton: Button
    

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.battery_widget_configure)

        // 获取小部件ID
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // 初始化视图引用
        initViews()


        // 在后台初始化
        lifecycleScope.launch {
            try {
                // 在后台线程准备数据
                val initData = withContext(Dispatchers.Default) {
                    val prefs = getSharedPreferences("BatteryWidgetPrefs", MODE_PRIVATE)
                    val savedBatteryNo = prefs.getString("batteryNo_$appWidgetId", "")
                    val savedCityCode = prefs.getString("cityCode_$appWidgetId", "0755")
                    val savedToken = prefs.getString("token_$appWidgetId", "")
                    val savedRefreshInterval = prefs.getInt("refreshInterval_$savedBatteryNo", 5)
                    val baseUrl = prefs.getString("baseUrl", "https://xiaoha.linkof.link/")
                    val refreshIntervals = resources.getStringArray(R.array.refresh_intervals)
                    val intervals = resources.getIntArray(R.array.refresh_interval_values)
                    val position = intervals.indexOf(savedRefreshInterval)
                    InitData(
                        savedBatteryNo.toString(),
                        savedCityCode.toString(),
                        savedToken.toString(),
                        baseUrl.toString(),
                        position,
                        refreshIntervals,
                    )
                }

                // 在主线程更新UI
                withContext(Dispatchers.Main) {
                    setupViews(initData)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@BatteryWidgetConfigureActivity, 
                        "初始化失败：${e.message}", 
                        Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun initViews() {
        batteryNoEdit = findViewById(R.id.battery_no_edit)
        cityCodeEdit = findViewById(R.id.city_code_edit)
        tokenEdit = findViewById(R.id.token_edit)
        baseUrlEdit = findViewById(R.id.base_url_edit)
        refreshIntervalSpinner = findViewById(R.id.refresh_interval_spinner)
        addButton = findViewById(R.id.add_button)

        // 添加 GitHub 链接点击事件
        findViewById<View>(R.id.github_container).setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                   data = getString(R.string.github_link).toUri()
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "无法打开链接：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }



    private fun setupViews(initData: InitData) {
        // 设置已保存的值
        batteryNoEdit.setText(initData.batteryNo)
        cityCodeEdit.setText(initData.cityCode)
        tokenEdit.setText(initData.token)
        baseUrlEdit.setText(initData.baseUrl)

        // 设置刷新间隔选项
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            initData.refreshIntervals
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        refreshIntervalSpinner.adapter = adapter

        // 设置已保存的刷新间隔
        if (initData.refreshIntervalPosition != -1) {
            refreshIntervalSpinner.setSelection(initData.refreshIntervalPosition)
        }

        // 设置保存按钮点击事件
        addButton.setOnClickListener {
            saveConfiguration()
        }
    }

    private fun saveConfiguration() {
        checkAndRequestExactAlarmPermission()
        val batteryNo = batteryNoEdit.text.toString().trim()
        val cityCode = cityCodeEdit.text.toString().trim().let {
            if (it.isEmpty()) "0755" else it
        }
        val token = tokenEdit.text.toString().trim()
        
        if (batteryNo.isEmpty()) {
            AlertDialog.Builder(this@BatteryWidgetConfigureActivity)
                .setTitle("保存失败")
                .setMessage("请输入电池编号")
                .setPositiveButton("确定", null)
                .show()
            return
        }
        
        if (token.isEmpty()) {
            AlertDialog.Builder(this@BatteryWidgetConfigureActivity)
                .setTitle("保存失败")
                .setMessage("请输入token")
                .setPositiveButton("确定", null)
                .show()
            return
        }

        // 禁用输入和按钮
        setInputsEnabled(false)
        addButton.text = "保存中..."

        lifecycleScope.launch {
            try {
                // 保存配置
                withContext(Dispatchers.IO) {
                    val prefs = getSharedPreferences("BatteryWidgetPrefs", MODE_PRIVATE)
                    prefs.edit {

                        // 保存电池号、城市代码和token
                        putString("batteryNo_$appWidgetId", batteryNo)
                        putString("cityCode_$appWidgetId", cityCode)
                        putString("token_$appWidgetId", token)

                        // 保存刷新间隔
                        val intervals = resources.getIntArray(R.array.refresh_interval_values)
                        val selectedInterval = intervals[refreshIntervalSpinner.selectedItemPosition]
                        putInt("refreshInterval_$batteryNo", selectedInterval)

                    }

                    // 更新小部件
                    val appWidgetManager = AppWidgetManager.getInstance(this@BatteryWidgetConfigureActivity)
                    val widget = BatteryWidget()
                    widget.onUpdate(this@BatteryWidgetConfigureActivity, appWidgetManager, intArrayOf(appWidgetId))
                }

                // 设置结果并关闭活动
                val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                setResult(RESULT_OK, resultValue)
                finish()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    AlertDialog.Builder(this@BatteryWidgetConfigureActivity)
                        .setTitle("保存失败")
                        .setMessage(e.message ?: "未知错误")
                        .setPositiveButton("确定", null)
                        .show()
                    setInputsEnabled(true)
                    addButton.text = getString(R.string.add_widget)
                }
            }
        }
    }

    private fun setInputsEnabled(enabled: Boolean) {
        batteryNoEdit.isEnabled = enabled
        cityCodeEdit.isEnabled = enabled
        tokenEdit.isEnabled = enabled
        refreshIntervalSpinner.isEnabled = enabled
        addButton.isEnabled = enabled
    }

    private fun checkAndRequestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                // 跳转系统设置界面让用户授权
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                startActivity(intent)
            }
        }
    }

    private data class InitData(
        val batteryNo: String,
        val cityCode: String,
        val token: String,
        val baseUrl: String,
        val refreshIntervalPosition: Int,
        val refreshIntervals: Array<String>
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as InitData

            if (refreshIntervalPosition != other.refreshIntervalPosition) return false
            if (batteryNo != other.batteryNo) return false
            if (cityCode != other.cityCode) return false
            if (token != other.token) return false
            if (baseUrl != other.baseUrl) return false
            if (!refreshIntervals.contentEquals(other.refreshIntervals)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = refreshIntervalPosition
            result = 31 * result + batteryNo.hashCode()
            result = 31 * result + cityCode.hashCode()
            result = 31 * result + token.hashCode()
            result = 31 * result + baseUrl.hashCode()
            result = 31 * result + refreshIntervals.contentHashCode()
            return result
        }
    }
} 