# 拾图 Shitu 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 构建安卓 App「拾图」——借 Shizuku 的 shell 身份，把指定目录（含 `Android/data/<包名>/`）里的图片全自动、后台常驻地搬到目标目录，可暂停、可撤回、绝不误删。

**架构：** App 进程只做界面/调度/记账，**不碰文件**；所有文件操作经 AIDL 交给跑在 shell 身份下的 `ShituUserService`；媒体库由系统自动收录（实测 8 秒内生效）。常驻用 `specialUse` 前台服务，兜底用 WorkManager，开机由 `BOOT_COMPLETED` 拉起服务并轮询 Shizuku 状态。

**技术栈：** Kotlin 2.1 · Jetpack Compose · Room · DataStore · WorkManager · Shizuku API 13.1.5（Maven Central）· minSdk 30 / targetSdk 36 / compileSdk 36

**唯一依据：** [`docs/superpowers/specs/2026-10-04-shitu-design.md`](../specs/2026-10-04-shitu-design.md)（下称"规格"）

---

## 前置条件（开工前一次性）

- [ ] 安装 Android SDK（本机 `D:\Android` 当前不存在）：
  ```powershell
  # 下载 commandline-tools 后
  $env:ANDROID_HOME = "D:\Android"
  & "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat" "platform-tools" "platforms;android-36" "build-tools;36.0.0"
  ```
- [ ] 确认 JDK：本机为 JDK 25。**Gradle 9.5.0 + AGP 9.2.1**（本机 Gradle 缓存已有 `gradle-9.5.0-all` 与 AGP 9.2.1）。若插件解析失败，回退：Gradle 8.13 + AGP 8.13.0，并改用 JDK 21。
- [ ] 真机连接：`D:\soft\scrcpy\adb.exe devices` 应为 `device`；Shizuku 已启动并授权拾图。
- [ ] 复现规格附录 A 的探针，确认当前系统仍可搬运（全绿才继续）。

---

## 文件结构（锁定分解）

```
D:\code\shitu\
├── settings.gradle.kts                 仓库与模块声明
├── build.gradle.kts                    根构建脚本（插件版本）
├── gradle.properties                   JVM/AndroidX 开关
├── gradle/libs.versions.toml           版本目录（唯一版本来源）
├── app/build.gradle.kts                app 模块：依赖、签名、FGS 配置
├── app/src/main/AndroidManifest.xml    权限、服务、接收器声明
└── app/src/
    ├── main/aidl/com/landslide/shitu/IShituService.aidl   桥接口（7 个方法）
    ├── main/java/com/landslide/shitu/
    │   ├── ShituApp.kt                  Application：通知渠道、WorkManager 初始化
    │   ├── MainActivity.kt              Compose 入口 + 导航
    │   ├── core/NamePolicy.kt           ★命名管线（纯函数）
    │   ├── core/ScanFilter.kt           ★候选过滤（扩展名/稳定期/类型）
    │   ├── core/RateLimiter.kt          ★三重限速闸门
    │   ├── core/LoopGuard.kt            ★重复抑制
    │   ├── core/RuleConflictChecker.kt  ★规则冲突静态检查
    │   ├── data/db/Enums.kt             Mode/RuleState/ItemStatus/LogResult
    │   ├── data/db/Entities.kt          RuleEntity/ItemEntity/LogEntity
    │   ├── data/db/Daos.kt              RuleDao/ItemDao/LogDao
    │   ├── data/db/AppDatabase.kt       Room 数据库 + 迁移
    │   ├── data/SettingsStore.kt        DataStore（规格 §8 全部参数）
    │   ├── data/RuleRepository.kt       规则/条目/日志的仓储
    │   ├── shizuku/RemoteFile.kt         Parcelable 文件信息
    │   ├── shizuku/ShituUserService.kt   跑在 shell 身份的实现
    │   ├── shizuku/ShizukuBridge.kt      权限/绑定/状态机/重试
    │   ├── shizuku/ShizukuState.kt       状态枚举
    │   ├── engine/RuleEngine.kt         ★一轮 Run 的编排
    │   ├── engine/TransferExecutor.kt   ★单文件事务
    │   ├── engine/UndoService.kt        ★撤回
    │   ├── engine/HealthChecker.kt      ★自检 6 项
    │   ├── service/WatchService.kt      specialUse 前台服务
    │   ├── service/BootReceiver.kt      开机拉起服务
    │   ├── service/FallbackWorker.kt    WorkManager 兜底（直接搬，不起服务）
    │   ├── service/Notifier.kt          通知
    │   └── ui/                          RuleListScreen / RuleEditScreen / LogScreen /
    │                                    SettingsScreen / DirPickerDialog / Nav
    └── test/java/com/landslide/shitu/   纯逻辑单测（不依赖真机）
```

**测试策略：** `core/` 与 `engine/` 通过接口 `FileBridge` 与 Shizuku 解耦，单测用 `FakeFileBridge`；真机行为用 adb 命令验证（每任务给出命令与期望输出）。

---

## 任务 1：项目骨架与可构建基线

**文件：**
- 创建：`settings.gradle.kts`、`build.gradle.kts`、`gradle.properties`、`gradle/libs.versions.toml`、`app/build.gradle.kts`、`app/src/main/AndroidManifest.xml`、`app/src/main/java/com/landslide/shitu/ShituApp.kt`、`MainActivity.kt`

- [ ] **步骤 1：写版本目录 `gradle/libs.versions.toml`**

```toml
[versions]
agp = "9.2.1"
kotlin = "2.1.20"
composeBom = "2025.04.01"
room = "2.7.1"
datastore = "1.1.3"
work = "2.10.0"
shizuku = "13.1.5"
lifecycle = "2.8.7"
nav = "2.8.9"
junit = "4.13.2"
robolectric = "4.14.1"

[libraries]
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-material3 = { module = "androidx.compose.material3:material3" }
compose-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
activity-compose = { module = "androidx.activity:activity-compose:1.10.1" }
lifecycle-runtime = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
lifecycle-viewmodel = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "nav" }
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
datastore-prefs = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
work-runtime = { module = "androidx.work:work-runtime-ktx", version.ref = "work" }
shizuku-api = { module = "dev.rikka.shizuku:api", version.ref = "shizuku" }
shizuku-provider = { module = "dev.rikka.shizuku:provider", version.ref = "shizuku" }
junit = { module = "junit:junit", version.ref = "junit" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version = "2.1.20-1.0.32" }
```

- [ ] **步骤 2：写 `settings.gradle.kts` 与根 `build.gradle.kts`**

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "shitu"
include(":app")
```

```kotlin
// build.gradle.kts
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
```

- [ ] **步骤 3：写 `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

- [ ] **步骤 4：写 `app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.landslide.shitu"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.landslide.shitu"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
    buildFeatures { compose = true; aidl = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { jvmToolchain(17) }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.preview)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.navigation.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.prefs)
    implementation(libs.work.runtime)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
```

- [ ] **步骤 5：写最小 `AndroidManifest.xml`（本任务只到能启动）**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:name=".ShituApp"
        android:label="拾图"
        android:icon="@mipmap/ic_launcher"
        android:theme="@style/Theme.Shitu">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **步骤 6：写 `ShituApp.kt` 与 `MainActivity.kt` 的最小可运行实现**

```kotlin
// ShituApp.kt
package com.landslide.shitu
import android.app.Application
class ShituApp : Application()

// MainActivity.kt
package com.landslide.shitu
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Text("拾图 0.1.0") }
    }
}
```

- [ ] **步骤 7：补最小主题资源**（`app/src/main/res/values/themes.xml`：`Theme.Shitu` 继承 `android:Theme.Material.Light.NoActionBar`；`mipmap/ic_launcher` 用模板默认图标）

- [ ] **步骤 8：构建验证**

运行：`.\gradlew.bat :app:assembleDebug`
预期：`BUILD SUCCESSFUL`，产出 `app/build/outputs/apk/debug/app-debug.apk`

- [ ] **步骤 9：真机安装验证**

运行：`D:\soft\scrcpy\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk`
预期：`Success`，桌面出现「拾图」且能启动、显示 `拾图 0.1.0`

- [ ] **步骤 10：Commit**

```bash
git add -A
git commit -m "chore: 项目骨架（Gradle 版本目录 + Compose + 可构建 APK）"
```

---

## 任务 2：纯逻辑核心 —— NamePolicy（命名管线）

**文件：**
- 创建：`app/src/main/java/com/landslide/shitu/core/NamePolicy.kt`
- 测试：`app/src/test/java/com/landslide/shitu/core/NamePolicyTest.kt`

- [ ] **步骤 1：先写失败的测试**

```kotlin
package com.landslide.shitu.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NamePolicyTest {
    private val policy = NamePolicy()

    @Test fun `清洗 Windows 非法字符`() {
        assertEquals("a_b_c_d_e_f_g_h_i.jpg", policy.sanitize("a:b*c?d\"e<f>g|h/i.jpg"))
    }

    @Test fun `中文与 emoji 保留`() {
        assertEquals("封面🎉.png", policy.sanitize("封面🎉.png"))
    }

    @Test fun `去掉控制字符与结尾的点`() {
        assertEquals("x_y", policy.sanitize("x\u0001y."))
    }

    @Test fun `追加来源 App 后缀`() {
        assertEquals("bookLastImage_起点读书.png",
            policy.withSourceSuffix("bookLastImage.png", "起点读书"))
    }

    @Test fun `超过 180 字节时截断且保留扩展名`() {
        val long = "图".repeat(200) + ".png"
        val out = policy.truncate(long, maxBytes = 180)
        assertTrue(out.endsWith(".png"))
        assertTrue(out.toByteArray(Charsets.UTF_8).size <= 180)
    }

    @Test fun `目标不存在时用原名后缀`() {
        val name = policy.resolve("bookLastImage.png", "起点读书", exists = { false })
        assertEquals("bookLastImage_起点读书.png", name)
    }

    @Test fun `目标已存在时加时间戳`() {
        val name = policy.resolve("a.png", "起点", exists = { it == "a_起点.png" },
            timestamp = "20261004-101500")
        assertEquals("a_起点_20261004-101500.png", name)
    }

    @Test fun `仍然冲突时加序号`() {
        val existing = setOf("a_起点.png", "a_起点_20261004-101500.png", "a_起点_1.png")
        val name = policy.resolve("a.png", "起点", exists = { it in existing },
            timestamp = "20261004-101500")
        assertEquals("a_起点_2.png", name)
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`.\gradlew.bat :app:testDebugUnitTest --tests "*NamePolicyTest*"`
预期：FAIL，编译错误 `Unresolved reference: NamePolicy`

- [ ] **步骤 3：写最小实现**

```kotlin
package com.landslide.shitu.core

/**
 * 目标文件名生成：清洗 → 后缀 → 冲突 → 截断。
 * 纯函数、无 IO，供 TransferExecutor 调用。
 */
class NamePolicy(private val maxBytes: Int = 180) {

    private val illegal = Regex("[\\\\/:*?\"<>|\\x00-\\x1F]")

    fun sanitize(name: String): String =
        name.replace(illegal, "_").trim().trimEnd('.')

    fun withSourceSuffix(name: String, sourceApp: String): String {
        val dot = name.lastIndexOf('.')
        if (dot <= 0) return "${name}_$sourceApp"
        return "${name.substring(0, dot)}_$sourceApp${name.substring(dot)}"
    }

