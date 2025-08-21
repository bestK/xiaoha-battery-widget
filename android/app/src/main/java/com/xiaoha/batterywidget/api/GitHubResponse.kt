package com.xiaoha.batterywidget.api

/**
 * GitHub Release API 响应数据结构
 */
data class GitHubRelease(
    val tag_name: String,              // 版本标签，如 "v1.0.0"
    val name: String,                  // 发布名称
    val body: String,                  // 发布说明/更新日志
    val html_url: String,              // GitHub Release页面URL
    val published_at: String,          // 发布时间
    val prerelease: Boolean,           // 是否为预发布版本
    val assets: List<GitHubAsset>      // 发布资源列表（如APK文件）
)

/**
 * GitHub Release 资源文件信息
 */
data class GitHubAsset(
    val name: String,                  // 文件名
    val browser_download_url: String,  // 下载链接
    val size: Long,                    // 文件大小（字节）
    val content_type: String           // 文件类型
)
