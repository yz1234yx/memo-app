package com.example.memo.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * GitHub Releases 升级检查。
 * 公共仓库模式：API 与资源下载均无需鉴权。
 */
object UpdateUtil {

    data class ReleaseInfo(
        val version: String,
        val apkUrl: String,
        val assetName: String,
        val releaseNotes: String
    )

    sealed class State {
        data object NotConfigured : State()
        data object Checking : State()
        data object UpToDate : State()
        data class Available(val info: ReleaseInfo) : State()
        data class Downloading(val percent: Int) : State()
        data class Ready(val apk: File, val info: ReleaseInfo) : State()
        data class Error(val message: String) : State()
    }

    /** 拉取最新 Release；仓库没有 Release、网络失败都会以 Error 返回。 */
    suspend fun check(repo: String): State = withContext(Dispatchers.IO) {
        if (repo.isBlank()) return@withContext State.NotConfigured
        try {
            val conn = open(
                "https://api.github.com/repos/$repo/releases/latest",
                accept = "application/vnd.github+json"
            )
            val code = conn.responseCode
            if (code == 404) return@withContext State.Error("仓库还没有发布任何版本")
            if (code != 200) return@withContext State.Error("GitHub 返回 HTTP $code")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val assets = json.getJSONArray("assets")
            var apkUrl: String? = null
            var apkName: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                val name = a.getString("name")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = a.getString("browser_download_url")
                    apkName = name
                    break
                }
            }
            if (apkUrl == null || apkName == null) {
                return@withContext State.Error("最新 Release 里没有 .apk 附件")
            }
            State.Available(
                ReleaseInfo(
                    version = json.getString("tag_name").removePrefix("v"),
                    apkUrl = apkUrl,
                    assetName = apkName,
                    releaseNotes = json.optString("body", "")
                )
            )
        } catch (e: Exception) {
            State.Error("检查更新失败：${e.message ?: e.javaClass.simpleName}")
        } finally {
            // HttpURLConnection 无需显式断开，异常路径下交给 GC
        }
    }

    /** 下载 APK 到应用缓存目录，回调进度百分比；自动重试 3 次，每次下载后做完整性校验。 */
    suspend fun download(
        context: Context,
        info: ReleaseInfo,
        onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        repeat(3) { attempt ->
            try {
                onProgress(0)
                val file = downloadOnce(context, info, onProgress)
                verifyApk(context, file)
                return@withContext file
            } catch (e: Exception) {
                lastError = e
                File(context.cacheDir, "update-${info.version}.apk").delete()
            }
        }
        throw java.io.IOException(
            "安装包下载失败（已重试 3 次）：${lastError?.message ?: "网络不稳定"}。请稍后在网络良好时重试。"
        )
    }

    private suspend fun downloadOnce(
        context: Context,
        info: ReleaseInfo,
        onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val dest = File(context.cacheDir, "update-${info.version}.apk")
        val conn = open(info.apkUrl, accept = "application/octet-stream")
        try {
            val total = conn.contentLengthLong
            if (conn.responseCode != 200) {
                throw java.io.IOException("下载源返回 HTTP ${conn.responseCode}")
            }
            conn.inputStream.use { input ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    var last = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        if (total > 0) {
                            val pct = (read * 100 / total).toInt()
                            if (pct != last) {
                                last = pct
                                onProgress(pct)
                            }
                        }
                    }
                    if (total > 0 && read != total) {
                        throw java.io.IOException("下载不完整（$read / $total 字节）")
                    }
                }
            }
            dest
        } finally {
            conn.disconnect()
        }
    }

    /** 完整性校验：ZIP 魔数 + PackageManager 能解析出同名包。 */
    private fun verifyApk(context: Context, file: File) {
        if (file.length() < 1024 * 1024) {
            throw java.io.IOException("安装包过小（${file.length()} 字节），疑似下载不完整")
        }
        file.inputStream().use { input ->
            val magic = ByteArray(2)
            if (input.read(magic) < 2 || magic[0] != 'P'.code.toByte() || magic[1] != 'K'.code.toByte()) {
                throw java.io.IOException("文件不是有效的 APK（缺少 ZIP 魔数）")
            }
        }
        val pinfo = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
        if (pinfo == null) {
            throw java.io.IOException("安装包无法解析，下载可能已损坏")
        }
        if (pinfo.packageName != context.packageName) {
            throw java.io.IOException("安装包内容与本应用不符（${pinfo.packageName}）")
        }
    }

    /** 拉起系统安装器；未授予「安装未知应用」时先跳设置页，返回后重试即可。 */
    fun install(context: Context, apk: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", apk
        )
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /** "安装未知应用"设置页。 */
    fun unknownSourceSettings(context: Context) {
        val intent = Intent(
            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** 版本号比较：按 '.' 分段取前导数字（"4-test" 按 4 算），1.10 > 1.9；无数字段按 0 处理。 */
    fun isNewer(remote: String, current: String): Boolean {
        fun seg(v: String) = v.removePrefix("v").removePrefix("V").split('.')
            .map { part -> Regex("^\\d+").find(part?.trim() ?: "")?.value?.toIntOrNull() ?: 0 }
        val r = seg(remote)
        val c = seg(current)
        for (i in 0 until maxOf(r.size, c.size)) {
            val ri = r.getOrNull(i) ?: 0
            val ci = c.getOrNull(i) ?: 0
            if (ri != ci) return ri > ci
        }
        return false
    }

    private fun open(url: String, accept: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("Accept", accept)
        conn.setRequestProperty("User-Agent", "MemoApp-Updater")
        return conn
    }
}