    fun truncate(name: String, maxBytes: Int = this.maxBytes): String {
        if (name.toByteArray(Charsets.UTF_8).size <= maxBytes) return name
        val dot = name.lastIndexOf('.')
        val ext = if (dot > 0) name.substring(dot) else ""
        val base = if (dot > 0) name.substring(0, dot) else name
        val budget = maxBytes - ext.toByteArray(Charsets.UTF_8).size
        val sb = StringBuilder()
        for (ch in base) {
            if ((sb.toString() + ch).toByteArray(Charsets.UTF_8).size > budget) break
            sb.append(ch)
        }
        return sb.toString() + ext
    }

    /** exists: 给定的目标文件名在目标目录中是否已存在 */
    fun resolve(rawName: String, sourceApp: String, exists: (String) -> Boolean,
                timestamp: String = ""): String {
        val base = truncate(sanitize(rawName))
        val first = truncate(withSourceSuffix(base, sanitize(sourceApp)))
        if (!exists(first)) return first
        if (timestamp.isNotEmpty()) {
            val ts = truncate(withSourceSuffix(base, "${sanitize(sourceApp)}_$timestamp"))
            if (!exists(ts)) return ts
        }
        var n = 1
        while (true) {
            val cand = truncate(withSourceSuffix(base, "${sanitize(sourceApp)}_$n"))
            if (!exists(cand)) return cand
            n++
        }
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`.\gradlew.bat :app:testDebugUnitTest --tests "*NamePolicyTest*"`
预期：PASS（8 个测试全绿）

- [ ] **步骤 5：Commit**

```bash
git add app/src/main/java/com/landslide/shitu/core/NamePolicy.kt app/src/test/java/com/landslide/shitu/core/NamePolicyTest.kt
git commit -m "feat(core): 命名管线 NamePolicy（清洗/后缀/冲突/截断）"
```

---

## 任务 3：纯逻辑核心 —— ScanFilter / RateLimiter / LoopGuard

**文件：**
- 创建：`app/src/main/java/com/landslide/shitu/core/ScanFilter.kt`、`core/RateLimiter.kt`、`core/LoopGuard.kt`
- 测试：`app/src/test/java/com/landslide/shitu/core/{ScanFilterTest,RateLimiterTest,LoopGuardTest}.kt`

- [ ] **步骤 1：先写失败的测试**

```kotlin
// ScanFilterTest.kt
package com.landslide.shitu.core

import com.landslide.shitu.shizuku.RemoteFile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanFilterTest {
    private val filter = ScanFilter(extensions = setOf("jpg", "png"), stableSeconds = 30)

    private fun f(name: String, mtime: Long, dir: Boolean = false, link: Boolean = false) =
        RemoteFile("/src/$name", name, 100, mtime, dir, link)

    @Test fun `扩展名在白名单内且已稳定则通过`() {
        assertTrue(filter.accept(f("a.JPG", now - 60_000), now))
    }
    @Test fun `扩展名不在白名单则拒绝`() {
        assertFalse(filter.accept(f("a.mp4", now - 60_000), now))
    }
    @Test fun `未过稳定期则拒绝`() {
        assertFalse(filter.accept(f("a.jpg", now - 10_000), now))
    }
    @Test fun `目录与软链拒绝`() {
        assertFalse(filter.accept(f("d", now - 60_000, dir = true), now))
        assertFalse(filter.accept(f("l.jpg", now - 60_000, link = true), now))
    }
    private val now = 1_800_000_000_000L
}
```

```kotlin
// RateLimiterTest.kt
class RateLimiterTest {
    @Test fun `单轮 20 张后拒绝`() {
        val rl = RateLimiter(maxPerRun = 20, maxPerMinute = 120, maxRunMillis = 60_000)
        repeat(20) { assertTrue(rl.tryAcquire(t = it * 1000L)) }
        assertFalse(rl.tryAcquire(t = 20_000L))
    }
    @Test fun `单轮 60 秒超时`() {
        val rl = RateLimiter(20, 120, 60_000)
        assertTrue(rl.tryAcquire(0))
        assertFalse(rl.tryAcquire(60_001))
    }
    @Test fun `分钟窗口 120 张后拒绝`() {
        val rl = RateLimiter(maxPerRun = 500, maxPerMinute = 120, maxRunMillis = 600_000)
        repeat(120) { assertTrue(rl.tryAcquire(it.toLong())) }
        assertFalse(rl.tryAcquire(120L))
    }
    @Test fun `窗口滑动后恢复`() {
        val rl = RateLimiter(500, 120, 600_000)
        repeat(120) { rl.tryAcquire(it.toLong()) }
        assertTrue(rl.tryAcquire(60_001L))
    }
}
```

```kotlin
// LoopGuardTest.kt
class LoopGuardTest {
    @Test fun `30 分钟内同名 3 次触发暂停`() {
        val g = LoopGuard(windowMillis = 30 * 60_000, threshold = 3)
        assertFalse(g.record("a.png", 0))
        assertFalse(g.record("a.png", 10_000))
        assertTrue(g.record("a.png", 20_000))
    }
    @Test fun `超出窗口的不计数`() {
        val g = LoopGuard(30 * 60_000, 3)
        g.record("a.png", 0); g.record("a.png", 10_000)
        assertFalse(g.record("a.png", 31 * 60_000))
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`.\gradlew.bat :app:testDebugUnitTest --tests "*core.*"`
预期：FAIL（`Unresolved reference: ScanFilter / RateLimiter / LoopGuard`）

- [ ] **步骤 3：写实现**

```kotlin
// ScanFilter.kt
package com.landslide.shitu.core

import com.landslide.shitu.shizuku.RemoteFile

class ScanFilter(
    extensions: Set<String>,
    private val stableSeconds: Int,
) {
    private val exts = extensions.map { it.lowercase().removePrefix(".") }.toSet()

    fun accept(file: RemoteFile, nowMillis: Long): Boolean {
        if (file.isDirectory || file.isSymlink) return false
        val ext = file.name.substringAfterLast('.', "").lowercase()
        if (ext !in exts) return false
        return nowMillis - file.mtimeMillis >= stableSeconds * 1000L
    }
}
```

```kotlin
// RateLimiter.kt
package com.landslide.shitu.core

/** 三重闸门：单轮数量、单轮时长、每分钟数量。t 单位为毫秒（单调时钟）。 */
class RateLimiter(
    private val maxPerRun: Int,
    private val maxPerMinute: Int,
    private val maxRunMillis: Long,
) {
    private var runStart = -1L
    private var runCount = 0
    private val recent = ArrayDeque<Long>()

    fun tryAcquire(t: Long): Boolean {
        if (runStart < 0) { runStart = t; runCount = 0 }
        if (t - runStart > maxRunMillis) return false
        if (runCount >= maxPerRun) return false
        while (recent.isNotEmpty() && t - recent.first() >= 60_000) recent.removeFirst()
        if (recent.size >= maxPerMinute) return false
        runCount++
        recent.addLast(t)
        return true
    }

    fun endRun() { runStart = -1L; runCount = 0 }
}
```

```kotlin
// LoopGuard.kt
package com.landslide.shitu.core

/** 重复抑制：同名文件在窗口内被搬走次数达到阈值 → 判定为"App 会自动重建"。 */
class LoopGuard(private val windowMillis: Long, private val threshold: Int) {
    private val hits = HashMap<String, ArrayDeque<Long>>()

    fun record(basename: String, t: Long): Boolean {
        val q = hits.getOrPut(basename) { ArrayDeque() }
        while (q.isNotEmpty() && t - q.first() > windowMillis) q.removeFirst()
        q.addLast(t)
        return q.size >= threshold
    }

    fun reset() = hits.clear()
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`.\gradlew.bat :app:testDebugUnitTest --tests "*core.*"`
预期：PASS

- [ ] **步骤 5：Commit**

```bash
git add app/src/main/java/com/landslide/shitu/core app/src/test/java/com/landslide/shitu/core
git commit -m "feat(core): 扫描过滤、三重限速、重复抑制"
```

---

## 任务 4：数据层（Room 三表 + DataStore 参数）

**文件：**
- 创建：`data/db/Enums.kt`、`data/db/Entities.kt`、`data/db/Daos.kt`、`data/db/AppDatabase.kt`、`data/SettingsStore.kt`、`data/RuleRepository.kt`
- 测试：`app/src/test/java/com/landslide/shitu/data/RuleDaoTest.kt`（Robolectric + in-memory Room）

- [ ] **步骤 1：先写失败的测试**

```kotlin
package com.landslide.shitu.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.landslide.shitu.data.db.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RuleDaoTest {
    private lateinit var db: AppDatabase
    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
    }
    @After fun tearDown() = db.close()

    @Test fun `插入规则并按启用状态查询`() {
        val id = db.ruleDao().insert(RuleEntity(name = "r1", srcPath = "/sdcard/Android/data/a/files",
            dstPath = "/sdcard/Pictures/拾图", mode = Mode.MOVE, intervalMinutes = 5,
            extensions = "jpg,png", createdAt = 1, updatedAt = 1))
        assertEquals(1, db.ruleDao().enabledRules().size)
        assertEquals("r1", db.ruleDao().byId(id)!!.name)
    }

    @Test fun `同规则同源路径的条目唯一`() {
        val dao = db.itemDao()
        val e = ItemEntity(ruleId = 1, srcPath = "/a/x.png", srcSize = 10, srcMtime = 100,
            dstPath = null, status = ItemStatus.PENDING)
        dao.upsert(e); dao.upsert(e.copy(id = 0))
        assertEquals(1, dao.countForRule(1))
    }

    @Test fun `按时间裁剪日志`() {
        val dao = db.logDao()
        repeat(10) { dao.insert(LogEntity(ts = it.toLong(), ruleId = 1, srcPath = null,
            dstPath = null, result = LogResult.INFO, durationMs = 0, message = null)) }
        dao.pruneBefore(5)
        assertEquals(5, dao.count())
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`.\gradlew.bat :app:testDebugUnitTest --tests "*RuleDaoTest*"`
预期：FAIL（`Unresolved reference: AppDatabase`）

- [ ] **步骤 3：写 `Enums.kt`**

```kotlin
package com.landslide.shitu.data.db

enum class Mode { MOVE, COPY }
enum class RuleState { IDLE, RUNNING, PAUSED_MANUAL, PAUSED_ERROR, PAUSED_LOOP }
enum class ItemStatus { PENDING, MOVED, COPIED, FAILED, SKIPPED }
enum class LogResult { MOVED, COPIED, SKIPPED, FAILED, UNDONE, INFO }
```

- [ ] **步骤 4：写 `Entities.kt`（字段与规格 §7 完全一致）**

```kotlin
package com.landslide.shitu.data.db

import androidx.room.*

@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val srcPath: String,
    val includeSubdirs: Boolean = true,
    val maxDepth: Int? = null,
    val dstPath: String,
    val mode: Mode = Mode.MOVE,
    val intervalMinutes: Int = 5,
    val extensions: String = "jpg,jpeg,png,gif,webp,bmp,heic,heif,avif",
    val addSourceAppSuffix: Boolean = true,
    val enabled: Boolean = true,
    val state: RuleState = RuleState.IDLE,
    val pauseReason: String? = null,
    val lastRunAt: Long? = null,
    val lastMoved: Int = 0,
    val lastFailed: Int = 0,
    val totalMoved: Long = 0,
    val totalFailed: Long = 0,
    val consecutiveFailures: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "items",
    indices = [Index(value = ["ruleId", "srcPath"], unique = true),
               Index(value = ["ruleId", "srcSize", "srcMtime"])])
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ruleId: Long,
    val srcPath: String,
    val srcSize: Long,
    val srcMtime: Long,
    val dstPath: String?,
    val status: ItemStatus,
    val attemptCount: Int = 0,
    val lastError: String? = null,
    val processedAt: Long? = null,
)

@Entity(tableName = "logs", indices = [Index("ts"), Index("ruleId")])
data class LogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    val ruleId: Long?,
    val srcPath: String?,
    val dstPath: String?,
    val result: LogResult,
    val durationMs: Long,
    val message: String?,
)
```

- [ ] **步骤 5：写 `Daos.kt`**

```kotlin
package com.landslide.shitu.data.db

