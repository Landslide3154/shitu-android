package com.landslide.shitu.data

import com.landslide.shitu.data.db.LogEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 日志导出（规格 §10 页面 3）：CSV，导出前由界面提示"包含完整文件路径"。 */
object LogExporter {

    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun toCsv(logs: List<LogEntity>, out: File) {
        out.parentFile?.mkdirs()
        out.bufferedWriter(Charsets.UTF_8).use { w ->
            w.write("时间,结果,规则ID,源路径,目标路径,耗时ms,说明\n")
            logs.forEach { l ->
                val cells = listOf(
                    fmt.format(Date(l.ts)),
                    l.result.name,
                    l.ruleId?.toString() ?: "",
                    l.srcPath ?: "",
                    l.dstPath ?: "",
                    l.durationMs.toString(),
                    l.message ?: "",
                )
                w.write(cells.joinToString(",") { "\"" + it.replace("\"", "\"\"") + "\"" })
                w.write("\n")
            }
        }
    }

    fun fileName(now: Long = System.currentTimeMillis()): String {
        val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(now))
        return "shitu-log-$ts.csv"
    }
}
