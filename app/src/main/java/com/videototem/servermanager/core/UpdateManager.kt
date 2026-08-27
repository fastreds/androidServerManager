package com.videototem.servermanager.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.videototem.servermanager.log.LogStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val tag: String,
    val versionName: String,
    val apkUrl: String,
    val apkName: String,
    val body: String
)

object UpdateManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun checkForUpdate(ctx: Context, repo: String): UpdateInfo? = withContext(Dispatchers.IO) {
        if (repo.isBlank() || !repo.contains("/")) return@withContext null
        try {
            val req = Request.Builder()
                .url("https://api.github.com/repos/$repo/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "androidServerManager")
                .build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) {
                LogStore.append("update", "GitHub API ${resp.code}")
                return@withContext null
            }
            val json = JSONObject(resp.body?.string() ?: return@withContext null)
            val tag = json.optString("tag_name", "").trim()
            if (tag.isEmpty()) return@withContext null

            val currentName = ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "1.0"
            if (tag.removePrefix("v") == currentName.removePrefix("v")) return@withContext null
            if (!isNewer(tag, currentName)) return@withContext null

            val assets = json.optJSONArray("assets") ?: return@withContext null
            var apkUrl = ""
            var apkName = ""
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                val n = a.optString("name", "")
                if (n.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = a.optString("browser_download_url", "")
                    apkName = n
                    break
                }
            }
            if (apkUrl.isEmpty()) return@withContext null
            UpdateInfo(tag, tag, apkUrl, apkName, json.optString("body", ""))
        } catch (e: Exception) {
            LogStore.append("update", "check error: ${e.message}")
            null
        }
    }

    private fun isNewer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.removePrefix("v").split(".", "-").mapNotNull { it.toIntOrNull() }
        val r = parts(remote)
        val l = parts(local)
        val n = maxOf(r.size, l.size)
        for (i in 0 until n) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv != lv) return rv > lv
        }
        return false
    }

    suspend fun downloadAndInstall(ctx: Context, apkUrl: String, onProgress: (String) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        try {
            onProgress("descargando…")
            val req = Request.Builder().url(apkUrl).header("User-Agent", "androidServerManager").build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful || resp.body == null) {
                LogStore.append("update", "descarga fallo ${resp.code}")
                return@withContext false
            }
            val tmp = File(ctx.cacheDir, "update.apk")
            resp.body!!.byteStream().use { input ->
                tmp.outputStream().use { out -> input.copyTo(out) }
            }
            if (!tmp.exists() || tmp.length() < 1024 * 100) {
                LogStore.append("update", "apk descargado corrupto")
                return@withContext false
            }
            onProgress("instalando…")
            if (RootShell.rootAvailable()) {
                val pub = File("/data/local/tmp/update.apk")
                RootShell.exec("cp ${Cmd.sh(tmp.absolutePath)} ${Cmd.sh(pub.absolutePath)} && chmod 644 ${Cmd.sh(pub.absolutePath)}")
                val r = RootShell.exec("pm install -r ${Cmd.sh(pub.absolutePath)} 2>&1")
                val ok = r.isSuccess || r.out.any { it.contains("Success", ignoreCase = true) }
                LogStore.append("update", "pm install: ${RootShell.resultText(r).take(200)}")
                return@withContext ok
            }
            val uri = try {
                FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", tmp)
            } catch (_: Exception) {
                Uri.fromFile(tmp)
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(intent)
            true
        } catch (e: Exception) {
            LogStore.append("update", "install error: ${e.message}")
            false
        }
    }
}