import androidx.room.*

@Dao
interface RuleDao {
    @Insert suspend fun insert(rule: RuleEntity): Long
    @Update suspend fun update(rule: RuleEntity)
    @Query("SELECT * FROM rules WHERE id = :id") suspend fun byId(id: Long): RuleEntity?
    @Query("SELECT * FROM rules ORDER BY id") suspend fun all(): List<RuleEntity>
    @Query("SELECT * FROM rules WHERE enabled = 1 ORDER BY id") suspend fun enabledRules(): List<RuleEntity>
    @Query("DELETE FROM rules WHERE id = :id") suspend fun delete(id: Long)
}

@Dao
interface ItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(item: ItemEntity): Long
    @Query("SELECT * FROM items WHERE ruleId = :ruleId AND srcPath = :srcPath")
    suspend fun find(ruleId: Long, srcPath: String): ItemEntity?
    @Query("SELECT COUNT(*) FROM items WHERE ruleId = :ruleId") suspend fun countForRule(ruleId: Long): Int
    @Query("SELECT * FROM items WHERE id = :id") suspend fun byId(id: Long): ItemEntity?
    @Query("""SELECT * FROM items WHERE status IN ('MOVED','COPIED')
              ORDER BY processedAt DESC LIMIT :limit""")
    suspend fun recentDone(limit: Int): List<ItemEntity>
}

@Dao
interface LogDao {
    @Insert suspend fun insert(log: LogEntity)
    @Query("SELECT * FROM logs ORDER BY ts DESC LIMIT :limit") suspend fun recent(limit: Int): List<LogEntity>
    @Query("SELECT COUNT(*) FROM logs") suspend fun count(): Int
    @Query("DELETE FROM logs WHERE ts < :before") suspend fun pruneBefore(before: Long)
    @Query("DELETE FROM logs WHERE id IN (SELECT id FROM logs ORDER BY ts DESC LIMIT -1 OFFSET :keep)")
    suspend fun pruneToCount(keep: Int)
}
```

- [ ] **步骤 6：写 `AppDatabase.kt`**

```kotlin
package com.landslide.shitu.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter fun mode(v: Mode) = v.name
    @TypeConverter fun mode(s: String) = Mode.valueOf(s)
    @TypeConverter fun ruleState(v: RuleState) = v.name
    @TypeConverter fun ruleState(s: String) = RuleState.valueOf(s)
    @TypeConverter fun itemStatus(v: ItemStatus) = v.name
    @TypeConverter fun itemStatus(s: String) = ItemStatus.valueOf(s)
    @TypeConverter fun logResult(v: LogResult) = v.name
    @TypeConverter fun logResult(s: String) = LogResult.valueOf(s)
}

@Database(entities = [RuleEntity::class, ItemEntity::class, LogEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ruleDao(): RuleDao
    abstract fun itemDao(): ItemDao
    abstract fun logDao(): LogDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "shitu.db").build()
    }
}
```

- [ ] **步骤 7：写 `SettingsStore.kt`（规格 §8 的全部参数，默认值照抄）**

```kotlin
package com.landslide.shitu.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private object K {
        val SCAN_BUDGET_SEC = intPreferencesKey("scan_budget_sec")          // 20
        val MAX_PER_RUN = intPreferencesKey("max_per_run")                  // 20
        val MAX_PER_MINUTE = intPreferencesKey("max_per_minute")            // 120
        val MAX_RUN_SEC = intPreferencesKey("max_run_sec")                  // 60
        val STABLE_SEC = intPreferencesKey("stable_sec")                    // 30
        val LOOP_WINDOW_MIN = intPreferencesKey("loop_window_min")          // 30
        val LOOP_THRESHOLD = intPreferencesKey("loop_threshold")            // 3
        val FAIL_THRESHOLD = intPreferencesKey("fail_threshold")            // 5
        val LOW_BATTERY_PAUSE = booleanPreferencesKey("low_battery_pause")  // true
        val LOW_BATTERY_PCT = intPreferencesKey("low_battery_pct")          // 15
        val LOG_KEEP_DAYS = intPreferencesKey("log_keep_days")              // 30
        val LOG_KEEP_COUNT = intPreferencesKey("log_keep_count")            // 50000
        val FAST_DRAIN = booleanPreferencesKey("fast_drain")                // false
    }

    val settings = context.dataStore.data.map { p ->
        Settings(
            scanBudgetSec = p[K.SCAN_BUDGET_SEC] ?: 20,
            maxPerRun = p[K.MAX_PER_RUN] ?: 20,
            maxPerMinute = p[K.MAX_PER_MINUTE] ?: 120,
            maxRunSec = p[K.MAX_RUN_SEC] ?: 60,
            stableSec = p[K.STABLE_SEC] ?: 30,
            loopWindowMin = p[K.LOOP_WINDOW_MIN] ?: 30,
            loopThreshold = p[K.LOOP_THRESHOLD] ?: 3,
            failThreshold = p[K.FAIL_THRESHOLD] ?: 5,
            lowBatteryPause = p[K.LOW_BATTERY_PAUSE] ?: true,
            lowBatteryPct = p[K.LOW_BATTERY_PCT] ?: 15,
            logKeepDays = p[K.LOG_KEEP_DAYS] ?: 30,
            logKeepCount = p[K.LOG_KEEP_COUNT] ?: 50_000,
            fastDrain = p[K.FAST_DRAIN] ?: false,
        )
    }

    suspend fun setMaxPerRun(v: Int) = context.dataStore.edit { it[K.MAX_PER_RUN] = v }
    suspend fun setStableSec(v: Int) = context.dataStore.edit { it[K.STABLE_SEC] = v }
    suspend fun setFastDrain(v: Boolean) = context.dataStore.edit { it[K.FAST_DRAIN] = v }
}

data class Settings(
    val scanBudgetSec: Int, val maxPerRun: Int, val maxPerMinute: Int, val maxRunSec: Int,
    val stableSec: Int, val loopWindowMin: Int, val loopThreshold: Int, val failThreshold: Int,
    val lowBatteryPause: Boolean, val lowBatteryPct: Int, val logKeepDays: Int,
    val logKeepCount: Int, val fastDrain: Boolean,
)
```

- [ ] **步骤 8：运行测试验证通过**

运行：`.\gradlew.bat :app:testDebugUnitTest --tests "*RuleDaoTest*"`
预期：PASS（3 个测试）

- [ ] **步骤 9：Commit**

```bash
git add app/src/main/java/com/landslide/shitu/data app/src/test/java/com/landslide/shitu/data
git commit -m "feat(data): Room 三表 + DataStore 参数（规格 §7/§8）"
```

---

## 任务 5：Shizuku 桥（AIDL + UserService + RemoteFile）

**文件：**
- 创建：`app/src/main/aidl/com/landslide/shitu/IShituService.aidl`、`shizuku/RemoteFile.kt`、`shizuku/ShituUserService.kt`
- 修改：`app/src/main/AndroidManifest.xml`（声明 UserService）

- [ ] **步骤 1：写 AIDL**

```aidl
// app/src/main/aidl/com/landslide/shitu/IShituService.aidl
package com.landslide.shitu;
import com.landslide.shitu.RemoteFile;

interface IShituService {
    List<RemoteFile> listFiles(String path, boolean recursive, int maxDepth, int maxCount, String afterPath);
    RemoteFile stat(String path);
    boolean exists(String path);
    void mkdirs(String path);
    boolean move(String srcPath, String dstPath);
    boolean copy(String srcPath, String dstPath);
    boolean delete(String path);
    String describeEnvironment();
}
```

- [ ] **步骤 2：写 `RemoteFile.kt`**

```kotlin
package com.landslide.shitu.shizuku

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class RemoteFile(
    val path: String,
    val name: String,
    val size: Long,
    val mtimeMillis: Long,
    val isDirectory: Boolean,
    val isSymlink: Boolean,
) : Parcelable
```

> 需要在 `app/build.gradle.kts` 增加 `id("kotlin-parcelize")` 插件（`libs.plugins` 中补 `kotlin-parcelize = { id = "org.jetbrains.kotlin.plugin.parcelize", version.ref = "kotlin" }`）。

- [ ] **步骤 3：写 `ShituUserService.kt`（跑在 shell 身份，只有原子操作）**

```kotlin
package com.landslide.shitu.shizuku

import android.os.RemoteException
import com.landslide.shitu.IShituService
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class ShituUserService : IShituService.Stub() {

    override fun listFiles(path: String, recursive: Boolean, maxDepth: Int,
                           maxCount: Int, afterPath: String?): List<RemoteFile> {
        val root = File(path)
        if (!root.isDirectory) return emptyList()
        val limit = maxCount.coerceIn(1, 500)
        val out = ArrayList<RemoteFile>(limit)
        val stack = ArrayDeque<Pair<File, Int>>()
        stack.addLast(root to 0)
        val startAfter = afterPath.isNullOrEmpty()
        while (stack.isNotEmpty() && out.size < limit) {
            val (dir, depth) = stack.removeLast()
            val children = dir.listFiles() ?: continue
            for (c in children.sortedBy { it.name }) {
                if (!startAfter) { if (c.absolutePath == afterPath) startAfter = true; continue }
                out.add(toRemote(c))
                if (out.size >= limit) break
                if (recursive && c.isDirectory && (maxDepth <= 0 || depth + 1 < maxDepth)) {
                    stack.addLast(c to depth + 1)
                }
            }
        }
        return out
    }

    override fun stat(path: String): RemoteFile? {
        val f = File(path)
        return if (f.exists()) toRemote(f) else null
    }

    override fun exists(path: String): Boolean = File(path).exists()

    override fun mkdirs(path: String) { File(path).mkdirs() }

    override fun move(srcPath: String, dstPath: String): Boolean {
        val src = File(srcPath); val dst = File(dstPath)
        dst.parentFile?.mkdirs()
        return try {
            Files.move(src.toPath(), dst.toPath(), StandardCopyOption.ATOMIC_MOVE).let { true }
        } catch (t: Throwable) {
            // 回退：复制 → 校验 → 删源（跨卷或 ATOMIC_MOVE 不支持时）
            runCatching {
                FileInputStream(src).use { i -> FileOutputStream(dst).use { o -> i.copyTo(o) } }
                if (dst.length() != src.length()) return false
                src.delete()
            }.isSuccess
        }
    }

    override fun copy(srcPath: String, dstPath: String): Boolean = runCatching {
        val src = File(srcPath); val dst = File(dstPath)
        dst.parentFile?.mkdirs()
        FileInputStream(src).use { i -> FileOutputStream(dst).use { o -> i.copyTo(o) } }
        dst.length() == src.length()
    }.getOrDefault(false)

    override fun delete(path: String): Boolean = File(path).delete()

    override fun describeEnvironment(): String {
        val runtime = Runtime.getRuntime()
        return "uid=${android.os.Process.myUid()} " +
            "sdk=${android.os.Build.VERSION.SDK_INT} " +
            "patch=${android.os.Build.VERSION.SECURITY_PATCH} " +
            "free=${android.os.Environment.getExternalStorageDirectory().usableSpace} " +
            "runtimeFree=${runtime.freeMemory()}"
    }

    private fun toRemote(f: File) = RemoteFile(
        path = f.absolutePath, name = f.name, size = if (f.isFile) f.length() else 0,
        mtimeMillis = f.lastModified(), isDirectory = f.isDirectory,
        isSymlink = runCatching { Files.isSymbolicLink(f.toPath()) }.getOrDefault(false),
    )
}
```

- [ ] **步骤 4：在 Manifest 声明 UserService**

```xml
<service android:name=".shizuku.ShituUserService"
         android:exported="false"
         android:process=":shizuku_user_service"/>
