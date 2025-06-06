package com.xiaoha.batterywidget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.*
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*

class BatteryWidgetConfigureActivity : AppCompatActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var batteryNoEdit: EditText
    private lateinit var cityCodeEdit: EditText
    private lateinit var refreshIntervalSpinner: Spinner
    private lateinit var addButton: Button
    private lateinit var loadingView: View
    private lateinit var contentView: View
    private lateinit var serviceLocalSwitch: Switch
    

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

        // 显示加载状态
        showLoading(true)

        // 在后台初始化
        lifecycleScope.launch {
            try {
                // 在后台线程准备数据
                val initData = withContext(Dispatchers.Default) {
                    val prefs = getSharedPreferences("BatteryWidgetPrefs", Context.MODE_PRIVATE)
                    val savedBatteryNo = prefs.getString("batteryNo_$appWidgetId", "")
                    val savedCityCode = prefs.getString("cityCode_$appWidgetId", "0755")
                    val savedRefreshInterval = prefs.getInt("refreshInterval_$appWidgetId", 30)
                    val refreshIntervals = resources.getStringArray(R.array.refresh_intervals)
                    val intervals = resources.getIntArray(R.array.refresh_intervals)
                    val position = intervals.indexOf(savedRefreshInterval)
                    InitData(
                        savedBatteryNo ?: "",
                        savedCityCode ?: "0755",
                        position,
                        refreshIntervals
                    )
                }

                // 在主线程更新UI
                withContext(Dispatchers.Main) {
                    setupViews(initData)
                    showLoading(false)
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
        loadingView = findViewById(R.id.loading_view)
        contentView = findViewById(R.id.content_view)
        batteryNoEdit = findViewById(R.id.battery_no_edit)
        cityCodeEdit = findViewById(R.id.city_code_edit)
        refreshIntervalSpinner = findViewById(R.id.refresh_interval_spinner)
        addButton = findViewById(R.id.add_button)

        serviceLocalSwitch = findViewById(R.id.service_local_switch)

        // 添加 GitHub 链接点击事件
        findViewById<View>(R.id.github_container).setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                   data = android.net.Uri.parse(getString(R.string.github_link))
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "无法打开链接：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

         
    }

    private fun showLoading(show: Boolean) {
        loadingView.visibility = if (show) View.VISIBLE else View.GONE
        contentView.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun setupViews(initData: InitData) {
        // 设置已保存的值
        batteryNoEdit.setText(initData.batteryNo)
        cityCodeEdit.setText(initData.cityCode)

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
        val batteryNo = batteryNoEdit.text.toString().trim()
        val cityCode = cityCodeEdit.text.toString().trim().let {
            if (it.isEmpty()) "0755" else it
        }
        
        if (batteryNo.isEmpty()) {
            Toast.makeText(this, "请输入电池编号", Toast.LENGTH_SHORT).show()
            return
        }

        // 禁用输入和按钮
        setInputsEnabled(false)
        addButton.text = "保存中..."

        lifecycleScope.launch {
            try {
                // 保存配置
                withContext(Dispatchers.IO) {
                    val prefs = getSharedPreferences("BatteryWidgetPrefs", Context.MODE_PRIVATE)
                    val editor = prefs.edit()
                    
                    // 保存电池号和城市代码
                    editor.putString("batteryNo_$appWidgetId", batteryNo)
                    editor.putString("cityCode_$appWidgetId", cityCode)
                    
                    // 保存刷新间隔
                    val intervals = resources.getIntArray(R.array.refresh_intervals)
                    val selectedInterval = intervals[refreshIntervalSpinner.selectedItemPosition]
                    editor.putInt("refreshInterval_$appWidgetId", selectedInterval)

                    // 保存服务状态
                    editor.putBoolean("serviceLocalEnabled", serviceLocalSwitch.isChecked)
                    
                    editor.apply()

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
                    Toast.makeText(this@BatteryWidgetConfigureActivity, 
                        "保存失败：${e.message}", 
                        Toast.LENGTH_SHORT).show()
                    setInputsEnabled(true)
                    addButton.text = getString(R.string.add_widget)
                }
            }
        }
    }

    private fun setInputsEnabled(enabled: Boolean) {
        batteryNoEdit.isEnabled = enabled
        cityCodeEdit.isEnabled = enabled
        refreshIntervalSpinner.isEnabled = enabled
        addButton.isEnabled = enabled
    }

    private data class InitData(
        val batteryNo: String,
        val cityCode: String,
        val refreshIntervalPosition: Int,
        val refreshIntervals: Array<String>
    )
} 