package com.freeplay.toolbox.core

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class Announcement(val id: String, val title: String, val body: String)

data class ReleaseInfo(
    val tag: String,
    val versionName: String,
    val notes: String,
    val downloadUrl: String,
    val digest: String?,
    val size: Long,
)

/**
 * 远程配置通道。
 *
 * 网络行为约束（隐私声明里已写明）：
 * - 只做两件事：GET 公告 JSON、GET 更新信息
 * - 不发送任何设备标识；UUID 仅在启用计数器时使用，且可完全关闭
 * - 全部超时 6 秒，失败静默
 */
object RemoteConfig {

    private const val PREF = "toolbox_prefs"
    const val KEY_ONLINE = "net_online"          // 总开关，默认开
    const val KEY_ANNOUNCE = "net_announce"      // 公告
    const val KEY_UPDATE = "net_update"          // 更新检查
    private const val KEY_UUID = "install_id"
    private const val KEY_READ_IDS = "read_announce_ids"

    private const val REPO = "xgocgo/FreePlayToolBox"
    private const val BRANCH = "master"

    // ---------- 开关 ----------

    fun onlineEnabled(c: Context): Boolean =
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(KEY_ONLINE, true)

    fun announceEnabled(c: Context): Boolean =
        onlineEnabled(c) && c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(KEY_ANNOUNCE, true)

    fun updateEnabled(c: Context): Boolean =
        onlineEnabled(c) && c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(KEY_UPDATE, true)

    fun setOnline(c: Context, on: Boolean) =
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(KEY_ONLINE, on).apply()

    fun setAnnounce(c: Context, on: Boolean) =
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(KEY_ANNOUNCE, on).apply()

    fun setUpdate(c: Context, on: Boolean) =
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(KEY_UPDATE, on).apply()

    // ---------- 安装标识（仅用于计数，可关闭） ----------

    fun installId(c: Context): String {
        val sp = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val existing = sp.getString(KEY_UUID, null)
        if (existing != null) return existing
        val id = UUID.randomUUID().toString()
        sp.edit().putString(KEY_UUID, id).apply()
        return id
    }

    // ---------- 公告 ----------

    private fun announceUrls(): List<String> = listOf(
        "https://cdn.jsdelivr.net/gh/$REPO@$BRANCH/announcements.json",
        "https://raw.githubusercontent.com/$REPO/$BRANCH/announcements.json",
        "https://gcore.jsdelivr.net/gh/$REPO@$BRANCH/announcements.json",
    )

    /** 拉取未读公告；失败返回空列表（静默） */
    suspend fun fetchAnnouncements(c: Context): List<Announcement> = withContext(Dispatchers.IO) {
        if (!announceEnabled(c)) return@withContext emptyList()
        val readIds = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getStringSet(KEY_READ_IDS, emptySet()) ?: emptySet()

        for (u in announceUrls()) {
            val text = httpGet(u) ?: continue
            val items = runCatching {
                val root = JSONObject(text)
                val arr = root.optJSONArray("items") ?: return@runCatching emptyList<Announcement>()
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.getJSONObject(i)
                    val id = o.optString("id", "")
                    if (id.isBlank() || id in readIds) return@mapNotNull null
                    Announcement(
                        id = id,
                        title = o.optString("title", "公告"),
                        body = o.optString("body", ""),
                    )
                }
            }.getOrDefault(emptyList())
            return@withContext items
        }
        emptyList()
    }

    fun markRead(c: Context, ids: List<String>) {
        val sp = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val old = sp.getStringSet(KEY_READ_IDS, emptySet()) ?: emptySet()
        sp.edit().putStringSet(KEY_READ_IDS, old + ids).apply()
    }

    // ---------- 在线更新 ----------

    suspend fun latestRelease(c: Context): ReleaseInfo? = withContext(Dispatchers.IO) {
        if (!updateEnabled(c)) return@withContext null
        val text = httpGet("https://api.github.com/repos/$REPO/releases/latest", json = true) ?: return@withContext null
        runCatching {
            val o = JSONObject(text)
            val tag = o.optString("tag_name", "")
            val assets = o.optJSONArray("assets")
            val a = if (assets != null && assets.length() > 0) assets.getJSONObject(0) else null
            ReleaseInfo(
                tag = tag,
                versionName = tag.removePrefix("v"),
                notes = o.optString("body", ""),
                downloadUrl = a?.optString("browser_download_url", "") ?: "",
                digest = a?.optString("digest", "")?.removePrefix("sha256:")?.takeIf { it.isNotBlank() },
                size = a?.optLong("size", 0L) ?: 0L,
            ).takeIf { it.versionName.isNotBlank() && it.downloadUrl.isNotBlank() }
        }.getOrNull()
    }

    /** 当前版本是否低于远程版本 */
    fun isNewer(remote: String, current: String): Boolean {
        fun parts(v: String) = v.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
        val a = parts(remote)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** 下载地址的多源候选：GitHub 直连 + 国内镜像前缀 */
    fun downloadCandidates(original: String): List<String> = listOf(
        original,
        "https://ghfast.top/$original",
        "https://gh-proxy.com/$original",
        "https://ghproxy.net/$original",
        "https://hub.gitmirror.com/$original",
    )

    fun selfVersion(c: Context): String = try {
        c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: "0"
    } catch (_: Throwable) {
        "0"
    }

    // ---------- HTTP ----------

    private fun httpGet(url: String, json: Boolean = false): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "FreePlayToolBox")
                if (json) setRequestProperty("Accept", "application/vnd.github+json")
            }
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    /** 下载到本地，返回文件路径；支持镜像回落 */
    suspend fun download(c: Context, candidates: List<String>, fileName: String): String? =
        withContext(Dispatchers.IO) {
            val dir = java.io.File("/sdcard/Download/DSH-Go")
            if (!dir.exists()) dir.mkdirs()
            val out = java.io.File(dir, fileName)
            for (u in candidates) {
                var conn: HttpURLConnection? = null
                try {
                    conn = (URL(u).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 10000
                        readTimeout = 30000
                        instanceFollowRedirects = true
                        setRequestProperty("User-Agent", "FreePlayToolBox")
                    }
                    if (conn.responseCode !in 200..299) continue
                    conn.inputStream.use { input ->
                        out.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (out.length() > 100_000) return@withContext out.absolutePath
                } catch (_: Throwable) {
                    // 换下一个源
                } finally {
                    runCatching { conn?.disconnect() }
                }
            }
            null
        }

    fun sha256(path: String): String? = try {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        java.io.File(path).inputStream().use { ins ->
            val buf = ByteArray(8192)
            while (true) {
                val n = ins.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    } catch (_: Throwable) {
        null
    }

    /** 触发系统安装 */
    fun installApk(c: Context, path: String): Boolean = try {
        val f = java.io.File(path)
        val uri = androidx.core.content.FileProvider.getUriForFile(
            c, "${c.packageName}.fileprovider", f,
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        c.startActivity(intent)
        true
    } catch (_: Throwable) {
        false
    }

    fun canInstallPackages(c: Context): Boolean = try {
        if (android.os.Build.VERSION.SDK_INT >= 26) c.packageManager.canRequestPackageInstalls()
        else true
    } catch (_: PackageManager.NameNotFoundException) {
        true
    }
}
