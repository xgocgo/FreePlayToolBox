package com.freeplay.toolbox.data

/** 权限中文名映射 + AppOps 操作映射 */
object PermissionNames {

    private val names = mapOf(
        "CAMERA" to "相机",
        "RECORD_AUDIO" to "麦克风录音",
        "ACTIVITY_RECOGNITION" to "身体活动（摇一摇/步数）",
        "BODY_SENSORS" to "身体传感器（心率等）",
        "ACCESS_FINE_LOCATION" to "精确位置",
        "ACCESS_COARSE_LOCATION" to "大致位置",
        "ACCESS_BACKGROUND_LOCATION" to "后台位置",
        "ACCESS_MEDIA_LOCATION" to "照片位置信息",
        "READ_CONTACTS" to "读取联系人",
        "WRITE_CONTACTS" to "修改联系人",
        "GET_ACCOUNTS" to "读取账户",
        "CALL_PHONE" to "拨打电话",
        "ANSWER_PHONE_CALLS" to "接听电话",
        "READ_PHONE_STATE" to "读取电话状态",
        "READ_PHONE_NUMBERS" to "读取本机号码",
        "READ_CALL_LOG" to "读取通话记录",
        "WRITE_CALL_LOG" to "修改通话记录",
        "READ_SMS" to "读取短信",
        "SEND_SMS" to "发送短信",
        "RECEIVE_SMS" to "接收短信",
        "RECEIVE_MMS" to "接收彩信",
        "RECEIVE_WAP_PUSH" to "接收 WAP 推送",
        "READ_CALENDAR" to "读取日历",
        "WRITE_CALENDAR" to "修改日历",
        "READ_EXTERNAL_STORAGE" to "读取存储空间",
        "WRITE_EXTERNAL_STORAGE" to "修改存储空间",
        "MANAGE_EXTERNAL_STORAGE" to "管理所有文件",
        "READ_MEDIA_IMAGES" to "读取图片",
        "READ_MEDIA_VIDEO" to "读取视频",
        "READ_MEDIA_AUDIO" to "读取音频",
        "POST_NOTIFICATIONS" to "发送通知",
        "SYSTEM_ALERT_WINDOW" to "悬浮窗显示",
        "RECEIVE_BOOT_COMPLETED" to "开机自启",
        "FOREGROUND_SERVICE" to "前台服务",
        "USE_BIOMETRIC" to "生物识别",
        "USE_FINGERPRINT" to "指纹识别",
        "VIBRATE" to "振动",
        "WAKE_LOCK" to "保持唤醒",
        "INTERNET" to "网络访问",
        "ACCESS_NETWORK_STATE" to "读取网络状态",
        "CHANGE_NETWORK_STATE" to "修改网络状态",
        "ACCESS_WIFI_STATE" to "读取 WIFI 状态",
        "CHANGE_WIFI_STATE" to "修改 WIFI 状态",
        "BLUETOOTH" to "蓝牙",
        "BLUETOOTH_ADMIN" to "蓝牙管理",
        "BLUETOOTH_SCAN" to "扫描蓝牙设备",
        "BLUETOOTH_CONNECT" to "连接蓝牙设备",
        "NFC" to "NFC",
        "QUERY_ALL_PACKAGES" to "读取应用列表",
        "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" to "忽略电池优化",
        "SCHEDULE_EXACT_ALARM" to "精确闹钟",
        "USE_EXACT_ALARM" to "精确闹钟（日历类）",
        "SEND_SMS" to "发送短信",
        "WRITE_SETTINGS" to "修改系统设置",
        "BIND_ACCESSIBILITY_SERVICE" to "无障碍服务",
        "BIND_DEVICE_ADMIN" to "设备管理员",
        "BIND_VPN_SERVICE" to "VPN 服务",
        "BIND_NOTIFICATION_LISTENER_SERVICE" to "通知读取",
        "REQUEST_INSTALL_PACKAGES" to "安装应用",
        "REQUEST_DELETE_PACKAGES" to "卸载应用",
        "MODIFY_AUDIO_SETTINGS" to "修改音频设置",
        "CAPTURE_AUDIO_OUTPUT" to "录制系统声音",
        "SET_WALLPAPER" to "设置壁纸",
        "SET_ALARM" to "设置闹钟",
        "READ_SYNC_SETTINGS" to "读取同步设置",
        "WRITE_SYNC_SETTINGS" to "修改同步设置",
        "DISABLE_KEYGUARD" to "禁用锁屏",
        "EXPAND_STATUS_BAR" to "展开状态栏",
        "KILL_BACKGROUND_PROCESSES" to "结束后台进程",
        "REORDER_TASKS" to "排序任务",
        "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" to "忽略电池优化",
        "TRANSMIT_IR" to "红外遥控",
        "USE_SIP" to "SIP 通话",
        "PROCESS_OUTGOING_CALLS" to "拦截拨出电话",
        "ADD_VOICEMAIL" to "添加语音信箱",
        "WRITE_CONTACTS" to "修改联系人",
        "ACCESS_FINE_LOCATION" to "精确位置",
        "ACCESS_COARSE_LOCATION" to "大致位置",
        "RECEIVE_BOOT_COMPLETED" to "开机自启",
    )

