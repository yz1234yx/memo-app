package com.example.memo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.memo.data.MemoDatabase
import com.example.memo.data.Note
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MemoViewModel(app: Application) : AndroidViewModel(app) {

    /** 当前使用的数据库；切换存放文件夹后会换成新的实例。 */
    private val db = MutableStateFlow(MemoDatabase.get(app))

    val notes: StateFlow<List<Note>> = db
        .flatMapLatest { database ->
            database.noteDao().observeAll().catch { emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 存储位置变了：重开数据库并让列表重新订阅。 */
    fun reloadStorage() {
        db.value = MemoDatabase.reopen(getApplication())
    }

    /** 新建（id 为 null）或修改。 */
    fun save(id: Long?, title: String, body: String) {
        val now = System.currentTimeMillis()
        val dao = db.value.noteDao()
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
        val dao = db.value.noteDao()
        viewModelScope.launch {
            dao.update(note.toggled(now))
        }
    }

    fun delete(note: Note) {
        val dao = db.value.noteDao()
        viewModelScope.launch { dao.delete(note) }
    }
}
