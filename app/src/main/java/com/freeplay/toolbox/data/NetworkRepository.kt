package com.freeplay.toolbox.data

import com.freeplay.toolbox.core.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class NetInfo(
    val ip: String,
    val gateway: String,
    val dnsMode: String,
    val dnsHost: String,
    val dnsServers: String,
    val wifiName: String,
)

object NetworkRepository {

    suspend fun load(): NetInfo = withContext(Dispatchers.IO) {
        val ipOut = Shell.run("ip -4 addr show wlan0").out
        val ip = Regex("inet (\\d+\\.\\d+\\.\\d+\\.\\d+)/").find(ipOut)?.groupValues?.get(1) ?: "-"

        val routeOut = Shell.run("ip route").out
        val gw = Regex("default via (\\d+\\.\\d+\\.\\d+\\.\\d+)").find(routeOut)?.groupValues?.get(1) ?: "-"

        val mode = Shell.run("settings get global private_dns_mode").out.trim().ifBlank { "-" }
        val host = Shell.run("settings get global private_dns_specifier").out.trim().let {
            if (it == "null") "-" else it.ifBlank { "-" }
        }

        val dns = Shell.run("getprop net.dns1").out.trim().let {
            if (it.isBlank()) {
                val fromProp = Shell.run("getprop | grep dns").out.lines()
                    .mapNotNull { Regex("\\[([\\d.]+)\\]").find(it)?.groupValues?.get(1) }
                    .distinct().take(2)
                if (fromProp.isEmpty()) "-" else fromProp.joinToString(", ")
            } else it
        }

        val ssid = Shell.run("dumpsys wifi").out.lines()
            .firstOrNull { it.contains("SSID:") }
            ?.substringAfter("SSID:")?.substringBefore(",")?.trim()
            ?: "-"

        NetInfo(ip, gw, mode, host, dns, ssid)
    }

    /** 私有 DNS 模式：off / opportunistic / hostname */
    suspend fun setDnsMode(mode: String, host: String? = null) = withContext(Dispatchers.IO) {
        Shell.runAsync("settings put global private_dns_mode $mode")
        if (mode == "hostname" && !host.isNullOrBlank()) {
            Shell.runAsync("settings put global private_dns_specifier $host")
        }
        Unit
    }
}
