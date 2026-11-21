# Shizuku / Dhizuku AIDL 与反射调用的系统服务
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-keep class com.rosan.dhizuku.** { *; }
-keep class android.app.admin.IDevicePolicyManager { *; }
-keep class android.os.ServiceManager { *; }

# 崩溃日志里保留行号，便于排查
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Dhizuku 内部反射引用的隐藏系统类（编译期不存在，运行时由系统提供）
-dontwarn android.app.ActivityThread
-dontwarn android.os.ServiceManager
-dontwarn android.app.admin.**
-keep class android.app.ActivityThread { *; }
