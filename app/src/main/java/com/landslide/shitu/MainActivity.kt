package com.landslide.shitu

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.landslide.shitu.data.AppLocale
import com.landslide.shitu.data.Settings
import com.landslide.shitu.service.WatchService
import com.landslide.shitu.ui.ShituRoot
import com.landslide.shitu.ui.theme.ShituTheme
import com.landslide.shitu.ui.theme.resolveDark
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        /** 「有新版本」通知里带的 extra：点通知进来直接打开设置页的更新弹窗。 */
        const val EXTRA_SHOW_UPDATE = "show_update"
    }

    /** 通知点进来的「打开更新」标记；用 State 让 Compose 能跟着变。 */
    private val showUpdate = mutableStateOf(false)

    /**
     * 语言开关真正生效的地方：给 Activity 套一层指定 Locale 的 Context。
     * 必须在 `super.onCreate` 之前决定，所以语言值用 SharedPreferences 同步读（见 [AppLocale]）。
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 让状态栏/导航栏图标跟随主题（浅色主题用深色图标，深色主题用浅色图标）
        enableEdgeToEdge()
        requestNotificationPermission()
        requestMediaPermission()
        WatchService.start(this)
        val app = application as ShituApp
        showUpdate.value = intent?.getBooleanExtra(EXTRA_SHOW_UPDATE, false) == true
        setContent {
            val settings by app.settings.settings.collectAsStateWithLifecycle(initialValue = Settings())
            val dark = resolveDark(settings.themeMode, isSystemInDarkTheme())
            // 主题切换时（含手动选浅色/深色）重新设置系统栏图标颜色
            LaunchedEffect(dark) {
                val barStyle = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
            }
            ShituTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                ShituRoot(
                    app = app,
                    autoOpenUpdate = showUpdate.value,
                    onAutoOpenUpdateConsumed = { showUpdate.value = false },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // 打开 App 立即扫一次（规格 §6.7）；手动打开不受「运行条件」限制
        val app = application as ShituApp
        lifecycleScope.launch { runCatching { app.runDueRules(force = true) } }
        // 顺带检查更新（内部 24h 节流）；打开 App 时就能看到新版提示
        lifecycleScope.launch { runCatching { app.maybeCheckUpdate() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // singleTop：点「有新版本」通知时 Activity 已存在，走这里
        if (intent.getBooleanExtra(EXTRA_SHOW_UPDATE, false)) showUpdate.value = true
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }

    /** 日志缩略图要读相册里的图；拿不到就显示占位图，不影响搬运。 */
    private fun requestMediaPermission() {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(perm), 1002)
        }
    }
}