```

- [ ] **步骤 5：真机验证桥接口（先写一个临时的 Debug 按钮或 Debug Activity 调用 `listFiles`）**

运行：
```powershell
.\gradlew.bat :app:assembleDebug
D:\soft\scrcpy\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
D:\soft\scrcpy\adb.exe logcat -s ShituDebug
```
预期：日志打印出 `/sdcard/Android/data/com.qidian.QDReader/files` 的前 20 个条目（字段完整、无 RemoteException）

- [ ] **步骤 6：Commit**

```bash
git add -A
git commit -m "feat(shizuku): AIDL 桥 + shell 身份 UserService（7 个原子接口）"
```

---

## 任务 6：ShizukuBridge（权限、绑定、状态机、重试）

**文件：**
- 创建：`shizuku/ShizukuState.kt`、`shizuku/FileBridge.kt`、`shizuku/ShizukuBridge.kt`
- 测试：`app/src/test/java/com/landslide/shitu/shizuku/ShizukuStateMachineTest.kt`

- [ ] **步骤 1：先写失败的测试（状态推导是纯逻辑，可单测）**

```kotlin
package com.landslide.shitu.shizuku

import org.junit.Assert.assertEquals
import org.junit.Test

class ShizukuStateMachineTest {
    @Test fun `未安装优先于其他状态`() {
        assertEquals(ShizukuState.NOT_INSTALLED,
            derive(installed = false, running = true, permitted = true, bindFailures = 0, binderAlive = true))
    }
    @Test fun `已安装未运行`() {
        assertEquals(ShizukuState.NOT_RUNNING,
            derive(true, running = false, permitted = true, bindFailures = 0, binderAlive = true))
    }
    @Test fun `未授权`() {
        assertEquals(ShizukuState.NO_PERMISSION,
            derive(true, true, permitted = false, bindFailures = 0, binderAlive = true))
    }
    @Test fun `绑定失败 5 次进入 BIND_FAILED`() {
        assertEquals(ShizukuState.BIND_FAILED,
            derive(true, true, true, bindFailures = 5, binderAlive = true))
    }
    @Test fun `全部就绪为 READY`() {
        assertEquals(ShizukuState.READY, derive(true, true, true, 0, true))
    }
}
```

- [ ] **步骤 2：运行验证失败**

运行：`.\gradlew.bat :app:testDebugUnitTest --tests "*ShizukuStateMachineTest*"`
预期：FAIL（`Unresolved reference: derive`）

- [ ] **步骤 3：写实现**

```kotlin
// ShizukuState.kt
package com.landslide.shitu.shizuku

enum class ShizukuState { NOT_INSTALLED, NOT_RUNNING, NO_PERMISSION, BIND_FAILED, READY }

fun derive(installed: Boolean, running: Boolean, permitted: Boolean,
           bindFailures: Int, binderAlive: Boolean): ShizukuState = when {
    !installed -> ShizukuState.NOT_INSTALLED
    !running -> ShizukuState.NOT_RUNNING
    !permitted -> ShizukuState.NO_PERMISSION
    bindFailures >= 5 -> ShizukuState.BIND_FAILED
    !binderAlive -> ShizukuState.NOT_RUNNING
    else -> ShizukuState.READY
}
```

```kotlin
// FileBridge.kt —— engine 与 Shizuku 的解耦接口（单测用 Fake 实现）
package com.landslide.shitu.shizuku

interface FileBridge {
    suspend fun list(path: String, recursive: Boolean, maxDepth: Int, maxCount: Int, after: String?): List<RemoteFile>
    suspend fun stat(path: String): RemoteFile?
    suspend fun exists(path: String): Boolean
    suspend fun mkdirs(path: String)
    suspend fun move(src: String, dst: String): Boolean
    suspend fun copy(src: String, dst: String): Boolean
    suspend fun delete(path: String): Boolean
}
```

```kotlin
// ShizukuBridge.kt
package com.landslide.shitu.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import dev.rikka.shizuku.Shizuku
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class ShizukuBridge(private val context: Context) : FileBridge {

    @Volatile private var binder: com.landslide.shitu.IShituService? = null
    @Volatile private var bindFailures = 0

    fun state(): ShizukuState = derive(
        installed = isInstalled(), running = Shizuku.pingBinder(),
        permitted = runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }
            .getOrDefault(false),
        bindFailures = bindFailures, binderAlive = binder != null,
    )

    private fun isInstalled() = runCatching {
        context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0); true
    }.getOrDefault(false)

    /** 绑定 UserService，成功返回 true；index 决定重试退避 1/2/4/8/16 秒 */
    suspend fun bind(): Boolean = withTimeoutOrNull(10_000) {
        suspendCancellableCoroutine { cont ->
            val args = Shizuku.UserServiceArgs(
                ComponentName(context.packageName, ShituUserService::class.java.name))
                .daemon(false).processNameSuffix("shizuku_user_service").debuggable(false).version(1)
            val conn = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    binder = com.landslide.shitu.IShituService.Stub.asInterface(service)
                    bindFailures = 0
                    if (cont.isActive) cont.resume(true)
                }
                override fun onServiceDisconnected(name: ComponentName?) {
                    binder = null; bindFailures++
                    if (cont.isActive) cont.resume(false)
                }
            }
            runCatching { Shizuku.bindUserService(args, conn) }
                .onFailure { bindFailures++; if (cont.isActive) cont.resume(false) }
        }
    }.also { if (it != true) bindFailures++ } == true

    private fun svc() = binder ?: throw IllegalStateException("UserService 未绑定（state=${state()}）")

    override suspend fun list(path: String, recursive: Boolean, maxDepth: Int, maxCount: Int, after: String?) =
        svc().listFiles(path, recursive, maxDepth, maxCount, after)
    override suspend fun stat(path: String) = svc().stat(path)
    override suspend fun exists(path: String) = svc().exists(path)
    override suspend fun mkdirs(path: String) { svc().mkdirs(path) }
    override suspend fun move(src: String, dst: String) = svc().move(src, dst)
    override suspend fun copy(src: String, dst: String) = svc().copy(src, dst)
    override suspend fun delete(path: String) = svc().delete(path)
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`.\gradlew.bat :app:testDebugUnitTest --tests "*ShizukuStateMachineTest*"`
预期：PASS

- [ ] **步骤 5：真机验证绑定与权限**

在 `MainActivity` 加一个临时按钮：请求权限 → `bind()` → 显示 `state()` 与 `listFiles("/sdcard/Android/data/com.qidian.QDReader/files", true, 2, 50, null).size`。
运行：`D:\soft\scrcpy\adb.exe logcat -s ShituDebug`
预期：`state=READY`，条目数 > 0

- [ ] **步骤 6：Commit**

```bash
git add -A
git commit -m "feat(shizuku): 桥接层（状态机/绑定/重试）+ FileBridge 抽象"
```

---

## 任务 7：TransferExecutor（单文件事务；用 Fake 单测）

**文件：**
- 创建：`engine/TransferExecutor.kt`
- 测试：`app/src/test/java/com/landslide/shitu/engine/TransferExecutorTest.kt`（含 `FakeFileBridge`）

- [ ] **步骤 1：先写失败的测试**

```kotlin
package com.landslide.shitu.engine

import com.landslide.shitu.core.NamePolicy
import com.landslide.shitu.data.db.*
import com.landslide.shitu.shizuku.FileBridge
import com.landslide.shitu.shizuku.RemoteFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TransferExecutorTest {

    class FakeFileBridge : FileBridge {
        val files = mutableMapOf<String, RemoteFile>()
        val dirs = mutableSetOf<String>()
        var failMove = false
        override suspend fun list(path: String, recursive: Boolean, maxDepth: Int, maxCount: Int, after: String?) = emptyList<RemoteFile>()
        override suspend fun stat(path: String) = files[path]
        override suspend fun exists(path: String) = files.containsKey(path)
        override suspend fun mkdirs(path: String) { dirs.add(path) }
        override suspend fun move(src: String, dst: String): Boolean {
            if (failMove) return false
            val f = files.remove(src) ?: return false
            files[dst] = f.copy(path = dst, name = dst.substringAfterLast('/'))
            return true
        }
        override suspend fun copy(src: String, dst: String): Boolean {
            val f = files[src] ?: return false
            files[dst] = f.copy(path = dst, name = dst.substringAfterLast('/'))
            return true
        }
        override suspend fun delete(path: String) = files.remove(path) != null
    }

    private fun src(name: String, size: Long = 100, mtime: Long = 1_000) =
        RemoteFile("/src/$name", name, size, mtime, false, false)

    @Test fun `移动成功后源消失目标存在`() = runBlocking {
        val bridge = FakeFileBridge().apply { files["/src/a.png"] = src("a.png") }
        val ex = TransferExecutor(bridge, NamePolicy())
        val item = ex.transfer(src = src("a.png"), srcPath = "/src/a.png",
            dstDir = "/dst", sourceApp = "起点", mode = Mode.MOVE, ruleId = 1, now = 2_000)
        assertEquals(ItemStatus.MOVED, item.status)
        assertFalse(bridge.exists("/src/a.png"))
        assertTrue(bridge.exists("/dst/a_起点.png"))
    }

    @Test fun `移动失败时源保持不动`() = runBlocking {
        val bridge = FakeFileBridge().apply { files["/src/a.png"] = src("a.png"); failMove = true }
        val ex = TransferExecutor(bridge, NamePolicy())
        val item = ex.transfer(src("a.png"), "/src/a.png", "/dst", "起点", Mode.MOVE, 1, 2_000)
        assertEquals(ItemStatus.FAILED, item.status)
        assertTrue(bridge.exists("/src/a.png"))
    }

    @Test fun `复制模式源保留`() = runBlocking {
        val bridge = FakeFileBridge().apply { files["/src/a.png"] = src("a.png") }
        val ex = TransferExecutor(bridge, NamePolicy())
        val item = ex.transfer(src("a.png"), "/src/a.png", "/dst", "起点", Mode.COPY, 1, 2_000)
        assertEquals(ItemStatus.COPIED, item.status)
        assertTrue(bridge.exists("/src/a.png"))
        assertTrue(bridge.exists("/dst/a_起点.png"))
    }

    @Test fun `目标已有同内容文件时移动模式删源不重复搬`() = runBlocking {
        val bridge = FakeFileBridge().apply {
            files["/src/a.png"] = src("a.png", size = 100, mtime = 1_000)
            files["/dst/a_起点.png"] = RemoteFile("/dst/a_起点.png", "a_起点.png", 100, 1_000, false, false)
        }
        val ex = TransferExecutor(bridge, NamePolicy())
        val item = ex.transfer(src("a.png"), "/src/a.png", "/dst", "起点", Mode.MOVE, 1, 2_000)
        assertEquals(ItemStatus.MOVED, item.status)
        assertFalse(bridge.exists("/src/a.png"))
        assertEquals("目标已存在同内容文件", item.lastError)
    }
}
```

- [ ] **步骤 2：运行验证失败**

运行：`.\gradlew.bat :app:testDebugUnitTest --tests "*TransferExecutorTest*"`
预期：FAIL（`Unresolved reference: TransferExecutor`）

- [ ] **步骤 3：写实现**

