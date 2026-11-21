package com.freeplay.toolbox.core

import android.content.ComponentName
import android.os.Build
import android.os.IBinder
import android.os.Parcel
import com.rosan.dhizuku.api.Dhizuku

/**
 * Dhizuku 设备控制：通过 Dhizuku 以设备所有者身份调用 DevicePolicyManager。
 *
 * transact code 按各 Android 版本 IDevicePolicyManager.aidl 的方法序号硬编码。
 * 版本适配：
 * - API 33：无 callerPackageName 参数，部分方法序号不同
 * - API 34：方法含 callerPackageName + parent 参数
 * 目前覆盖 API 33–34；更高版本若方法表变动，依赖 [compat] 做显式拦截。
 */
object DeviceControl {

    private const val IFACE = "android.app.admin.IDevicePolicyManager"
    private const val FIRST = IBinder.FIRST_CALL_TRANSACTION

    /** API 34 分支 */
    private val is34 get() = Build.VERSION.SDK_INT >= 34

    /** 当前系统版本是否在已适配范围内 */
    fun compat(): Boolean = when (Build.VERSION.SDK_INT) {
        in 33..34 -> true
        else -> false
    }

    /** 不支持时的说明文案 */
    fun compatHint(): String = when {
        Build.VERSION.SDK_INT < 33 -> "需要 Android 13 及以上"
        Build.VERSION.SDK_INT > 34 -> "当前系统 (API ${Build.VERSION.SDK_INT}) 未适配，调用可能失败"
        else -> ""
    }

    fun supported(): Boolean = Build.VERSION.SDK_INT >= 33

    private fun wrapper(): IBinder? {
        return try {
            val sm = Class.forName("android.os.ServiceManager")
            val raw = sm.getMethod("getService", String::class.java)
                .invoke(null, "device_policy") as? IBinder ?: return null
            Dhizuku.binderWrapper(raw)
        } catch (_: Throwable) {
            null
        }
    }

