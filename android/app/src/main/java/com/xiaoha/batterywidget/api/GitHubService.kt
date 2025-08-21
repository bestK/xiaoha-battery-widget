package com.xiaoha.batterywidget.api

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * GitHub API 服务接口
 * 用于检查应用版本更新
 */
interface GitHubService {
    /**
     * 获取指定仓库的最新发布版本
     * @param owner GitHub用户名或组织名
     * @param repo 仓库名称
     * @return 最新发布版本信息
     */
    @GET("repos/{owner}/{repo}/releases/latest")
    fun getLatestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Call<GitHubRelease>
}
