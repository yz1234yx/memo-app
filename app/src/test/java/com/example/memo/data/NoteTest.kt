package com.example.memo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteTest {

    private fun base() = Note(
        id = 1L, title = "写周报", body = "含备注",
        done = false, createdAt = 100L, updatedAt = 100L, doneAt = null
    )

    @Test
    fun 勾选完成时写入完成时刻() {
        val after = base().toggled(now = 500L)

        assertTrue(after.done)
        assertEquals(500L, after.doneAt)
        assertEquals("updatedAt 必须同步刷新", 500L, after.updatedAt)
        assertEquals("createdAt 不应被改写", 100L, after.createdAt)
        assertEquals("标题不应被改写", "写周报", after.title)
        assertEquals("备注不应被改写", "含备注", after.body)
    }

    @Test
    fun 撤销完成时必须清空完成时刻() {
        val done = base().toggled(now = 500L)
        val undone = done.toggled(now = 900L)

        assertFalse(undone.done)
        assertNull("撤销后 doneAt 必须为 null，否则完成统计会失真", undone.doneAt)
        assertEquals(900L, undone.updatedAt)
        assertEquals("createdAt 不应被改写", 100L, undone.createdAt)
    }

    @Test
    fun 连续切换两次应回到初始状态() {
        val roundTrip = base().toggled(now = 500L).toggled(now = 900L)

        assertFalse(roundTrip.done)
        assertNull(roundTrip.doneAt)
        assertEquals(900L, roundTrip.updatedAt)
    }

    @Test
    fun 切换不修改id与正文() {
        val after = base().toggled(now = 1L)
        assertEquals(1L, after.id)
        assertEquals("含备注", after.body)
    }
}
