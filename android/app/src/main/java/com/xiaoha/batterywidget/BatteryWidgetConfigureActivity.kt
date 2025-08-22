package com.xiaoha.batterywidget

import android.Manifest
import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.xiaoha.batterywidget.api.BatteryService
import com.xiaoha.batterywidget.views.TagView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class BatteryWidgetConfigureActivity : AppCompatActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    // 通知权限请求
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "通知权限已授予", Toast.LENGTH_SHORT).show()
        } else {
            AlertDialog.Builder(this)
                .setTitle("通知权限")
                .setMessage("为了及时通知您电池电量的变化，请在设置中开启通知权限。")
                .setPositiveButton("去设置") { _, _ ->
                    // 打开应用设置页面
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    }
                    startActivity(intent)
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }
    private lateinit var batteryNoEdit: EditText
    private lateinit var cityCodeEdit: EditText
    private lateinit var tokenEdit: EditText
    private lateinit var baseUrlEdit: EditText
    private lateinit var refreshIntervalSpinner: Spinner
    private lateinit var addButton: Button
    private lateinit var testButton: TagView
    private lateinit var checkUpdateButton: TagView
    private lateinit var testResultContainer: LinearLayout

    private lateinit var testResultText: TextView
    private lateinit var copyLogButton: TagView
    private lateinit var clearLogButton: TagView
    private lateinit var notificationSwitch: androidx.appcompat.widget.SwitchCompat

    private lateinit var versionManager: VersionManager


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

        // 初始化版本管理器
        versionManager = VersionManager(this)

        // 检查并请求通知权限
        checkAndRequestNotificationPermission()

        // 在后台初始化
        lifecycleScope.launch {
            try {
                // 在后台线程准备数据
                val initData = withContext(Dispatchers.Default) {
                    val prefs = getSharedPreferences("BatteryWidgetPrefs", MODE_PRIVATE)

                    // 读取保存的配置
                    val batteryNo = prefs.getString("batteryNo", "")
                    val cityCode = prefs.getString("cityCode", "0755")
                    val token = prefs.getString("token", "")
                    val baseUrl = prefs.getString("baseUrl", "https://xiaoha.linkof.link/")
                    val refreshInterval = prefs.getInt("refreshInterval", 5)

                    val refreshIntervals = resources.getStringArray(R.array.refresh_intervals)
                    val intervals = resources.getIntArray(R.array.refresh_interval_values)
                    val position = intervals.indexOf(refreshInterval)

                    // 返回配置数据
                    InitData(
                        batteryNo ?: "",
                        cityCode ?: "0755",
                        token ?: "",
                        baseUrl ?: "https://xiaoha.linkof.link/",
                        position,
                        refreshIntervals
                    )
                }

                // 如果initData为null，说明已经自动配置并关闭了活动
                if (initData != null) {
                    // 在主线程更新UI
                    withContext(Dispatchers.Main) {
                        setupViews(initData)

                        // 检查版本更新
                        checkForUpdates()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@BatteryWidgetConfigureActivity,
                        "初始化失败：${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
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
        testButton = findViewById(R.id.test_button)
        checkUpdateButton = findViewById(R.id.check_update_button)
        testResultContainer = findViewById(R.id.test_result_container)

        testResultText = findViewById<TextView>(R.id.test_result_text).apply {
            // 设置触摸监听器，允许内部滚动
            setOnTouchListener { v, event ->
                // 告诉父视图不要拦截触摸事件
                v.parent.requestDisallowInterceptTouchEvent(true)
                // 返回false以允许正常的触摸事件处理
                false
            }
        }
        copyLogButton = findViewById<TagView>(R.id.copy_log_button)
        clearLogButton = findViewById<TagView>(R.id.clear_log_button)
        notificationSwitch = findViewById(R.id.notification_switch)

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

        // 设置通知开关状态
        notificationSwitch.isChecked =
            NotificationManager.isNotificationEnabled(this, initData.batteryNo)
        notificationSwitch.setOnCheckedChangeListener { _, isChecked ->
            NotificationManager.setNotificationEnabled(this, initData.batteryNo, isChecked)

            // 如果打开通知，发送测试通知
            if (isChecked) {
                val notificationManager = NotificationManager(this)
                notificationManager.showBatteryUpdateNotification(
                    initData.batteryNo,
                    100, // 测试用100%电量
                    SimpleDateFormat("M/d HH:mm", Locale.CHINA).format(Date()), // 当前时间
                    true
                )
                Toast.makeText(this, "已发送测试通知", Toast.LENGTH_SHORT).show()
            }
        }

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

        // 设置测试按钮点击事件
        testButton.setOnClickListener {
            testApiConnection()
        }

        // 设置检查更新按钮点击事件
        checkUpdateButton.setOnClickListener {
            manualCheckForUpdates()
        }

        // 设置复制日志按钮点击事件
        copyLogButton.setOnClickListener {
            copyLogToClipboard()
        }

        // 设置清空日志按钮点击事件
        clearLogButton.setOnClickListener {
            clearLog()
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
                        // 保存基本配置
                        putString("batteryNo", batteryNo)
                        putString("cityCode", cityCode)
                        putString("token", token)
                        putString("baseUrl", baseUrlEdit.text.toString().trim())

                        // 保存刷新间隔
                        val intervals = resources.getIntArray(R.array.refresh_interval_values)
                        val selectedInterval =
                            intervals[refreshIntervalSpinner.selectedItemPosition]
                        putInt("refreshInterval", selectedInterval)

                        // 保存通知设置
                        putBoolean("notification_enabled", notificationSwitch.isChecked)

                        // 保存配置版本号，用于后续配置格式升级
                        putInt("configVersion", 1)

                        // 立即写入磁盘
                        commit()
                    }

                    // 更新小部件
                    val appWidgetManager =
                        AppWidgetManager.getInstance(this@BatteryWidgetConfigureActivity)
                    val widget = BatteryWidget()
                    widget.onUpdate(
                        this@BatteryWidgetConfigureActivity,
                        appWidgetManager,
                        intArrayOf(appWidgetId)
                    )
                }

                // 设置结果并关闭活动
                val resultValue =
                    Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
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
        testButton.isEnabled = enabled
        checkUpdateButton.isEnabled = enabled
        copyLogButton.isEnabled = enabled
        clearLogButton.isEnabled = enabled
    }

    private fun testApiConnection() {
        val batteryNo = batteryNoEdit.text.toString().trim()
        val token = tokenEdit.text.toString().trim()
        val baseUrl = baseUrlEdit.text.toString().trim().let {
            if (it.isEmpty()) "https://xiaoha.linkof.link/" else it
        }

        if (batteryNo.isEmpty()) {
            Toast.makeText(this, "请输入电池编号", Toast.LENGTH_SHORT).show()
            return
        }

        if (token.isEmpty()) {
            Toast.makeText(this, "请输入token", Toast.LENGTH_SHORT).show()
            return
        }

        // 显示测试结果区域
        testResultContainer.visibility = View.VISIBLE
        testResultText.text = "开始测试连接...\n"

        // 禁用输入
        setInputsEnabled(false)
        testButton.text = "测试中..."
        testButton.type = TagView.TagType.WARNING

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                appendTestResult("=== 开始API测试 ===")
                appendTestResult("电池编号: $batteryNo")
                appendTestResult("基础URL: $baseUrl")
                appendTestResult("Token: ${token.take(20)}...")
                appendTestResult("")

                // 创建API服务
                val loggingInterceptor = HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }
                val okHttpClient = OkHttpClient.Builder()
                    .addInterceptor(loggingInterceptor)
                    .build()

                val retrofit = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(okHttpClient)
                    .build()

                val apiService = retrofit.create(BatteryService::class.java)

                // 步骤1: 获取预处理参数
                appendTestResult("步骤1: 获取预处理参数")
                appendTestResult("请求URL: ${baseUrl}preparams?batteryNo=$batteryNo")
                appendTestResult("请求方法: POST")
                appendTestResult("请求Body: $token")

                val tokenRequestBody = token.toRequestBody("text/plain".toMediaType())
                val preparamsResponse = withTimeout(10000) {
                    apiService.getPreparams(batteryNo, tokenRequestBody).awaitResponse()
                }

                if (preparamsResponse == null || !preparamsResponse.isSuccessful) {
                    throw Exception("预处理参数请求失败: ${preparamsResponse?.code()} ${preparamsResponse?.message()}")
                }

                val preparamsData = preparamsResponse.body()?.data
                if (preparamsData == null) {
                    throw Exception("预处理参数响应数据无效")
                }

                appendTestResult("响应状态码: ${preparamsResponse.code()}")
                appendTestResult("响应内容: ${preparamsResponse.body()}")
                appendTestResult("获取到的URL: ${preparamsData.url}")
                appendTestResult("获取到的Body长度: ${preparamsData.body.length}")
                appendTestResult("获取到的Headers: ${preparamsData.headers}")
                appendTestResult("")

                // 步骤2: 使用预处理参数获取电池数据
                appendTestResult("步骤2: 获取电池数据")
                appendTestResult("请求URL: ${preparamsData.url}")
                appendTestResult("请求方法: POST")
                appendTestResult("请求Body长度: ${preparamsData.body.length}")

                val batteryRequestBody =
                    preparamsData.body.toRequestBody("application/json".toMediaType())
                val batteryResponse = withTimeout(10000) {
                    // 创建新的OkHttpClient，设置headers
                    val batteryClient = OkHttpClient.Builder()
                        .addInterceptor { chain ->
                            val originalRequest = chain.request()
                            val newRequestBuilder = originalRequest.newBuilder()

                            // 添加从preparams响应中获取的headers
                            preparamsData.headers.forEach { (key, value) ->
                                newRequestBuilder.addHeader(key, value)
                                appendTestResult("添加Header: $key = $value")
                            }

                            chain.proceed(newRequestBuilder.build())
                        }
                        .addInterceptor(loggingInterceptor)
                        .build()

                    // 创建新的retrofit实例用于调用不同的base URL
                    val batteryRetrofit = Retrofit.Builder()
                        .baseUrl("https://dummy.base.url/")
                        .addConverterFactory(GsonConverterFactory.create())
                        .client(batteryClient)
                        .build()
                    val batteryService = batteryRetrofit.create(BatteryService::class.java)
                    batteryService.getBatteryData(preparamsData.url, batteryRequestBody)
                        .awaitResponse()
                }

                if (batteryResponse == null || !batteryResponse.isSuccessful) {
                    throw Exception("电池数据请求失败: ${batteryResponse?.code()} ${batteryResponse?.message()}")
                }

                val encryptedData = batteryResponse.body()
                if (encryptedData == null) {
                    throw Exception("电池数据响应为空")
                }

                // 一次性读取响应数据，避免重复读取
                val encryptedBytes = try {
                    encryptedData.bytes()
                } catch (e: Exception) {
                    appendTestResult("读取响应数据失败: ${e.message}")
                    throw Exception("读取电池数据失败: ${e.message}")
                }

                appendTestResult("响应状态码: ${batteryResponse.code()}")
                appendTestResult("响应Content-Type: ${batteryResponse.headers()["Content-Type"]}")
                appendTestResult("响应Content-Length: ${batteryResponse.headers()["Content-Length"]}")
                appendTestResult("实际数据长度: ${encryptedBytes.size} bytes")

                // 显示数据的十六进制预览（前32字节）
                val preview = encryptedBytes.take(32).joinToString("") { "%02x".format(it) }
                appendTestResult("数据预览(hex): $preview${if (encryptedBytes.size > 32) "..." else ""}")
                appendTestResult("")

                // 步骤3: 解码电池数据
                appendTestResult("步骤3: 解码电池数据")
                appendTestResult("请求URL: ${baseUrl}decode")
                appendTestResult("请求方法: POST")
                appendTestResult("请求Body: 二进制数据 (${encryptedBytes.size} bytes)")

                val decodeRequestBody =
                    encryptedBytes.toRequestBody("application/octet-stream".toMediaType())
                val decodeResponse = withTimeout(10000) {
                    apiService.decodeBatteryData(decodeRequestBody).awaitResponse()
                }

                if (decodeResponse == null || !decodeResponse.isSuccessful) {
                    throw Exception("解码请求失败: ${decodeResponse?.code()} ${decodeResponse?.message()}")
                }

                val decodeData = decodeResponse.body()?.data?.data
                if (decodeData == null || decodeData.bindBatteries.isEmpty()) {
                    throw Exception("解码响应数据无效或电池数据为空")
                }

                val batteryInfo = decodeData.bindBatteries[0]

                appendTestResult("响应状态码: ${decodeResponse.code()}")
                appendTestResult("解码响应: ${decodeResponse.body()}")
                appendTestResult("")
                appendTestResult("=== 解析结果 ===")
                appendTestResult("电池电量: ${batteryInfo.batteryLife}%")
                appendTestResult("报告时间: ${batteryInfo.reportTime}")

                // 解析时间格式 (reportTime是时间戳)
                val reportTime = try {
                    // 尝试解析为时间戳（毫秒）
                    Date(batteryInfo.reportTime.toLong())
                } catch (e: Exception) {
                    try {
                        // 如果不是时间戳，尝试解析为ISO格式
                        SimpleDateFormat(
                            "yyyy-MM-dd'T'HH:mm:ss",
                            Locale.CHINA
                        ).parse(batteryInfo.reportTime)
                            ?: Date()
                    } catch (e2: Exception) {
                        appendTestResult("时间解析失败: ${e.message}")
                        Date()
                    }
                }

                val formattedTime = SimpleDateFormat("M/d HH:mm", Locale.CHINA).format(reportTime)
                appendTestResult("格式化时间: $formattedTime")
                appendTestResult("")
                appendTestResult("✅ 测试完成，接口调用成功！")

                // 如果通知开关打开，发送测试通知
                if (notificationSwitch.isChecked) {
                    val notificationManager =
                        NotificationManager(this@BatteryWidgetConfigureActivity)
                    notificationManager.showBatteryUpdateNotification(
                        batteryNo,
                        batteryInfo.batteryLife,
                        formattedTime,
                        true
                    )
                    appendTestResult("已发送测试通知")
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@BatteryWidgetConfigureActivity,
                        "测试成功！",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: Exception) {
                val errorMessage = when (e) {
                    is java.net.UnknownHostException -> "无法连接到服务器，请检查网络连接或服务器地址是否正确"
                    is java.net.SocketTimeoutException -> "连接服务器超时，请稍后重试"
                    is retrofit2.HttpException -> "服务器返回错误: ${e.code()}"
                    is java.io.IOException -> "网络错误: ${e.message}"
                    else -> "未知错误: ${e.message}"
                }

                appendTestResult("❌ 测试失败: $errorMessage")
                appendTestResult("错误类型: ${e.javaClass.simpleName}")
                if (e !is java.net.UnknownHostException) {  // 对于DNS错误不显示堆栈
                    appendTestResult("错误详情: ${e.stackTraceToString()}")
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@BatteryWidgetConfigureActivity,
                        errorMessage,
                        Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    setInputsEnabled(true)
                    testButton.type = TagView.TagType.INFO
                    testButton.text = ""
                }
            }
        }
    }

    private fun appendTestResult(text: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            testResultText.append("$text\n")
            // 自动滚动到底部
            testResultText.post {
                val scrollAmount =
                    testResultText.layout.getLineTop(testResultText.lineCount) - testResultText.height
                if (scrollAmount > 0) {
                    testResultText.scrollTo(0, scrollAmount)
                }
            }
        }
    }

    private fun copyLogToClipboard() {
        val logContent = testResultText.text.toString()
        if (logContent.isEmpty()) {
            Toast.makeText(this, "没有日志可复制", Toast.LENGTH_SHORT).show()
            return
        }

        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText("测试日志", logContent)
        clipboardManager.setPrimaryClip(clipData)

        Toast.makeText(this, "日志已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }

    private fun clearLog() {
        testResultText.text = ""
        Toast.makeText(this, "日志已清空", Toast.LENGTH_SHORT).show()
    }

    private fun checkForUpdates() {
        lifecycleScope.launch {
            try {
                val (hasUpdate, latestVersion, downloadUrl) = withContext(Dispatchers.IO) {
                    versionManager.checkForUpdate()
                }

                if (hasUpdate && latestVersion != null && downloadUrl != null) {
                    versionManager.showUpdateDialog(latestVersion, downloadUrl)
                }
            } catch (e: Exception) {
                Log.e("VersionCheck", "Error checking for updates", e)
                // 版本检查失败不应该影响主要功能，所以这里只记录日志
            }
        }
    }

    private fun manualCheckForUpdates() {
        lifecycleScope.launch {
            try {
                Toast.makeText(
                    this@BatteryWidgetConfigureActivity,
                    "正在检查更新...",
                    Toast.LENGTH_SHORT
                ).show()

                val (hasUpdate, latestVersion, downloadUrl) = withContext(Dispatchers.IO) {
                    versionManager.forceCheckForUpdate()
                }

                if (hasUpdate && latestVersion != null && downloadUrl != null) {
                    versionManager.showUpdateDialog(latestVersion, downloadUrl)
                } else {
                    Toast.makeText(
                        this@BatteryWidgetConfigureActivity,
                        "当前已是最新版本",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e("VersionCheck", "Error checking for updates", e)
                Toast.makeText(
                    this@BatteryWidgetConfigureActivity,
                    "检查更新失败",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
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

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // 已经有权限，不需要做任何事
                }

                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    // 用户之前拒绝过，显示解释对话框
                    AlertDialog.Builder(this)
                        .setTitle("需要通知权限")
                        .setMessage("为了及时通知您电池电量的变化，我们需要通知权限。")
                        .setPositiveButton("授予权限") { _, _ ->
                            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        .setNegativeButton("取消", null)
                        .show()
                }

                else -> {
                    // 直接请求权限
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
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