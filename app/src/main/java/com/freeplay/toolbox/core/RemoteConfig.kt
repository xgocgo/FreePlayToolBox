package com.freeplay.toolbox.core

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class Announcement(
    val id: String,
    val title: String,
    val body: String,
    val level: String = "info",
    val image: String = "",
)

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
    private const val KEY_WORKER = "ann_worker_url"
    private const val KEY_CACHE = "ann_cache"
    private const val KEY_EDIT_URL = "ann_edit_url"

    private const val REPO = "xgocgo/FreePlayToolBox"
    private const val BRANCH = "master"

    // ---------- 开关 ----------

    fun onlineEnabled(c: Context): Boolean =
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(KEY_ONLINE, true)

    fun announceEnabled(c: Context): Boolean = onlineEnabled(c)

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

    /**
     * 公告源优先级（按实测可达性排序）：
     * 1. api.github.com contents 接口 —— 返回 base64，需解码；国内多数网络可通
     * 2. jsDelivr + 时间戳 —— 绕过 CDN 缓存
     * 3. raw.githubusercontent —— 实时但国内常被阻断
     */
    private fun announceUrls(c: Context): List<String> {
        val bust = System.currentTimeMillis() / 60000
        val self = customSources(c).map { it.trimEnd('/') }
        return self + listOf(
            "https://gitee.com/zhy3860770417/toolbox-ann/raw/master/announcements.json?t=$bust",
            "https://api.github.com/repos/$REPO/contents/announcements.json?ref=$BRANCH",
            "https://cdn.jsdelivr.net/gh/$REPO@$BRANCH/announcements.json?t=$bust",
            "https://raw.githubusercontent.com/$REPO/$BRANCH/announcements.json",
        )
    }

    /**
     * 从混杂文本（如 QQ 收藏分享页的 HTML）里提取 JSON 对象。
     * 做法：定位 "items" 出现处，向前找最近的 '{'，再做花括号配平向后截取，
     * 逐个尝试解析，第一个成功且含 items 数组的即为结果。
     */
    private fun extractJson(raw: String): String? {
        val text = raw
            .replace("&quot;", "\"")
            .replace("&#34;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#39;", "'")

        var from = text.indexOf("\"items\"")
        var guard = 0
        while (from >= 0 && guard < 12) {
            guard++
            val start = text.lastIndexOf('{', from)
            if (start >= 0) {
                var depth = 0
                var inStr = false
                var esc = false
                var i = start
                while (i < text.length && i - start < 400_000) {
                    val ch = text[i]
                    when {
                        esc -> esc = false
                        ch == '\\' && inStr -> esc = true
                        ch == '"' -> inStr = !inStr
                        !inStr && ch == '{' -> depth++
                        !inStr && ch == '}' -> {
                            depth--
                            if (depth == 0) {
                                val candidate = text.substring(start, i + 1)
                                if (runCatching {
                                        JSONObject(candidate).optJSONArray("items") != null
                                    }.getOrDefault(false)
                                ) return candidate
                                break
                            }
                        }
                    }
                    i++
                }
            }
            from = text.indexOf("\"items\"", from + 1)
        }
        return null
    }

    /** GitHub contents 接口返回的是 base64 包裹的 JSON，这里统一解成纯文本 */
    private fun unwrapContent(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("{")) return trimmed
        return try {
            val o = JSONObject(trimmed)
            val b64 = o.optString("content", "")
            if (b64.isBlank()) return trimmed
            String(android.util.Base64.decode(b64.replace("\n", ""), android.util.Base64.DEFAULT))
        } catch (_: Throwable) {
            trimmed
        }
    }

    /** 用户填写的自定义源（每行一个，按顺序尝试） */
    fun customSources(c: Context): List<String> =
        (c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_WORKER, "") ?: "")
            .lines().map { it.trim() }.filter { it.isNotEmpty() }

    fun workerUrl(c: Context): String = customSources(c).firstOrNull() ?: ""

    fun setWorkerUrl(c: Context, url: String) =
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY_WORKER, url.trim()).apply()

    /** 保存最近一次成功拉取的公告，供离线时展示 */
    private fun saveCache(c: Context, list: List<Announcement>) {
        val arr = org.json.JSONArray()
        list.forEach { a ->
            arr.put(JSONObject().apply {
                put("id", a.id); put("title", a.title)
                put("body", a.body); put("level", a.level); put("image", a.image)
            })
        }
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY_CACHE, arr.toString()).apply()
    }

    /** 读取缓存（网络不可用时的兜底） */
    fun cachedAnnouncements(c: Context): List<Announcement> {
        val raw = c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_CACHE, null)
            ?: return emptyList()
        return runCatching {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Announcement(
                    id = o.optString("id"),
                    title = o.optString("title"),
                    body = o.optString("body"),
                    level = o.optString("level", "info"),
                    image = o.optString("image", ""),
                )
            }
        }.getOrDefault(emptyList())
    }

    /** 公告编辑页地址（点「打开编辑页」跳这里） */
    fun editUrl(c: Context): String =
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_EDIT_URL, "")?.trim()?.ifBlank { null }
            ?: "https://github.com/xgocgo/FreePlayToolBox/edit/master/announcements.json"

    fun setEditUrl(c: Context, url: String) =
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY_EDIT_URL, url.trim()).apply()

    fun readIds(c: Context): Set<String> =
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getStringSet(KEY_READ_IDS, emptySet()) ?: emptySet()

    /** 拉取公告；onlyUnread=true 时只返回未读（启动弹窗用），false 返回全部（首页常驻展示用） */
    suspend fun fetchAnnouncements(c: Context, onlyUnread: Boolean = true): List<Announcement> = withContext(Dispatchers.IO) {
        if (!announceEnabled(c)) return@withContext emptyList()
        val read = readIds(c)

        for (u in announceUrls(c)) {
            var text = httpGet(u, json = true) ?: continue
            text = unwrapContent(text)
            if (!text.trimStart().startsWith("{")) {
                text = extractJson(text) ?: continue
            }
            val all = runCatching {
                val root = JSONObject(text)
                val arr = root.optJSONArray("items") ?: return@runCatching emptyList<Announcement>()
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.getJSONObject(i)
                    val id = o.optString("id", "")
                    if (id.isBlank()) return@mapNotNull null
                    Announcement(
                        id = id,
                        title = o.optString("title", "公告"),
                        body = o.optString("body", ""),
                        level = o.optString("level", "info"),
                        image = o.optString("image", ""),
                    )
                }
            }.getOrNull() ?: continue

            // 成功拉到（哪怕是空列表）就整体覆盖缓存
            saveCache(c, all)
            return@withContext if (onlyUnread) all.filter { it.id !in read } else all
        }

        // 所有源都失败：回落本地缓存，没网也能看到上次公告
        val cached = cachedAnnouncements(c)
        if (cached.isEmpty()) emptyList()
        else if (onlyUnread) cached.filter { it.id !in read }
        else cached
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
