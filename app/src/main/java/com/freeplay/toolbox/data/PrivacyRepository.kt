package com.freeplay.toolbox.data

import com.freeplay.toolbox.core.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PrivacyRecord(
    val pkg: String,
    val op: String,
    val lastAccess: String,
)

/**
 * 隐私审计：从 dumpsys appops 中提取敏感权限的最近访问记录。
 * 不同 ROM 输出格式差异较大，解析失败时返回空并附带说明。
 */
object PrivacyRepository {

    private val SENSITIVE = listOf(
        "camera" to "相机",
        "microphone" to "麦克风",
        "record_audio" to "录音",
        "fine_location" to "精确位置",
        "coarse_location" to "大致位置",
        "activity_recognition" to "身体活动",
        "body_sensors" to "身体传感器",
    )

    suspend fun audit(): List<PrivacyRecord> = withContext(Dispatchers.IO) {
        val dump = Shell.run("dumpsys appops").out
        if (dump.isBlank()) return@withContext emptyList()

        val records = mutableListOf<PrivacyRecord>()
        var currentPkg: String? = null
        var currentOp: String? = null

        val pkgRe = Regex("""Package ([\w.]+)""")
        val opRe = Regex("""(camera|microphone|record_audio|fine_location|coarse_location|activity_recognition|body_sensors)""", RegexOption.IGNORE_CASE)
        val accessRe = Regex("""([+-]?\d+\w*[dhms]\s*\d*[dhms]?\s*ago)""", RegexOption.IGNORE_CASE)

        for (line in dump.lines()) {
            pkgRe.find(line)?.let {
                currentPkg = it.groupValues[1]
                currentOp = null
            }
            opRe.find(line)?.let { m ->
                val op = m.groupValues[1].lowercase()
                if (SENSITIVE.any { it.first == op }) {
                    currentOp = op
                }
            }
            if (currentPkg != null && currentOp != null) {
                val t = accessRe.find(line)?.groupValues?.get(1)
                if (t != null) {
                    val label = SENSITIVE.firstOrNull { it.first == currentOp!! }?.second ?: currentOp!!
                    records.add(PrivacyRecord(currentPkg!!, label, t.trim()))
                    currentOp = null
                }
            }
        }
        records.distinctBy { it.pkg to it.op }.take(120)
    }

    /** 该 ROM 是否可能提供访问记录 */
    suspend fun supported(): Boolean = withContext(Dispatchers.IO) {
        Shell.run("dumpsys appops").out.contains("Package ")
    }
}
