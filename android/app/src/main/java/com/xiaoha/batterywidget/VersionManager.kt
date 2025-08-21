package com.xiaoha.batterywidget

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.appcompat.app.AlertDialog

import com.xiaoha.batterywidget.api.GitHubService
import com.xiaoha.batterywidget.api.GitHubRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class VersionManager(private val context: Context) {
    companion object {
        private const val TAG = "VersionManager"
        private const val GITHUB_API_BASE_URL = "https://api.github.com/"
        private const val REPO_OWNER = "bestK" // 替换为实际的GitHub用户名
        private const val REPO_NAME = "xiaoha-battery-widget" // 替换为实际的仓库名
        private const val PREF_LAST_CHECK_TIME = "last_version_check_time"
        private const val CHECK_INTERVAL_HOURS = 24 // 24小时检查一次
    }

    private val gitHubService: GitHubService by lazy {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(GITHUB_API_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpClient)
            .build()
            .create(GitHubService::class.java)
    }

    /**
     * 检查是否需要进行版本更新检查
     */
    fun shouldCheckForUpdate(): Boolean {
        val prefs = context.getSharedPreferences("version_prefs", Context.MODE_PRIVATE)
        val lastCheckTime = prefs.getLong(PREF_LAST_CHECK_TIME, 0)
        val currentTime = System.currentTimeMillis()
        val timeDiff = currentTime - lastCheckTime
        val intervalMillis = CHECK_INTERVAL_HOURS * 60 * 60 * 1000L
        
        return timeDiff >= intervalMillis
    }

    /**
     * 更新最后检查时间
     */
    private fun updateLastCheckTime() {
        val prefs = context.getSharedPreferences("version_prefs", Context.MODE_PRIVATE)
        prefs.edit().putLong(PREF_LAST_CHECK_TIME, System.currentTimeMillis()).apply()
    }

    /**
     * 获取当前应用版本名称
     */
    private fun getCurrentVersionName(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Failed to get version name", e)
            "1.0.0"
        }
    }

    /**
     * 比较版本号
     * @param current 当前版本
     * @param latest 最新版本
     * @return true如果latest版本更新
     */
    private fun isNewerVersion(current: String, latest: String): Boolean {
        return try {
            val currentParts = current.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
            val latestParts = latest.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
            
            val maxLength = maxOf(currentParts.size, latestParts.size)
            val currentNormalized = currentParts + List(maxLength - currentParts.size) { 0 }
            val latestNormalized = latestParts + List(maxLength - latestParts.size) { 0 }
            
            for (i in 0 until maxLength) {
                when {
                    latestNormalized[i] > currentNormalized[i] -> return true
                    latestNormalized[i] < currentNormalized[i] -> return false
                }
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error comparing versions: $current vs $latest", e)
            false
        }
    }

    /**
     * 检查更新（在后台执行）
     * 需要在调用方的协程作用域中执行
     */
    suspend fun checkForUpdate(): Triple<Boolean, String?, String?> {
        if (!shouldCheckForUpdate()) {
            Log.d(TAG, "Skip version check - too soon since last check")
            return Triple(false, null, null)
        }

        Log.d(TAG, "Checking for app updates...")
        return performVersionCheck()
    }

    /**
     * 强制检查更新（忽略时间限制）
     */
    suspend fun forceCheckForUpdate(): Triple<Boolean, String?, String?> {
        Log.d(TAG, "Force checking for app updates...")
        return performVersionCheck()
    }

    /**
     * 检查更新（回调版本，用于非协程环境）
     */
    fun checkForUpdateAsync(onResult: (Boolean, String?, String?) -> Unit) {
        if (!shouldCheckForUpdate()) {
            Log.d(TAG, "Skip version check - too soon since last check")
            onResult(false, null, null)
            return
        }

        Log.d(TAG, "Checking for app updates...")
        
        // 使用GlobalScope执行，但这需要调用方管理生命周期
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            val result = performVersionCheck()
            withContext(Dispatchers.Main) {
                onResult(result.first, result.second, result.third)
            }
        }
    }

    private suspend fun performVersionCheck(): Triple<Boolean, String?, String?> {
        try {
            val response = gitHubService.getLatestRelease(REPO_OWNER, REPO_NAME).execute()
            
            updateLastCheckTime() // 无论成功失败都更新检查时间
            
            if (response.isSuccessful) {
                val release = response.body()
                if (release != null && !release.prerelease) {
                    val currentVersion = getCurrentVersionName()
                    val latestVersion = release.tag_name
                    
                    Log.d(TAG, "Current version: $currentVersion, Latest version: $latestVersion")
                    
                    if (isNewerVersion(currentVersion, latestVersion)) {
                        Log.i(TAG, "New version available: $latestVersion")
                        return Triple(true, latestVersion, release.html_url)
                    } else {
                        Log.d(TAG, "App is up to date")
                    }
                } else {
                    Log.w(TAG, "No stable release found or response is null")
                }
            } else {
                Log.e(TAG, "GitHub API request failed: ${response.code()} ${response.message()}")
            }
            
            return Triple(false, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates", e)
            return Triple(false, null, null)
        }
    }

    /**
     * 显示更新对话框
     */
    fun showUpdateDialog(latestVersion: String, downloadUrl: String) {
        if (context !is androidx.appcompat.app.AppCompatActivity) {
            Log.w(TAG, "Context is not an AppCompatActivity, cannot show dialog")
            return
        }

        AlertDialog.Builder(context)
            .setTitle("🎉 发现新版本")
            .setMessage("发现新版本 $latestVersion，是否前往下载？\n\n点击「立即更新」将打开GitHub Release页面。")
            .setPositiveButton("立即更新") { _, _ ->
                openDownloadPage(downloadUrl)
            }
            .setNegativeButton("稍后提醒") { dialog, _ ->
                dialog.dismiss()
            }
            .setNeutralButton("忽略此版本") { dialog, _ ->
                // 可以在这里添加忽略特定版本的逻辑
                dialog.dismiss()
            }
            .setCancelable(true)
            .show()
    }

    /**
     * 打开下载页面
     */
    private fun openDownloadPage(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open download page", e)
            // 可以在这里添加Toast提示用户手动访问
        }
    }
}
