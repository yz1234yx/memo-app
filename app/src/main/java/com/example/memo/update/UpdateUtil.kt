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

    /** 下载 APK 到应用缓存目录，回调进度百分比。 */
    suspend fun download(
        context: Context,
        info: ReleaseInfo,
        onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val dest = File(context.cacheDir, "update-${info.version}.apk")
        val conn = open(info.apkUrl, accept = "application/octet-stream")
        val total = conn.contentLengthLong
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
            }
        }
        dest
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

    /** 版本号比较：按 '.' 分段取数字，1.10 > 1.9；无法解析时按字符串不等处理。 */
    fun isNewer(remote: String, current: String): Boolean {
        val r = remote.removePrefix("v").removePrefix("V").split('.')
        val c = current.removePrefix("v").removePrefix("V").split('.')
        for (i in 0 until maxOf(r.size, c.size)) {
            val ri = r.getOrNull(i)?.toIntOrNull() ?: 0
            val ci = c.getOrNull(i)?.toIntOrNull() ?: 0
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
