package com.example.memo.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 导出实现。三种格式共用同一份 [Note] 列表，不做数据库查询，调用方传入即可。
 *
 * 刻意不用 org.json：它是 Android 平台 API，在 JVM 单元测试里只有打桩实现，
 * 这样导出逻辑就无法被真实执行到。这里手写最小 JSON 序列化，代价是几十行代码，
 * 换来的是导出契约可以被 unit test 直接断言。
 */
object ExportUtil {

    /** 导出格式。ext 用于 SAF 建议文件名，mime 供分享 / 预览场景使用。 */
    enum class Format(val label: String, val ext: String, val mime: String) {
        JSON("JSON（可再导入）", "json", "application/json"),
        CSV("CSV（表格软件打开）", "csv", "text/csv"),
        MARKDOWN("Markdown（按状态分节）", "md", "text/markdown")
    }

    fun render(notes: List<Note>, format: Format): String = when (format) {
        Format.JSON -> toJson(notes)
        Format.CSV -> toCsv(notes)
        Format.MARKDOWN -> toMarkdown(notes)
    }

    /** 建议文件名，形如 memo-20261026-1642.json */
    fun suggestFileName(format: Format, now: Long = System.currentTimeMillis()): String =
        "memo-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date(now)) + "." + format.ext

    private fun iso(epochMillis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date(epochMillis))

    private fun human(epochMillis: Long?): String =
        if (epochMillis == null) "" else
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochMillis))

    private fun toJson(notes: List<Note>): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"app\": ").append(jsonString("MemoApp")).append(",\n")
        sb.append("  \"schema\": 1,\n")
        sb.append("  \"exportedAt\": ").append(jsonString(iso(System.currentTimeMillis()))).append(",\n")
        sb.append("  \"count\": ").append(notes.size).append(",\n")
        sb.append("  \"notes\": [")
        if (notes.isEmpty()) {
            sb.append("]\n}\n")
            return sb.toString()
        }
        sb.append('\n')
        notes.forEachIndexed { index, n ->
            sb.append("    {\n")
            sb.append("      \"id\": ").append(n.id).append(",\n")
            sb.append("      \"title\": ").append(jsonString(n.title)).append(",\n")
            sb.append("      \"body\": ").append(jsonString(n.body)).append(",\n")
            sb.append("      \"done\": ").append(n.done).append(",\n")
            sb.append("      \"createdAt\": ").append(jsonString(iso(n.createdAt))).append(",\n")
            sb.append("      \"updatedAt\": ").append(jsonString(iso(n.updatedAt))).append(",\n")
            sb.append("      \"doneAt\": ")
                .append(n.doneAt?.let { jsonString(iso(it)) } ?: "null")
                .append('\n')
            sb.append("    }")
            if (index != notes.lastIndex) sb.append(',')
            sb.append('\n')
        }
        sb.append("  ]\n}\n")
        return sb.toString()
    }

    private fun jsonString(raw: String): String {
        val sb = StringBuilder(raw.length + 2)
        sb.append('"')
        for (ch in raw) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (ch < ' ') {
                    sb.append("\\u").append(String.format(Locale.US, "%04x", ch.code))
                } else {
                    sb.append(ch)
                }
            }
        }
        sb.append('"')
        return sb.toString()
    }

    private fun toCsv(notes: List<Note>): String {
        // Excel 打开带 BOM 才不会把中文识别成乱码
        val sb = StringBuilder("\uFEFF")
        sb.append("id,标题,备注,状态,创建时间,完成时间,更新时间\r\n")
        notes.forEach { n ->
            sb.append(n.id).append(',')
                .append(csv(n.title)).append(',')
                .append(csv(n.body)).append(',')
                .append(if (n.done) "已完成" else "未完成").append(',')
                .append(human(n.createdAt)).append(',')
                .append(human(n.doneAt)).append(',')
                .append(human(n.updatedAt)).append("\r\n")
        }
        return sb.toString()
    }

    private fun csv(raw: String): String = "\"" + raw.replace("\"", "\"\"") + "\""

    private fun toMarkdown(notes: List<Note>): String {
        val todo = notes.filter { !it.done }
        val done = notes.filter { it.done }
        val sb = StringBuilder()
        sb.append("# 备忘录导出\n\n")
        sb.append("> 导出时间：").append(human(System.currentTimeMillis()))
            .append("　共 ").append(notes.size).append(" 条")
            .append("（未完成 ").append(todo.size).append(" / 已完成 ").append(done.size).append("）\n\n")

        sb.append("## 未完成\n\n")
        appendSection(sb, todo)
        sb.append("## 已完成\n\n")
        appendSection(sb, done)
        return sb.toString()
    }

    private fun appendSection(sb: StringBuilder, list: List<Note>) {
        if (list.isEmpty()) {
            sb.append("_无_\n\n")
            return
        }
        list.forEach { n ->
            sb.append("- [").append(if (n.done) "x" else " ").append("] ").append(n.title).append('\n')
            if (n.body.isNotBlank()) {
                n.body.lines().forEach { line -> sb.append("  ").append(line).append('\n') }
            }
            val stamp = if (n.done) {
                "创建 " + human(n.createdAt) + "　完成 " + human(n.doneAt)
            } else {
                "创建 " + human(n.createdAt) + "　更新 " + human(n.updatedAt)
            }
            sb.append("  - ").append(stamp).append("\n\n")
        }
    }
}