    /** 执行一次 DPM transact */
    private fun dpm(code: Int, write: (Parcel) -> Unit): Boolean {
        if (!supported()) return false
        if (!Dhizuku.isPermissionGranted()) throw SecurityException("Dhizuku 未授权")
        val binder = wrapper() ?: return false
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(IFACE)
            write(data)
            return binder.transact(code, data, reply, 0)
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    /** 执行一次返回 boolean 的调用；null = 失败/异常 */
    private fun dpmReadBool(code: Int, write: (Parcel) -> Unit): Boolean? {
        if (!supported()) return null
        if (!Dhizuku.isPermissionGranted()) throw SecurityException("Dhizuku 未授权")
        val binder = wrapper() ?: return null
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(IFACE)
            write(data)
            if (!binder.transact(code, data, reply, 0)) {
                null
            } else if (reply.readInt() != 0) {
                null                    // 系统端抛异常
            } else {
                reply.readInt() != 0
            }
        } catch (_: Throwable) {
            null
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    /**
     * 反射构造 android.os.RemoteCallback（SystemApi，公开 SDK 无此类）。
     * 构造失败返回 null，调用方写 null 兜底。
     */
    private fun remoteCallback(): android.os.Parcelable? = try {
        val listenerCls = Class.forName("android.os.RemoteCallback\$OnResultListener")
        val proxy = java.lang.reflect.Proxy.newProxyInstance(
            listenerCls.classLoader,
            arrayOf(listenerCls),
        ) { _, method, _ ->
            if (method.returnType == Void.TYPE) null else null
        }
        val cls = Class.forName("android.os.RemoteCallback")
        val ctor = cls.getConstructor(listenerCls)
        ctor.newInstance(proxy) as? android.os.Parcelable
    } catch (_: Throwable) {
        null
    }

    private fun admin(): ComponentName = Dhizuku.getOwnerComponent()
    private fun caller(): String = try { Dhizuku.getOwnerPackageName() } catch (_: Throwable) { "android" }

    // ---------------- 相机 ----------------

    fun setCameraDisabled(disabled: Boolean): Boolean = dpm(52 + FIRST) {
        it.writeTypedObject(admin(), 0)
        if (is34) it.writeString(caller())
        it.writeInt(if (disabled) 1 else 0)
        it.writeInt(0)
    }

    fun isCameraDisabled(): Boolean? = dpmReadBool(53 + FIRST) {
        it.writeTypedObject(admin(), 0)
        if (is34) it.writeString(caller())
        it.writeInt(android.os.Process.myUid())
        it.writeInt(0)
    }

    // ---------------- 截屏 ----------------

    fun setScreenCaptureDisabled(disabled: Boolean): Boolean = dpm(54 + FIRST) {
        it.writeTypedObject(admin(), 0)
        if (is34) it.writeString(caller())
        it.writeInt(if (disabled) 1 else 0)
        it.writeInt(0)
    }

    fun isScreenCaptureDisabled(): Boolean? = dpmReadBool(55 + FIRST) {
        it.writeTypedObject(admin(), 0)
        it.writeInt(android.os.Process.myUid())
        it.writeInt(0)
    }

    // ---------------- 应用隐藏 / 卸载拦截 ----------------

    fun setApplicationHidden(pkg: String, hidden: Boolean): Boolean {
        val idx = if (is34) 151 else 148
        return dpm(idx + FIRST) {
            it.writeTypedObject(admin(), 0)
            it.writeString(caller())
            it.writeString(pkg)
            it.writeInt(if (hidden) 1 else 0)
            it.writeInt(0)
        }
    }

    fun isApplicationHidden(pkg: String): Boolean? {
        val idx = if (is34) 152 else 149
        return dpmReadBool(idx + FIRST) {
            it.writeTypedObject(admin(), 0)
            it.writeString(caller())
            it.writeString(pkg)
            it.writeInt(0)
        }
    }

    fun setUninstallBlocked(pkg: String, blocked: Boolean): Boolean {
        val idx = if (is34) 190 else 187
        return dpm(idx + FIRST) {
            it.writeTypedObject(admin(), 0)
            it.writeString(caller())
            it.writeString(pkg)
            it.writeInt(if (blocked) 1 else 0)
        }
    }

    fun isUninstallBlocked(pkg: String): Boolean? {
        val idx = if (is34) 191 else 188
        return dpmReadBool(idx + FIRST) {
            if (!is34) it.writeTypedObject(admin(), 0)
            it.writeString(pkg)
        }
    }

    // ---------------- 音量 ----------------

    fun setMasterVolumeMuted(on: Boolean): Boolean {
        val idx = if (is34) 187 else 184
        return dpm(idx + FIRST) {
            it.writeTypedObject(admin(), 0)
            it.writeInt(if (on) 1 else 0)
        }
    }

    fun isMasterVolumeMuted(): Boolean? = dpmReadBool((if (is34) 188 else 185) + FIRST) {
        it.writeTypedObject(admin(), 0)
    }

    // ---------------- 锁屏 / 状态栏 ----------------

    fun lockNow(): Boolean = dpm(39 + FIRST) {
        it.writeInt(0)
        if (is34) it.writeString(caller())
        it.writeInt(0)
    }

    fun setKeyguardDisabled(disabled: Boolean): Boolean {
        val idx = if (is34) 228 else 217
        return dpm(idx + FIRST) {
            it.writeTypedObject(admin(), 0)
            it.writeInt(if (disabled) 1 else 0)
        }
    }

    fun setStatusBarDisabled(disabled: Boolean): Boolean {
        val idx = if (is34) 229 else 218
        return dpm(idx + FIRST) {
            it.writeTypedObject(admin(), 0)
            if (is34) it.writeString(caller())
            it.writeInt(if (disabled) 1 else 0)
        }
    }

    // ---------------- 代理 ----------------

    fun setGlobalProxy(hostPort: String): Boolean = dpm(45 + FIRST) {
        it.writeTypedObject(admin(), 0)
        it.writeString(hostPort)
        it.writeString("")
    }

    fun clearGlobalProxy(): Boolean = dpm(45 + FIRST) {
        it.writeTypedObject(admin(), 0)
        it.writeString(null)
        it.writeString("")
    }

    // ---------------- 权限强授（拒绝后仍可强设状态） ----------------

    /** grantState: 0=默认 1=已授予 2=已拒绝 3=已拒绝且用户不可改 */
    fun setPermissionGrantState(pkg: String, perm: String, grantState: Int): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val idx = if (is34) 236 else 224
        return dpm(idx + FIRST) {
            it.writeTypedObject(admin(), 0)
            it.writeString(caller())
            it.writeString(pkg)
            it.writeString(perm)
            it.writeInt(grantState)
            it.writeTypedObject(remoteCallback(), 0)
        }
    }

    fun getPermissionGrantState(pkg: String, perm: String): Int? {
        val idx = if (is34) 237 else 225
        return try {
            if (!supported()) return null
            if (!Dhizuku.isPermissionGranted()) return null
            val binder = wrapper() ?: return null
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(IFACE)
                data.writeTypedObject(admin(), 0)
                data.writeString(caller())
                data.writeString(pkg)
                data.writeString(perm)
                if (!binder.transact(idx + FIRST, data, reply, 0)) return null
                if (reply.readInt() != 0) return null
                reply.readInt()
            } finally {
                data.recycle()
                reply.recycle()
            }
        } catch (_: Throwable) {
            null
        }
    }

    // ---------------- 系统更新策略（实验性） ----------------

    /**
     * 暂停系统更新若干天。依赖 SystemUpdatePolicy 反射构造，
     * 构造失败返回 false。postponeDays <= 0 表示解除策略。
     */
    fun setUpdatePostpone(postponeDays: Int): Boolean {
        val policyCls = try {
            Class.forName("android.app.admin.SystemUpdatePolicy")
        } catch (_: Throwable) {
            return false
        }
        val policy: Any? = try {
            if (postponeDays <= 0) null
            else policyCls.getDeclaredMethod("createPostponeInstall", Int::class.javaPrimitiveType)
                .invoke(null, postponeDays)
        } catch (_: Throwable) {
            try {
                policyCls.getDeclaredMethod("createPostponeInstall").invoke(null)
            } catch (_: Throwable) {
                return false
            }
        }
        val idx = if (is34) 225 else 214
        return dpm(idx + FIRST) {
            it.writeTypedObject(admin(), 0)
            if (is34) it.writeString(caller())
            it.writeTypedObject(policy as? android.os.Parcelable, 0)
        }
    }
}
