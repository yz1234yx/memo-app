package com.example.memo.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.memo.data.Note

/**
 * 新建 / 修改对话框。[initial] 为 null 表示新建。
 * 标题为空的记录不允许保存，避免列表里出现无法辨识的空白条目。
 */
@Composable
fun NoteEditorDialog(
    initial: Note?,
    onDismiss: () -> Unit,
    onSave: (title: String, body: String) -> Unit,
    onDelete: () -> Unit
) {
    var title by remember(initial) { mutableStateOf(initial?.title ?: "") }
    var body by remember(initial) { mutableStateOf(initial?.body ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新建备忘录" else "修改备忘录") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("备注") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title.trim(), body.trim()) },
                enabled = title.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (initial != null) {
                    TextButton(onClick = onDelete) { Text("删除") }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}