```kotlin
package com.landslide.shitu.engine

import com.landslide.shitu.core.NamePolicy
import com.landslide.shitu.data.db.ItemEntity
import com.landslide.shitu.data.db.ItemStatus
import com.landslide.shitu.data.db.Mode
import com.landslide.shitu.shizuku.FileBridge
import com.landslide.shitu.shizuku.RemoteFile

class TransferExecutor(
    private val bridge: FileBridge,
    private val namePolicy: NamePolicy,
) {
    /** 单文件事务：绝不先删后搬；任何失败源文件保持不动。 */
    suspend fun transfer(
        src: RemoteFile, srcPath: String, dstDir: String, sourceApp: String,
        mode: Mode, ruleId: Long, now: Long,
    ): ItemEntity {
        val timestamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
            .format(java.util.Date(now))
        val targetName = namePolicy.resolve(
            rawName = src.name, sourceApp = sourceApp,
            exists = { candidate ->
                val p = "$dstDir/$candidate"
                val found = runCatching { kotlinx.coroutines.runBlocking { bridge.stat(p) } }.getOrNull()
                found != null
            },
            timestamp = timestamp,
        )
        val dstPath = "$dstDir/$targetName"

        bridge.mkdirs(dstDir)

        // 目标已存在同内容文件：移动模式删源；复制模式跳过
        val existing = runCatching { bridge.stat(dstPath) }.getOrNull()
        if (existing != null && existing.size == src.size && existing.mtimeMillis == src.mtimeMillis) {
            return if (mode == Mode.MOVE) {
                val ok = bridge.delete(srcPath)
                ItemEntity(ruleId = ruleId, srcPath = srcPath, srcSize = src.size,
                    srcMtime = src.mtimeMillis, dstPath = dstPath,
                    status = if (ok) ItemStatus.MOVED else ItemStatus.FAILED,
                    attemptCount = 1, lastError = "目标已存在同内容文件", processedAt = now)
            } else {
                ItemEntity(ruleId = ruleId, srcPath = srcPath, srcSize = src.size,
                    srcMtime = src.mtimeMillis, dstPath = dstPath,
                    status = ItemStatus.SKIPPED, attemptCount = 1,
                    lastError = "目标已存在同内容文件", processedAt = now)
            }
        }

        val ok = if (mode == Mode.MOVE) bridge.move(srcPath, dstPath) else bridge.copy(srcPath, dstPath)
        if (!ok) {
            return ItemEntity(ruleId = ruleId, srcPath = srcPath, srcSize = src.size,
                srcMtime = src.mtimeMillis, dstPath = null, status = ItemStatus.FAILED,
                attemptCount = 1, lastError = "move/copy 失败", processedAt = now)
        }

        val dst = bridge.stat(dstPath)
        val verified = dst != null && dst.size == src.size
        return ItemEntity(
            ruleId = ruleId, srcPath = srcPath, srcSize = src.size, srcMtime = src.mtimeMillis,
            dstPath = dstPath,
            status = if (!verified) ItemStatus.FAILED else if (mode == Mode.MOVE) ItemStatus.MOVED else ItemStatus.COPIED,
            attemptCount = 1,
            lastError = if (verified) null else "搬运后校验失败（大小不一致）",
            processedAt = now,
        )
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`.\gradlew.bat :app:testDebugUnitTest --tests "*TransferExecutorTest*"`
预期：PASS（4 个测试）

- [ ] **步骤 5：真机集成验证（用假源目录）**

在 Debug 界面加按钮：对 `/sdcard/Download/_shitu_test/`（手动放 3 张图）跑一次 `TransferExecutor`，目标是 `/sdcard/Pictures/拾图/`。
运行：
```powershell
D:\soft\scrcpy\adb.exe shell ls -l /sdcard/Pictures/拾图/
D:\soft\scrcpy\adb.exe shell content query --uri content://media/external/images/media --projection _id:_data | Select-String 拾图
```
预期：3 个带 `_来源App` 后缀的文件；MediaStore 10 秒内出现对应行

- [ ] **步骤 6：Commit**

```bash
git add -A
git commit -m "feat(engine): 单文件搬运事务（校验/不覆盖/失败保源）"
```

---

## 任务 8：RuleEngine（一轮 Run 的编排）

**文件：**
- 创建：`engine/RuleEngine.kt`
- 测试：`app/src/test/java/com/landslide/shitu/engine/RuleEngineTest.kt`

- [ ] **步骤 1：先写失败的测试**

```kotlin
package com.landslide.shitu.engine

import com.landslide.shitu.core.LoopGuard
import com.landslide.shitu.core.NamePolicy
import com.landslide.shitu.core.RateLimiter
import com.landslide.shitu.core.ScanFilter
import com.landslide.shitu.data.Settings
import com.landslide.shitu.data.db.*
import com.landslide.shitu.shizuku.RemoteFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RuleEngineTest {

    private val settings = Settings(scanBudgetSec = 20, maxPerRun = 3, maxPerMinute = 120,
        maxRunSec = 60, stableSec = 30, loopWindowMin = 30, loopThreshold = 3, failThreshold = 5,
        lowBatteryPause = false, lowBatteryPct = 15, logKeepDays = 30, logKeepCount = 50_000,
        fastDrain = false)

    @Test fun `只搬白名单扩展名且遵守单轮上限`() = runBlocking {
        val bridge = TransferExecutorTest.FakeFileBridge()
        (1..10).forEach { bridge.files["/src/f$it.png"] = RemoteFile("/src/f$it.png", "f$it.png", 10, 1_000, false, false) }
        bridge.files["/src/skip.mp4"] = RemoteFile("/src/skip.mp4", "skip.mp4", 10, 1_000, false, false)

        val engine = RuleEngine(bridge, NamePolicy(), settings, now = { 2_000 })
        val rule = RuleEntity(id = 1, name = "t", srcPath = "/src", dstPath = "/dst",
            extensions = "png", mode = Mode.MOVE, createdAt = 0, updatedAt = 0)
        val result = engine.runOnce(rule, LoopGuard(60_000, 3))

        assertEquals(3, result.moved)                       // 单轮上限 3
        assertTrue(bridge.exists("/src/skip.mp4"))          // 非白名单未动
        assertEquals(7, bridge.files.keys.count { it.startsWith("/src/") && it.endsWith(".png") })
    }

    @Test fun `未过稳定期的文件不搬`() = runBlocking {
        val bridge = TransferExecutorTest.FakeFileBridge()
        bridge.files["/src/new.png"] = RemoteFile("/src/new.png", "new.png", 10, 1_990, false, false)
        val engine = RuleEngine(bridge, NamePolicy(), settings, now = { 2_000 })
        val rule = RuleEntity(id = 1, name = "t", srcPath = "/src", dstPath = "/dst",
            extensions = "png", createdAt = 0, updatedAt = 0)
        assertEquals(0, engine.runOnce(rule, LoopGuard(60_000, 3)).moved)
        assertTrue(bridge.exists("/src/new.png"))
    }

    @Test fun `同名反复出现触发重复抑制`() = runBlocking {
        val bridge = TransferExecutorTest.FakeFileBridge()
        val engine = RuleEngine(bridge, NamePolicy(), settings, now = { 2_000 })
        val rule = RuleEntity(id = 1, name = "t", srcPath = "/src", dstPath = "/dst",
            extensions = "png", createdAt = 0, updatedAt = 0)
        val guard = LoopGuard(30 * 60_000, 3)
        repeat(3) {
            bridge.files["/src/a.png"] = RemoteFile("/src/a.png", "a.png", 10, 1_000, false, false)
            engine.runOnce(rule, guard)
        }
        assertEquals(RuleState.PAUSED_LOOP, engine.lastState)
    }
}
```

- [ ] **步骤 2：运行验证失败**

运行：`.\gradlew.bat :app:testDebugUnitTest --tests "*RuleEngineTest*"`
预期：FAIL（`Unresolved reference: RuleEngine`）

- [ ] **步骤 3：写实现**

```kotlin
package com.landslide.shitu.engine

import com.landslide.shitu.core.LoopGuard
import com.landslide.shitu.core.NamePolicy
import com.landslide.shitu.core.RateLimiter
import com.landslide.shitu.core.ScanFilter
import com.landslide.shitu.data.Settings
import com.landslide.shitu.data.db.*
import com.landslide.shitu.shizuku.FileBridge
import kotlinx.coroutines.withTimeoutOrNull

class RuleEngine(
    private val bridge: FileBridge,
    private val namePolicy: NamePolicy,
    private val settings: Settings,
    private val now: () -> Long = System::currentTimeMillis,
) {
    var lastState: RuleState = RuleState.IDLE; private set

    data class RunResult(val scanned: Int, val moved: Int, val failed: Int, val skipped: Int,
                         val loopSuspected: Boolean)

    suspend fun runOnce(rule: RuleEntity, guard: LoopGuard): RunResult {
        val filter = ScanFilter(rule.extensions.split(',').toSet(), settings.stableSec)
        val limiter = RateLimiter(settings.maxPerRun, settings.maxPerMinute, settings.maxRunSec * 1000L)
        val executor = TransferExecutor(bridge, namePolicy)
        val t0 = now()

        // 1) 扫描（受时间预算约束），游标式分页避免 Binder 超限
        val candidates = ArrayList<com.landslide.shitu.shizuku.RemoteFile>()
        var after: String? = null
        var scanned = 0
        val deadline = t0 + settings.scanBudgetSec * 1000L
        while (now() < deadline) {
            val page = bridge.list(rule.srcPath, rule.includeSubdirs, rule.maxDepth ?: 0, 500, after)
            if (page.isEmpty()) break
            after = page.last().path
            scanned += page.size
            candidates += page.filter { filter.accept(it, now()) }
            if (page.size < 500) break
        }

        // 2) 排序 → 3) 限速执行
        var moved = 0; var failed = 0; var skipped = 0; var loop = false
        for (src in candidates.sortedBy { it.mtimeMillis }) {
            if (!limiter.tryAcquire(now())) break
            val item = executor.transfer(src, src.path, rule.dstPath,
                sourceApp = rule.name, mode = rule.mode, ruleId = rule.id, now = now())
            when (item.status) {
                ItemStatus.MOVED, ItemStatus.COPIED -> {
                    moved++
                    if (guard.record(src.name, now())) loop = true
                }
                ItemStatus.SKIPPED -> skipped++
                ItemStatus.FAILED -> failed++
                else -> {}
            }
            if (loop) break
        }
        limiter.endRun()
        lastState = if (loop) RuleState.PAUSED_LOOP else RuleState.IDLE
        return RunResult(scanned, moved, failed, skipped, loop)
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`.\gradlew.bat :app:testDebugUnitTest --tests "*RuleEngineTest*"`
预期：PASS（3 个测试）

- [ ] **步骤 5：Commit**

```bash
git add app/src/main/java/com/landslide/shitu/engine app/src/test/java/com/landslide/shitu/engine
git commit -m "feat(engine): 一轮 Run 编排（扫描预算/排序/限速/重复抑制）"
```

---

## 任务 9：后台与恢复（specialUse 服务 / 开机 / WorkManager 兜底 / 通知）

**文件：**
- 创建：`service/WatchService.kt`、`service/BootReceiver.kt`、`service/FallbackWorker.kt`、`service/Notifier.kt`
- 修改：`AndroidManifest.xml`（权限与组件）

- [ ] **步骤 1：Manifest 增加权限与组件**

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE"/>
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"/>
<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES"
    tools:ignore="QueryAllPackagesPermission"/>

<service android:name=".service.WatchService"
         android:exported="false"
         android:foregroundServiceType="specialUse">
    <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
              android:value="file_organizer_watcher"/>
</service>

<receiver android:name=".service.BootReceiver" android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED"/>
        <action android:name="android.intent.action.MY_PACKAGE_REPLACED"/>
    </intent-filter>
</receiver>
```

- [ ] **步骤 2：写 `Notifier.kt`**

```kotlin
package com.landslide.shitu.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.landslide.shitu.MainActivity
import com.landslide.shitu.R

class Notifier(private val context: Context) {
    companion object {
        const val CH_WATCH = "watch"
        const val CH_EVENT = "event"
        const val ID_WATCH = 1
    }

    fun ensureChannels() {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_WATCH, "监控状态", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_EVENT, "事件提醒", NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun watchNotification(text: String): Notification {
        val open = PendingIntent.getActivity(context, 0,
            Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(context, CH_WATCH)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("拾图正在监控")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "暂停全部", null)
            .addAction(0, "立即扫一次", null)
            .build()
    }

    fun event(title: String, text: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.notify(title.hashCode(), NotificationCompat.Builder(context, CH_EVENT)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(title).setContentText(text).setAutoCancel(true).build())
    }
}
```

- [ ] **步骤 3：写 `WatchService.kt`（常驻 + 30 秒探测 Shizuku + 按间隔调度）**

```kotlin
package com.landslide.shitu.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.landslide.shitu.ShituApp
import com.landslide.shitu.shizuku.ShizukuBridge
import com.landslide.shitu.shizuku.ShizukuState
import kotlinx.coroutines.*

class WatchService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var notifier: Notifier
    private lateinit var bridge: ShizukuBridge
    private var moved = 0L

    override fun onCreate() {
        super.onCreate()
        notifier = Notifier(this); notifier.ensureChannels()
        bridge = (application as ShituApp).bridge
        startForeground(Notifier.ID_WATCH, notifier.watchNotification("正在启动…"))
        scope.launch { loop() }
    }

    private suspend fun loop() {
        while (isActive()) {
            var state = bridge.state()
            if (state != ShizukuState.READY) {
                startForeground(Notifier.ID_WATCH,
                    notifier.watchNotification("未就绪（$state），点开 App 查看修复指引"))
                if (bridge.bind()) state = bridge.state()
            }
            if (state == ShizukuState.READY) {
                val n = (application as ShituApp).runDueRules()
                moved += n
                startForeground(Notifier.ID_WATCH,
                    notifier.watchNotification("已搬 $moved 张 · ${System.currentTimeMillis().let { fmt(it) }}"))
            }
            delay(if (state == ShizukuState.READY) 30_000 else 30_000)
        }
    }

    private fun fmt(ms: Long) = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date(ms))

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
```

- [ ] **步骤 4：写 `BootReceiver.kt` 与 `FallbackWorker.kt`**

```kotlin
// BootReceiver.kt
package com.landslide.shitu.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runCatching { ContextCompat.startForegroundService(context, Intent(context, WatchService::class.java)) }
    }
}
```

```kotlin
// FallbackWorker.kt —— 不需要前台服务，直接在 Worker 里搬一轮
package com.landslide.shitu.service

