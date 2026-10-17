package com.landslide.shitu.data

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/**
 * 界面语言。
 *
 * 为什么不用系统的 per-app language（`LocaleManager` / `AppCompatDelegate`）：
 * 项目的 Activity 是 `ComponentActivity`、主题是 Material3，为了一个语言开关去引 AppCompat、
 * 换基类、换主题不值得；而 Android 13 以下也没有 per-app language。
 * 这里用 `attachBaseContext` 给 Activity 套一层指定 Locale 的 Context，API 30 起全版本一致，
 * 代码不到 50 行，也不影响系统其它应用。
 *
 * 注意：语言值必须能被 `attachBaseContext` **同步**读到，所以单独落一份 SharedPreferences，
 * DataStore 里那份只用于界面状态显示。
 */
object AppLocale {
    const val SYSTEM = 0
    const val CHINESE = 1
    const val ENGLISH = 2

    private const val PREFS = "app_locale"
    private const val KEY = "language"

    fun current(context: Context): Int =
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, SYSTEM)
        }.getOrDefault(SYSTEM)

    fun set(context: Context, value: Int) {
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt(KEY, value).apply()
        }
    }

    fun localeOf(language: Int): Locale? = when (language) {
        CHINESE -> Locale.SIMPLIFIED_CHINESE
        ENGLISH -> Locale.ENGLISH
        else -> null
    }

    /** 按语言包一层 Context；跟随系统时原样返回。 */
    fun wrap(base: Context, language: Int = current(base)): Context {
        val locale = localeOf(language) ?: return base
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLocales(LocaleList(locale))
        return runCatching { base.createConfigurationContext(config) }.getOrDefault(base)
    }

    /** Compose 里的 LocalContext 常常不是 Activity 本身，往上找一层。 */
    fun findActivity(context: Context): Activity? {
        var c: Context? = context
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }
}
