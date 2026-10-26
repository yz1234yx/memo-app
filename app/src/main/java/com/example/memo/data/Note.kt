package com.example.memo.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 备忘录实体。
 *
 * 五项需求的字段映射：
 * - 未完成 / 已完成 -> [done]，完成时刻单独记在 [doneAt]，便于按完成时间排序与统计
 * - 备注            -> [body]
 * - 修改            -> [updatedAt]，每次写入时刷新
 */
@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val body: String = "",
    val done: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val doneAt: Long? = null
) {
    /**
     * 切换完成态，同时维护 [doneAt] 与 [updatedAt]。
     *
     * 抽成纯函数而非写在 ViewModel 里，是为了让「勾选写入完成时刻、撤销必须清空」
     * 这条容易写错的规则可以被单元测试直接断言。
     */
    fun toggled(now: Long = System.currentTimeMillis()): Note = copy(
        done = !done,
        doneAt = if (done) null else now,
        updatedAt = now
    )
}
