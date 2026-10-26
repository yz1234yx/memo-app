package com.example.memo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.memo.data.MemoDatabase
import com.example.memo.data.Note
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MemoViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = MemoDatabase.get(app).noteDao()

    val notes: StateFlow<List<Note>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 新建（id 为 null）或修改。 */
    fun save(id: Long?, title: String, body: String) {
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            if (id == null) {
                dao.insert(Note(title = title, body = body, createdAt = now, updatedAt = now))
            } else {
                val existing = dao.byId(id) ?: return@launch
                dao.update(existing.copy(title = title, body = body, updatedAt = now))
            }
        }
    }

    /** 勾选 / 取消勾选。doneAt 的写入与清空规则收在 Note.toggled() 里，便于单测。 */
    fun toggleDone(note: Note) {
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            dao.update(note.toggled(now))
        }
    }

    fun delete(note: Note) {
        viewModelScope.launch { dao.delete(note) }
    }
}
