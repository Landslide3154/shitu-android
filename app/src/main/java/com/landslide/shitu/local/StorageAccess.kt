package com.landslide.shitu.local

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings

/**
 * 「所有文件访问」的查询与跳转。
 *
 * 和 Shizuku 授权不是一回事：Shizuku 的授权只认它自己的弹窗（`adb pm grant` 对它无效），
 * 这个走系统设置页，用户点一下「允许管理所有文件」就行，不需要电脑、不需要重启。
 */
object StorageAccess {

    /**
     * 现在能不能用本地通道访问普通目录。
     *
     * Android 11 起看「所有文件访问」；更低版本没有这个概念，退回旧版存储权限。
     * 读不到/抛异常一律当没授权——宁可多引导一次，也不要在没权限时去搬文件。
     */
    fun isGranted(context: Context): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }
    }.getOrDefault(false)

    /**
     * 能给本地通道授权的系统页面。
     *
     * 优先「本应用」那个直达页（带 package: 参数），用户少点一层；
     * 个别 ROM 没有这个 Activity（或不让解析）时，退回所有应用的列表页，让用户自己在列表里找到拾图。
     * Android 11 以下压根没有「所有文件访问」，只能去应用详情里手动开存储权限。
     */
    fun settingsIntent(context: Context): Intent? {
        val pkg = Uri.fromParts("package", context.packageName, null)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return runCatching {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
            }.getOrNull()
        }

        // 构造本身几乎不会失败；真正可能失败的是设备上没这个页面，所以先确认能打开再返回
        val perApp = runCatching {
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, pkg)
        }.getOrNull()
        if (perApp != null && canOpen(context, perApp)) return perApp

        return runCatching {
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        }.getOrNull()
    }

    /**
     * 安全地打开设置页。
     *
     * 调用点可能在通知/前台服务里（没有 Activity 栈），所以要 NEW_TASK；
     * 找不到页面就静默失败——这里宁可什么都不发生，也不能因为跳转把服务/界面搞崩。
     */
    fun openSettings(context: Context) {
        val intent = settingsIntent(context) ?: return
        runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun canOpen(context: Context, intent: Intent): Boolean =
        runCatching { context.packageManager.resolveActivity(intent, 0) != null }.getOrDefault(false)
}
