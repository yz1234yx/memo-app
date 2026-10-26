package com.example.memo.data

import com.example.memo.data.ExportUtil.Format
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportUtilTest {

    private val t0 = 1_700_000_000_000L
    private val t1 = t0 + 60_000L
    private val t2 = t0 + 120_000L

    private fun todo() = Note(
        id = 1L, title = "买牛奶", body = "顺路去超市",
        done = false, createdAt = t0, updatedAt = t1, doneAt = null
    )

    private fun done() = Note(
        id = 2L, title = "交周报", body = "",
        done = true, createdAt = t0, updatedAt = t2, doneAt = t2
    )

    @Test
    fun json_可被解析且字段与源数据一致() {
        val notes = listOf(todo(), done())
        val json = ExportUtil.render(notes, Format.JSON)
        val root = JsonParser.parseString(json).asJsonObject

        assertEquals("MemoApp", root.get("app").asString)
        assertEquals(1, root.get("schema").asInt)
        assertEquals(2, root.get("count").asInt)
        assertTrue(root.has("exportedAt"))

        val arr = root.getAsJsonArray("notes")
        assertEquals(2, arr.size())

        val first = arr[0].asJsonObject
        assertEquals(1L, first.get("id").asLong)
        assertEquals("买牛奶", first.get("title").asString)
        assertEquals("顺路去超市", first.get("body").asString)
        assertFalse(first.get("done").asBoolean)
        assertTrue("未完成项的 doneAt 必须是 null", first.get("doneAt").isJsonNull)

        val second = arr[1].asJsonObject
        assertTrue(second.get("done").asBoolean)
        assertFalse("已完成项的 doneAt 不能为 null", second.get("doneAt").isJsonNull)
    }

    @Test
    fun json_转义特殊字符后可无损还原() {
        val tricky = "引号\"反斜杠\\换行\n制表\t控制符\u0001"
        val note = Note(id = 9L, title = tricky, body = tricky, createdAt = t0, updatedAt = t0)
        val json = ExportUtil.render(listOf(note), Format.JSON)

        val back = JsonParser.parseString(json).asJsonObject
            .getAsJsonArray("notes")[0].asJsonObject

        assertEquals(tricky, back.get("title").asString)
        assertEquals(tricky, back.get("body").asString)
    }

    @Test
    fun json_空列表输出合法结构() {
        val json = ExportUtil.render(emptyList(), Format.JSON)
        val root = JsonParser.parseString(json).asJsonObject
        assertEquals(0, root.get("count").asInt)
        assertEquals(0, root.getAsJsonArray("notes").size())
    }

    @Test
    fun csv_带BOM与表头且引号正确转义() {
        val note = Note(id = 7L, title = "a,\"b\"", body = "第一行\n第二行", createdAt = t0, updatedAt = t0)
        val csv = ExportUtil.render(listOf(note), Format.CSV)

        assertTrue("缺少 UTF-8 BOM，Excel 会乱码", csv.startsWith("\uFEFF"))
        assertTrue(csv.contains("id,标题,备注,状态,创建时间,完成时间,更新时间"))
        assertTrue("内部引号应翻倍", csv.contains("\"a,\"\"b\"\"\""))
        assertTrue("备注应被引号包裹以容纳换行", csv.contains("\"第一行\n第二行\""))
        assertTrue(csv.contains("未完成"))
        assertTrue(csv.endsWith("\r\n"))
    }

    @Test
    fun csv_完成态输出中文状态() {
        val csv = ExportUtil.render(listOf(done()), Format.CSV)
        assertTrue(csv.contains("已完成"))
        assertFalse(csv.contains("未完成"))
    }

    @Test
    fun markdown_按状态分节并区分勾选符() {
        val md = ExportUtil.render(listOf(todo(), done()), Format.MARKDOWN)

        assertTrue(md.startsWith("# 备忘录导出"))
        assertTrue(md.contains("（未完成 1 / 已完成 1）"))
        assertTrue(md.contains("## 未完成"))
        assertTrue(md.contains("## 已完成"))
        assertTrue(md.contains("- [ ] 买牛奶"))
        assertTrue(md.contains("- [x] 交周报"))
        assertTrue("未完成节应排在已完成节之前", md.indexOf("## 未完成") < md.indexOf("## 已完成"))
    }

    @Test
    fun markdown_空节写无而不是留白() {
        val md = ExportUtil.render(listOf(todo()), Format.MARKDOWN)
        val tail = md.substringAfter("## 已完成")
        assertTrue(tail.contains("_无_"))
    }

    @Test
    fun markdown_备注多行时逐行缩进() {
        val note = Note(id = 3L, title = "多行", body = "第一行\n第二行", createdAt = t0, updatedAt = t0)
        val md = ExportUtil.render(listOf(note), Format.MARKDOWN)
        assertTrue(md.contains("  第一行\n  第二行"))
    }

    @Test
    fun 建议文件名带扩展名与时间戳() {
        val name = ExportUtil.suggestFileName(Format.MARKDOWN, t0)
        assertTrue(name, name.startsWith("memo-"))
        assertTrue(name, name.endsWith(".md"))
        assertTrue(name, Regex("^memo-\\d{8}-\\d{4}\\.md$").matches(name))
    }
}