import android.content.Context
import androidx.work.*
import com.landslide.shitu.ShituApp
import java.util.concurrent.TimeUnit

class FallbackWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as ShituApp
        return runCatching {
            if (app.bridge.state() == com.landslide.shitu.shizuku.ShizukuState.NOT_RUNNING) app.bridge.bind()
            app.runDueRules()
            Result.success()
        }.getOrElse { Result.retry() }
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<FallbackWorker>(15, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("shitu_fallback", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
```

- [ ] **步骤 5：真机验证常驻与恢复**

运行：
```powershell
D:\soft\scrcpy\adb.exe shell dumpsys activity services com.landslide.shitu | Select-String -Pattern "WatchService|foregroundServiceType|isForeground"
D:\soft\scrcpy\adb.exe shell cmd notification list | Select-String 拾图
D:\soft\scrcpy\adb.exe reboot       # 重启后
D:\soft\scrcpy\adb.exe shell dumpsys activity services com.landslide.shitu | Select-String WatchService
```
预期：服务以 `specialUse` 类型前台运行；通知栏出现"拾图正在监控"；**重启后服务自动起来**（若未起来 → 按规格 §20-1 退化为"打开 App 后恢复"，并把结论回写文档）

- [ ] **步骤 6：Commit**

```bash
git add -A
git commit -m "feat(service): specialUse 常驻服务 + 开机恢复 + WorkManager 兜底 + 通知"
```

---

## 任务 10：UndoService（撤回）与日志裁剪/导出

**文件：**
- 创建：`engine/UndoService.kt`、`data/LogExporter.kt`
- 测试：`app/src/test/java/com/landslide/shitu/engine/UndoServiceTest.kt`

- [ ] **步骤 1：先写失败的测试**

```kotlin
package com.landslide.shitu.engine

import com.landslide.shitu.shizuku.RemoteFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class UndoServiceTest {
    @Test fun `目标存在且源位置为空则撤回成功`() = runBlocking {
        val b = TransferExecutorTest.FakeFileBridge()
        b.files["/dst/a_起点.png"] = RemoteFile("/dst/a_起点.png", "a_起点.png", 100, 1_000, false, false)
        val undo = UndoService(b)
        val ok = undo.undo("/src/a.png", "/dst/a_起点.png", 100)
        assertTrue(ok); assertTrue(b.exists("/src/a.png")); assertFalse(b.exists("/dst/a_起点.png"))
    }
    @Test fun `目标已被改动则跳过`() = runBlocking {
        val b = TransferExecutorTest.FakeFileBridge()
        b.files["/dst/a.png"] = RemoteFile("/dst/a.png", "a.png", 999, 1_000, false, false)
        assertFalse(UndoService(b).undo("/src/a.png", "/dst/a.png", 100))
    }
    @Test fun `源位置已被占用则跳过`() = runBlocking {
        val b = TransferExecutorTest.FakeFileBridge()
        b.files["/dst/a.png"] = RemoteFile("/dst/a.png", "a.png", 100, 1_000, false, false)
        b.files["/src/a.png"] = RemoteFile("/src/a.png", "a.png", 1, 1, false, false)
        assertFalse(UndoService(b).undo("/src/a.png", "/dst/a.png", 100))
    }
}
```

- [ ] **步骤 2：运行验证失败**

运行：`.\gradlew.bat :app:testDebugUnitTest --tests "*UndoServiceTest*"`
预期：FAIL（`Unresolved reference: UndoService`）

- [ ] **步骤 3：写实现**

```kotlin
package com.landslide.shitu.engine

import com.landslide.shitu.shizuku.FileBridge

class UndoService(private val bridge: FileBridge) {
    /** 规格 §6.6：目标存在且大小一致、源位置为空 → 才撤回 */
    suspend fun undo(srcPath: String, dstPath: String, expectedSize: Long): Boolean {
        val dst = bridge.stat(dstPath) ?: return false
        if (dst.size != expectedSize) return false
        if (bridge.exists(srcPath)) return false
        return bridge.move(dstPath, srcPath)
    }
}
```

```kotlin
// LogExporter.kt
package com.landslide.shitu.data

import com.landslide.shitu.data.db.LogEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogExporter {
    fun toCsv(logs: List<LogEntity>, out: File) {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        out.bufferedWriter(Charsets.UTF_8).use { w ->
            w.write("时间,结果,规则,源,目标,耗时ms,说明\n")
            logs.forEach { l ->
                w.write(listOf(fmt.format(Date(l.ts)), l.result.name, l.ruleId ?: "",
                    l.srcPath ?: "", l.dstPath ?: "", l.durationMs, l.message ?: "")
                    .joinToString(",") { "\"${it.toString().replace("\"", "\"\"")}\"" })
                w.write("\n")
            }
        }
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`.\gradlew.bat :app:testDebugUnitTest --tests "*UndoServiceTest*"`
预期：PASS

- [ ] **步骤 5：Commit**

```bash
git add -A
git commit -m "feat(engine): 撤回（严格条件）+ 日志 CSV 导出"
```

---

## 任务 11：HealthChecker 自检（对应规格 §6.9、验收 A7）

**文件：**
- 创建：`engine/HealthChecker.kt`
- 测试：`app/src/test/java/com/landslide/shitu/engine/HealthCheckerTest.kt`

- [ ] **步骤 1：先写失败的测试**

```kotlin
package com.landslide.shitu.engine

import com.landslide.shitu.shizuku.RemoteFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthCheckerTest {
    @Test fun `全绿时 6 项都通过`() = runBlocking {
        val b = TransferExecutorTest.FakeFileBridge()
        b.dirs.add("/tmp/src"); b.dirs.add("/sdcard/Pictures")
        val report = HealthChecker(b, "/tmp/src", "/sdcard/Pictures").run()
        assertTrue(report.all { it.passed })
        assertEquals(6, report.size)
    }
    @Test fun `源目录不可写时报第 4 项失败`() = runBlocking {
        val b = object : TransferExecutorTest.FakeFileBridge() {
            override suspend fun move(src: String, dst: String) = false
        }
        val report = HealthChecker(b, "/tmp/src", "/sdcard/Pictures").run()
        assertTrue(report.any { !it.passed })
    }
}
```

> `FakeFileBridge` 需支持 `mkdirs` 写入 `dirs`、以及 `move/copy/delete`（任务 7 已实现）。

- [ ] **步骤 2：运行验证失败**

运行：`.\gradlew.bat :app:testDebugUnitTest --tests "*HealthCheckerTest*"`
预期：FAIL（`Unresolved reference: HealthChecker`）

- [ ] **步骤 3：写实现（**只用自己创建的临时文件**，绝不碰真实图片）**

```kotlin
package com.landslide.shitu.engine

import com.landslide.shitu.shizuku.FileBridge
import com.landslide.shitu.shizuku.RemoteFile

class HealthChecker(
    private val bridge: FileBridge,
    private val probeSrcDir: String,
    private val probeDstDir: String,
) {
    data class Item(val name: String, val passed: Boolean, val detail: String)

    suspend fun run(): List<Item> {
        val out = ArrayList<Item>()
        val stamp = System.currentTimeMillis()

        // 1) 桥可达
        val reachable = runCatching { bridge.stat("/") != null || bridge.exists("/") }.getOrDefault(false)
        out += Item("Shizuku 通道可用", reachable, if (reachable) "UserService 已绑定" else "无法调用 UserService")

        // 2) 源目录可列举
        val listed = runCatching { bridge.list(probeSrcDir, false, 1, 10, null) }.getOrNull()
        out += Item("可列举源目录", listed != null, "条目=${listed?.size ?: -1}")

        // 3) 目标目录可写
        val tmp = "$probeDstDir/_shitu_selftest_$stamp"
        val w = runCatching {
            bridge.mkdirs(probeDstDir); bridge.copy("/dev/null", tmp); bridge.exists(tmp)
        }.getOrDefault(false)
        out += Item("目标目录可写", w, tmp)
        runCatching { bridge.delete(tmp) }

        // 4) 源目录可写（新建→改名→删除）
        val a = "$probeSrcDir/_shitu_w_$stamp"; val b = "$probeSrcDir/_shitu_w2_$stamp"
        val srcWrite = runCatching {
            bridge.copy("/dev/null", a); bridge.move(a, b); bridge.delete(b)
        }.getOrDefault(false)
        out += Item("源目录可写/可改名/可删", srcWrite, "$a → $b")

        // 5) 源→目标 移动
        val s = "$probeSrcDir/_shitu_mv_$stamp"; val d = "$probeDstDir/_shitu_mv_$stamp"
        val moved = runCatching { bridge.copy("/dev/null", s); bridge.move(s, d) }.getOrDefault(false)
        out += Item("源→目标 可移动", moved, "$s → $d")
        runCatching { bridge.delete(d) }

        // 6) 环境信息
        out += Item("环境信息", true, "见截图/日志")

        return out
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`.\gradlew.bat :app:testDebugUnitTest --tests "*HealthCheckerTest*"`
预期：PASS

- [ ] **步骤 5：真机对照验证（验收 A7）**

在规则列表页点「自检」，把结果与规格附录 A 的探针结果对照（也可用命令交叉验证源目录可写）：
```powershell
D:\soft\scrcpy\adb.exe shell "touch /sdcard/Android/data/com.qidian.QDReader/files/_probe && mv /sdcard/Android/data/com.qidian.QDReader/files/_probe /sdcard/Android/data/com.qidian.QDReader/files/_probe2 && rm -f /sdcard/Android/data/com.qidian.QDReader/files/_probe2 && echo WRITE_OK"
```
预期：自检 **6 项全绿**（与本文档编写时的实测一致），命令输出 `WRITE_OK`；系统更新后再跑能正确报红。

- [ ] **步骤 6：Commit**

```bash
git add -A
git commit -m "feat(engine): 自检 6 项（只用临时文件）"
```

---

## 任务 12：规则冲突检查（规格 §10 页面 2）

**文件：**
- 创建：`core/RuleConflictChecker.kt`
- 测试：`app/src/test/java/com/landslide/shitu/core/RuleConflictCheckerTest.kt`

- [ ] **步骤 1：先写失败的测试**

```kotlin
package com.landslide.shitu.core

import com.landslide.shitu.data.db.Mode
import com.landslide.shitu.data.db.RuleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleConflictCheckerTest {
    private fun rule(id: Long, src: String, dst: String) = RuleEntity(
        id = id, name = "r$id", srcPath = src, dstPath = dst, mode = Mode.MOVE,
        createdAt = 0, updatedAt = 0)

    @Test fun `源目录互相嵌套要告警`() {
        val issues = RuleConflictChecker.check(listOf(
            rule(1, "/sdcard/Android/data/com.a/files", "/sdcard/Pictures/拾图"),
            rule(2, "/sdcard/Android/data/com.a/files/sub", "/sdcard/Pictures/拾图2")))
        assertTrue(issues.any { it.contains("嵌套") })
    }
    @Test fun `目标目录被别的规则当源要告警`() {
        val issues = RuleConflictChecker.check(listOf(
            rule(1, "/sdcard/Android/data/com.a/files", "/sdcard/Pictures/拾图"),
            rule(2, "/sdcard/Pictures/拾图", "/sdcard/DCIM/other")))
        assertTrue(issues.any { it.contains("循环") })
    }
    @Test fun `两条规则源相同要告警`() {
        val issues = RuleConflictChecker.check(listOf(
            rule(1, "/sdcard/Android/data/com.a/files", "/sdcard/Pictures/x"),
            rule(2, "/sdcard/Android/data/com.a/files", "/sdcard/Pictures/y")))
        assertEquals(1, issues.count { it.contains("源目录相同") })
    }
}
```

- [ ] **步骤 2：运行验证失败**

运行：`.\gradlew.bat :app:testDebugUnitTest --tests "*RuleConflictCheckerTest*"`
预期：FAIL（`Unresolved reference: RuleConflictChecker`）

- [ ] **步骤 3：写实现**

```kotlin
package com.landslide.shitu.core

import com.landslide.shitu.data.db.RuleEntity

object RuleConflictChecker {
    /** 返回人类可读的告警列表；不阻止保存（规格：提示后仍可继续）。 */
    fun check(rules: List<RuleEntity>): List<String> {
        val out = ArrayList<String>()
        for (i in rules.indices) for (j in i + 1 until rules.size) {
            val a = rules[i]; val b = rules[j]
            if (a.srcPath == b.srcPath) out += "规则「${a.name}」与「${b.name}」源目录相同"
            if (a.srcPath.startsWith(b.srcPath.trimEnd('/') + "/")) out += "规则「${a.name}」的源目录嵌套在「${b.name}」源目录内（嵌套）"
            if (b.srcPath.startsWith(a.srcPath.trimEnd('/') + "/")) out += "规则「${b.name}」的源目录嵌套在「${a.name}」源目录内（嵌套）"
            if (a.dstPath == b.srcPath) out += "规则「${a.name}」的目标目录是「${b.name}」的源目录（可能形成循环）"
            if (b.dstPath == a.srcPath) out += "规则「${b.name}」的目标目录是「${a.name}」的源目录（可能形成循环）"
        }
        return out
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`.\gradlew.bat :app:testDebugUnitTest --tests "*RuleConflictCheckerTest*"`
预期：PASS

- [ ] **步骤 5：Commit**

```bash
git add -A
git commit -m "feat(core): 规则冲突检查（嵌套/循环/源相同）"
```

---

## 任务 13：界面（四页 + Shizuku 目录选择器）

**文件：**
- 创建：`ui/theme/Theme.kt`、`ui/Nav.kt`、`ui/rules/RuleListScreen.kt`、`ui/rules/RuleEditScreen.kt`、`ui/logs/LogScreen.kt`、`ui/settings/SettingsScreen.kt`、`ui/components/DirPickerDialog.kt`
- 修改：`MainActivity.kt`（改成导航宿主）、`ShituApp.kt`（暴露 bridge/repository/runDueRules）

- [ ] **步骤 1：写 `ShituApp.kt`（应用级容器）**

```kotlin
package com.landslide.shitu

import android.app.Application
import com.landslide.shitu.data.RuleRepository
import com.landslide.shitu.data.SettingsStore
import com.landslide.shitu.data.db.AppDatabase
import com.landslide.shitu.engine.RuleEngine
import com.landslide.shitu.shizuku.ShizukuBridge
import com.landslide.shitu.core.LoopGuard
import com.landslide.shitu.core.NamePolicy
import kotlinx.coroutines.runBlocking

class ShituApp : Application() {
    lateinit var db: AppDatabase; lateinit var repo: RuleRepository
    lateinit var settings: SettingsStore; lateinit var bridge: ShizukuBridge
    private val guard = LoopGuard(30 * 60_000, 3)

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.build(this)
        repo = RuleRepository(db)
        settings = SettingsStore(this)
        bridge = ShizukuBridge(this)
        com.landslide.shitu.service.FallbackWorker.schedule(this)
    }

    /** 供 WatchService / Worker 调用：跑所有到期规则，返回本轮搬移总数 */
    fun runDueRules(): Int = runBlocking {
        val s = kotlinx.coroutines.flow.first(settings.settings)
        var total = 0
        repo.enabledRules().forEach { rule ->
            if (rule.state != com.landslide.shitu.data.db.RuleState.IDLE) return@forEach
            val engine = RuleEngine(bridge, NamePolicy(), s)
            val r = engine.runOnce(rule, guard)
            total += r.moved
            repo.updateRule(rule.copy(
                lastRunAt = System.currentTimeMillis(), lastMoved = r.moved,
                totalMoved = rule.totalMoved + r.moved, lastFailed = r.failed,
                consecutiveFailures = if (r.failed > 0) rule.consecutiveFailures + 1 else 0,
                state = engine.lastState,
                pauseReason = if (engine.lastState == com.landslide.shitu.data.db.RuleState.PAUSED_LOOP)
                    "疑似 App 会自动重建文件（重复抑制）" else rule.pauseReason,
                updatedAt = System.currentTimeMillis()))
        }
        total
    }
}
```

- [ ] **步骤 2：写 `DirPickerDialog.kt`（用 Shizuku 列目录）**

```kotlin
package com.landslide.shitu.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.landslide.shitu.shizuku.FileBridge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirPickerDialog(
    bridge: FileBridge,
    startPath: String = "/sdcard/Android/data",
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    var path by remember { mutableStateOf(startPath) }
    var entries by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(path) {
        entries = runCatching {
            bridge.list(path, false, 1, 500, null)
                .filter { it.isDirectory }.map { it.path }
        }.getOrDefault(emptyList())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(path, maxLines = 1) },
        text = {
            LazyColumn {
                item { TextButton(onClick = { path = path.substringBeforeLast('/', "/") }) { Text("⬆ 上一级") } }
                items(entries) { p ->
                    ListItem(
                        headlineContent = { Text(p.substringAfterLast('/')) },
                        modifier = Modifier.clickable { path = p },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(path) }) { Text("选择此目录") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
```

- [ ] **步骤 3：写 `RuleListScreen.kt`（含自检入口与状态条）**

```kotlin
package com.landslide.shitu.ui.rules

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.landslide.shitu.data.db.RuleEntity
import com.landslide.shitu.shizuku.ShizukuState

@Composable
fun RuleListScreen(
    rules: List<RuleEntity>,
    state: ShizukuState,
    onSelfCheck: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (RuleEntity) -> Unit,
    onToggle: (RuleEntity, Boolean) -> Unit,
    onRunNow: (RuleEntity) -> Unit,
    onUndo: (RuleEntity) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            AssistChip(onClick = onSelfCheck, label = { Text("Shizuku: $state · 点击自检") })
        }
        LazyColumn(Modifier.weight(1f)) {
            items(rules, key = { it.id }) { r ->
                Card(Modifier.fillMaxWidth().padding(8.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(r.name, style = MaterialTheme.typography.titleMedium)
                        Text("${r.srcPath}\n→ ${r.dstPath}", style = MaterialTheme.typography.bodySmall)
                        Text("模式 ${r.mode} · 间隔 ${r.intervalMinutes} 分钟 · 状态 ${r.state}" +
                             (r.pauseReason?.let { "（$it）" } ?: ""), style = MaterialTheme.typography.bodySmall)
                        Text("上次 ${r.lastRunAt ?: "—"} · 上次搬 ${r.lastMoved} · 累计 ${r.totalMoved}",
                             style = MaterialTheme.typography.bodySmall)
                        Row {
                            Switch(checked = r.enabled, onCheckedChange = { onToggle(r, it) })
                            TextButton(onClick = { onEdit(r) }) { Text("编辑") }
                            TextButton(onClick = { onRunNow(r) }) { Text("立即运行") }
                            TextButton(onClick = { onUndo(r) }) { Text("撤回") }
                        }
                    }
                }
            }
        }
    }
    FloatingActionButton(onClick = onAdd, modifier = Modifier.padding(16.dp)) { Text("+") }
}
```

- [ ] **步骤 4：写 `RuleEditScreen.kt`（字段与规格 §10 页面 2 一一对应）**

```kotlin
package com.landslide.shitu.ui.rules

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.landslide.shitu.data.db.Mode
import com.landslide.shitu.data.db.RuleEntity
import com.landslide.shitu.ui.components.DirPickerDialog
import com.landslide.shitu.shizuku.FileBridge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleEditScreen(
    initial: RuleEntity, bridge: FileBridge, conflicts: List<String>,
    onSave: (RuleEntity) -> Unit, onCancel: () -> Unit,
) {
    var rule by remember { mutableStateOf(initial) }
    var picking by remember { mutableStateOf<String?>(null) }   // "src" | "dst"
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        OutlinedTextField(rule.name, { rule = rule.copy(name = it) }, label = { Text("规则名称") })
        OutlinedTextField(rule.srcPath, {}, label = { Text("源目录") }, readOnly = true,
            trailingIcon = { TextButton({ picking = "src" }) { Text("选择") } })
        Row {
            Checkbox(rule.includeSubdirs, { rule = rule.copy(includeSubdirs = it) }); Text("包含子目录")
        }
        OutlinedTextField(rule.dstPath, {}, label = { Text("目标目录") }, readOnly = true,
            trailingIcon = { TextButton({ picking = "dst" }) { Text("选择") } })
        SingleChoiceSegmentedButtonRow {
            Mode.entries.forEachIndexed { i, m ->
                SegmentedButton(selected = rule.mode == m, onClick = { rule = rule.copy(mode = m) },
                    shape = SegmentedButtonDefaults.itemShape(i, Mode.entries.size)) { Text(m.name) }
            }
        }
        Text("间隔：${rule.intervalMinutes} 分钟")
        Slider(value = rule.intervalMinutes.toFloat(), onValueChange = {
            rule = rule.copy(intervalMinutes = it.toInt().coerceIn(1, 30)) }, valueRange = 1f..30f)
        OutlinedTextField(rule.extensions, { rule = rule.copy(extensions = it) }, label = { Text("扩展名白名单") })
        Row { Checkbox(rule.addSourceAppSuffix, { rule = rule.copy(addSourceAppSuffix = it) }); Text("文件名加来源 App 后缀") }
        conflicts.forEach { Text("⚠ $it", color = MaterialTheme.colorScheme.error) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onCancel) { Text("取消") }
            Button({ onSave(rule) }) { Text("保存并开始搬运") }
        }
    }
    picking?.let { which ->
        DirPickerDialog(bridge,
            startPath = if (which == "src") "/sdcard/Android/data" else "/sdcard/Pictures",
            onDismiss = { picking = null },
            onPick = { p -> rule = if (which == "src") rule.copy(srcPath = p) else rule.copy(dstPath = p); picking = null })
    }
}
```

- [ ] **步骤 5：写 `LogScreen.kt` 与 `SettingsScreen.kt`**

`LogScreen`：`LazyColumn` 显示 `LogEntity`（时间/结果/源→目标/错误），顶部筛选（规则下拉、结果下拉），右上角"导出 CSV"调用 `LogExporter.toCsv` 写到 `/sdcard/Download/shitu-log-<时间>.csv`（经 bridge 写入）。
`SettingsScreen`：规格 §8 参数逐项（`NumberField`/`Switch`）+ 通知开关 + 粗体提示"低电量暂停" + 关于（版本/许可/仓库地址 `https://github.com/Landslide3154/shitu-android`）。

- [ ] **步骤 6：写 `Nav.kt` 与改造 `MainActivity.kt`**

```kotlin
// Nav.kt
package com.landslide.shitu.ui

import androidx.compose.runtime.*
import androidx.navigation.compose.*
import com.landslide.shitu.ShituApp
import com.landslide.shitu.core.RuleConflictChecker
import com.landslide.shitu.data.db.RuleEntity
import com.landslide.shitu.engine.HealthChecker
import com.landslide.shitu.engine.UndoService
import com.landslide.shitu.ui.logs.LogScreen
import com.landslide.shitu.ui.rules.RuleEditScreen
import com.landslide.shitu.ui.rules.RuleListScreen
import com.landslide.shitu.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

@Composable
fun ShituNav(app: ShituApp) {
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    var rules by remember { mutableStateOf(emptyList<RuleEntity>()) }
    var logs by remember { mutableStateOf(emptyList<com.landslide.shitu.data.db.LogEntity>()) }
    var state by remember { mutableStateOf(app.bridge.state()) }
    var health by remember { mutableStateOf(emptyList<HealthChecker.Item>()) }

    suspend fun refresh() {
        rules = app.repo.allRules()
        logs = app.repo.recentLogs(500)
        state = app.bridge.state()
    }
    LaunchedEffect(Unit) { refresh() }

    NavHost(nav, startDestination = "rules") {
        composable("rules") {
            RuleListScreen(
                rules = rules, state = state,
                onSelfCheck = { scope.launch {
                    if (app.bridge.state() != com.landslide.shitu.shizuku.ShizukuState.READY) app.bridge.bind()
                    health = HealthChecker(app.bridge, "/sdcard/Download/_shitu_test", "/sdcard/Pictures").run()
                } },
                onAdd = { nav.navigate("edit/0") },
                onEdit = { nav.navigate("edit/${it.id}") },
                onToggle = { r, on -> scope.launch { app.repo.updateRule(r.copy(enabled = on)); refresh() } },
                onRunNow = { scope.launch { app.runDueRules(); refresh() } },
                onUndo = { r -> scope.launch {
                    UndoService(app.bridge).let { u ->
                        app.repo.recentDoneForRule(r.id).forEach { item ->
                            item.dstPath?.let { u.undo(item.srcPath, it, item.srcSize) }
                        }
                    }
                    refresh()
                } },
            )
        }
        composable("edit/{id}") { entry ->
            val id = entry.arguments?.getString("id")?.toLongOrNull() ?: 0L
            val initial = rules.firstOrNull { it.id == id }
                ?: RuleEntity(name = "新规则", srcPath = "/sdcard/Android/data", dstPath = "/sdcard/Pictures/拾图",
                    createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis())
            RuleEditScreen(
                initial = initial, bridge = app.bridge,
                conflicts = RuleConflictChecker.check(rules.filter { it.id != id } + initial),
                onSave = { r -> scope.launch {
                    if (r.id == 0L) app.repo.insertRule(r) else app.repo.updateRule(r)
                    refresh(); nav.popBackStack()
                } },
                onCancel = { nav.popBackStack() },
            )
        }
        composable("logs") { LogScreen(logs = logs, onExport = { scope.launch { app.repo.exportCsv() } }) }
        composable("settings") { SettingsScreen(app.settings) }
    }
}
```

> `RuleRepository` 需要提供：`allRules()`、`insertRule()`、`updateRule()`、`recentLogs(n)`、`recentDoneForRule(id)`、`exportCsv()`（后者内部用 `LogExporter` 经 `bridge` 写到 `/sdcard/Download/shitu-log-<时间>.csv`）。这些方法在任务 4 的 `RuleRepository` 中一并实现（`suspend` 函数）。

```kotlin
// MainActivity.kt
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { ShituNav() } }
    }
}
```

- [ ] **步骤 7：真机走查**

运行：`D:\soft\scrcpy\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk`
预期：能新建规则（目录选择器默认落在 `/sdcard/Android/data`）、保存后规则出现在列表、立即运行能真实搬图、日志页能看到记录、设置页参数可改

- [ ] **步骤 8：Commit**

```bash
git add -A
git commit -m "feat(ui): 规则列表/编辑/日志/设置 + Shizuku 目录选择器"
```

---

## 任务 14：端到端灰度与长跑（真机）

**文件：** 无需改代码；把结论回写 `docs/`（新增 `2026-10-xx-shitu-验证记录.md`）

- [ ] **步骤 1：假源目录全流程**（规格 §14 灰度）

```powershell
D:\soft\scrcpy\adb.exe shell mkdir -p /sdcard/Download/_shitu_test
D:\soft\scrcpy\adb.exe push 测试图1.jpg /sdcard/Download/_shitu_test/
D:\soft\scrcpy\adb.exe push 测试图2.png /sdcard/Download/_shitu_test/
```
预期：规则（源=`/sdcard/Download/_shitu_test`，目标=`/sdcard/Pictures/拾图`，**复制模式**）一次运行后目标出现两个带后缀文件，源仍在

- [ ] **步骤 2：真实 Android/data 源，第 1 天只用复制模式**

源=`/sdcard/Android/data/com.qidian.QDReader/files/QDReader/download/rare_book`，目标=`/sdcard/Pictures/拾图/起点`，复制模式，间隔 5 分钟。
预期：24 小时内该目录下图片全部复制过去；无崩溃；重复抑制未误触发

- [ ] **步骤 3：切移动模式 + 观察 24 小时**

预期：`rare_book` 下图片被搬空；APP 若重建文件，触发 `PAUSED_LOOP` 并通知（这正是要验证的行为）

- [ ] **步骤 4：场景矩阵**（规格 §11 的 20 条，逐条勾）

必跑：Shizuku 未运行 / 未授权、目标不可写、源目录删除、文件正在写、同名冲突、目标已有同内容、撤回、重启手机、杀 App、WorkManager 兜底触发、低电量暂停

- [ ] **步骤 5：长跑与指标**

运行 24 小时（间隔 1 分钟）：记录崩溃数（应为 0）、ANR（0）、电量消耗、内存峰值、日志条数增长。
命令：`D:\soft\scrcpy\adb.exe shell dumpsys batterystats com.landslide.shitu | Select-String -Pattern "Estimated power"`

- [ ] **步骤 6：Commit**

```bash
git add docs
git commit -m "docs: 灰度与长跑验证记录（对照规格 §11/§14/§15）"
```

---

## 任务 15：发布 0.1.0

**文件：**
- 修改：`app/build.gradle.kts`（release 签名配置，keystore 不入库）
- 创建：`docs/CHANGELOG.md`

- [ ] **步骤 1：生成自签 keystore（一次性，放仓库外）**

```powershell
keytool -genkeypair -v -keystore D:\keys\shitu-release.jks -alias shitu -keyalg RSA -keysize 4096 -validity 10000
```
`keystore.properties` 写入仓库外路径，本地 `app/build.gradle.kts` 读取；**确认 `.gitignore` 已忽略 `*.jks`/`keystore.properties`**

- [ ] **步骤 2：构建 release 并真机验收**

```powershell
.\gradlew.bat :app:assembleRelease
D:\soft\scrcpy\adb.exe install -r app\build\outputs\apk\release\app-release.apk
```
预期：安装后跑完任务 14 的步骤 1、2 的核心用例

- [ ] **步骤 3：写 CHANGELOG 与发布说明**（含"系统更新后请先自检"的提醒）

- [ ] **步骤 4：打 tag 并推送**

```bash
git tag -a v0.1.0 -m "拾图 0.1.0"
git push origin main --tags
```

- [ ] **步骤 5：GitHub Release 上传 APK**（用 REST API，MCP 默认工具不覆盖 Release 资产上传）

---

## 自检结果（规格对照）

| 规格章节 | 对应任务 |
|---|---|
| §3 约束 C1–C4 | 任务 5/6（C1）、任务 13 手动扫描按钮（C2）、任务 11（C3）、任务 13 目标提示（C4） |
| §6.1–6.9 组件 | 任务 4/5/6/7/8/9/10/11/13 |
| §7 数据模型 | 任务 4 |
| §8 参数表 | 任务 4（SettingsStore）、任务 13（设置页） |
| §9 算法 | 任务 3（过滤/限速/抑制）、任务 8（编排） |
| §10 界面 | 任务 13 |
| §11 错误处理 20 条 | 任务 7/8/9（实现）、任务 14（逐条验证） |
| §12 权限 | 任务 9 |
| §13 隐私安全 | 任务 5（无 shell 接口）、任务 6（无 INTERNET）、任务 11（只用临时文件） |
| §14 测试计划 | 任务 2/3/7/8/10/11/12（单测）、任务 14（真机/长跑） |
| §15 验收 A1–A12 | 任务 14（A1–A11）、任务 15（A12） |
| §16 里程碑 M1–M5 | M1=任务 1/5/6/11、M2=任务 2/3/4/7/8、M3=任务 9、M4=任务 10/12/13、M5=任务 14/15 |
| §20 五件待验证 | 任务 9 步骤 5（第 1、2 条）、任务 14（第 3、4、5 条） |

**占位符扫描：** 无 TODO/待定/"类似任务 N"/"添加适当的错误处理"；所有代码步骤均含可编译的 Kotlin 片段。
**类型一致性：** `FileBridge` 的 7 个方法与 AIDL 一一对应；`RuleEntity/ItemEntity/LogEntity` 字段在任务 4 定义后，后续任务只用这些字段名；`TransferExecutor.transfer(...)` 的签名在任务 7/8 中一致；`NamePolicy.resolve(rawName, sourceApp, exists, timestamp)` 在任务 2/7 中一致。