    /** 权限短名 → 中文名，未收录的返回原短名 */
    fun zh(short: String): String = names[short] ?: short

    /** 权限短名 → AppOps op 名（可用三态控制），无对应返回 null */
    fun appOpsOp(short: String): String? = when (short) {
        "ACTIVITY_RECOGNITION" -> "ACTIVITY_RECOGNITION"
        "BODY_SENSORS" -> "BODY_SENSORS"
        "CAMERA" -> "CAMERA"
        "RECORD_AUDIO" -> "RECORD_AUDIO"
        "ACCESS_FINE_LOCATION" -> "FINE_LOCATION"
        "ACCESS_COARSE_LOCATION" -> "COARSE_LOCATION"
        "ACCESS_BACKGROUND_LOCATION" -> "ACCESS_BACKGROUND_LOCATION"
        "READ_CONTACTS" -> "READ_CONTACTS"
        "WRITE_CONTACTS" -> "WRITE_CONTACTS"
        "READ_SMS" -> "READ_SMS"
        "SEND_SMS" -> "SEND_SMS"
        "RECEIVE_SMS" -> "RECEIVE_SMS"
        "CALL_PHONE" -> "CALL_PHONE"
        "READ_CALL_LOG" -> "READ_CALL_LOG"
        "WRITE_CALL_LOG" -> "WRITE_CALL_LOG"
        "READ_CALENDAR" -> "READ_CALENDAR"
        "WRITE_CALENDAR" -> "WRITE_CALENDAR"
        "READ_PHONE_STATE" -> "READ_PHONE_STATE"
        "SYSTEM_ALERT_WINDOW" -> "SYSTEM_ALERT_WINDOW"
        else -> null
    }
}

/** AppOps 操作中文名 */
private val opNames = mapOf(
    "ACTIVITY_RECOGNITION" to "身体活动识别",
    "BODY_SENSORS" to "身体传感器",
    "CAMERA" to "相机",
    "RECORD_AUDIO" to "录音",
    "FINE_LOCATION" to "精确位置",
    "COARSE_LOCATION" to "大致位置",
    "ACCESS_BACKGROUND_LOCATION" to "后台位置",
    "READ_CONTACTS" to "读取联系人",
    "WRITE_CONTACTS" to "修改联系人",
    "READ_SMS" to "读取短信",
    "SEND_SMS" to "发送短信",
    "RECEIVE_SMS" to "接收短信",
    "CALL_PHONE" to "拨打电话",
    "READ_CALL_LOG" to "读取通话记录",
    "WRITE_CALL_LOG" to "修改通话记录",
    "READ_CALENDAR" to "读取日历",
    "WRITE_CALENDAR" to "修改日历",
    "READ_PHONE_STATE" to "读取电话状态",
    "SYSTEM_ALERT_WINDOW" to "悬浮窗",
    "WRITE_SETTINGS" to "修改系统设置",
    "GET_USAGE_STATS" to "使用情况统计",
    "PACKAGE_USAGE_STATS" to "使用情况统计",
    "RUN_IN_BACKGROUND" to "后台运行",
    "WAKE_LOCK" to "唤醒锁",
    "VIBRATE" to "振动",
    "READ_CLIPBOARD" to "读取剪贴板",
    "WRITE_CLIPBOARD" to "写入剪贴板",
    "MOCK_LOCATION" to "模拟位置",
    "INSTALL_PACKAGES" to "安装应用",
    "REQUEST_INSTALL_PACKAGES" to "请求安装应用",
    "START_FOREGROUND" to "启动前台服务",
    "USE_BIOMETRIC" to "生物识别",
    "QUERY_PACKAGES" to "读取应用列表",
    "READ_MEDIA_IMAGES" to "读取图片",
    "READ_MEDIA_VIDEO" to "读取视频",
    "READ_MEDIA_AUDIO" to "读取音频",
    "READ_EXTERNAL_STORAGE" to "读取存储",
    "WRITE_EXTERNAL_STORAGE" to "写入存储",
    "GET_ACCOUNTS" to "读取账户",
    "MANAGE_EXTERNAL_STORAGE" to "管理所有文件",
    "ACTIVATE_VPN" to "启用 VPN",
    "POST_NOTIFICATION" to "发送通知",
    "POST_NOTIFICATIONS" to "发送通知",
    "BOOT_COMPLETED" to "开机自启",
    "AUDIO_MEDIA_VOLUME" to "媒体音量",
)

fun opZh(op: String): String = opNames[op] ?: op
