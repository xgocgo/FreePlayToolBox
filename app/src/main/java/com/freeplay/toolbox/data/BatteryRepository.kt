package com.freeplay.toolbox.data

import com.freeplay.toolbox.core.Shell

data class BatteryInfo(
    val level: Int,
    val statusText: String,
    val healthText: String,
    val voltageV: Double,
    val tempC: Double,
    val technology: String,
    val chargeCounterMah: Double,
    val charging: Boolean,
    val powerSource: String,
)

/** 解析 dumpsys battery，输出电池状态 */
object BatteryRepository {

    suspend fun load(): BatteryInfo? = Shell.runAsync("dumpsys battery").out.let { parse(it) }

    fun parse(dump: String): BatteryInfo? {
        if (!dump.contains("Battery Service")) return null
        fun value(key: String): String? {
            val m = Regex("$key:\\s*(\\S+)").find(dump) ?: return null
            return m.groupValues[1]
        }
        val status = value("status")?.toIntOrNull() ?: 1
        val health = value("health")?.toIntOrNull() ?: 1
        val statusText = mapOf(
            1 to "未知", 2 to "充电中", 3 to "放电中",
            4 to "未充电", 5 to "已充满",
        )[status] ?: "未知"
        val healthText = mapOf(
            1 to "未知", 2 to "良好", 3 to "过热",
            4 to "已损坏", 5 to "过压", 6 to "低温", 7 to "其他",
        )[health] ?: "未知"
        val source = when {
            value("AC powered") == "true" -> "AC 电源"
            value("USB powered") == "true" -> "USB"
            value("Wireless powered") == "true" -> "无线"
            else -> "电池"
        }
        val counter = value("Charge counter")?.toLongOrNull() ?: 0L
        return BatteryInfo(
            level = value("level")?.toIntOrNull() ?: -1,
            statusText = statusText,
            healthText = healthText,
            voltageV = (value("voltage")?.toDoubleOrNull() ?: 0.0) / 1000.0,
            tempC = (value("temperature")?.toDoubleOrNull() ?: 0.0) / 10.0,
            technology = value("technology") ?: "-",
            chargeCounterMah = counter / 1000.0,
            charging = status == 2 || status == 5,
            powerSource = source,
        )
    }
}
