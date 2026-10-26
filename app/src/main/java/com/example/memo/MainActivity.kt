@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.memo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.memo.data.ExportUtil
import com.example.memo.data.MemoDatabase
import com.example.memo.data.Note
import com.example.memo.ui.MemoTheme
import com.example.memo.ui.NoteEditorDialog
import com.example.memo.update.UpdateUtil
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 列表筛选维度，对应「未完成 / 已完成」这组需求。 */
private enum class Filter(val label: String) {
    ALL("全部"),
    TODO("未完成"),
    DONE("已完成")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 已授权时的首次冷启动：趁数据库还没打开，把内部存储的旧库搬到公共目录
        MemoDatabase.migrateToExternalIfNeeded(this)
        setContent {
            MemoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MemoScreen()
                }
            }
        }
    }
}

@Composable
private fun MemoScreen(vm: MemoViewModel = viewModel()) {
    val notes by vm.notes.collectAsState()
    var filter by remember { mutableStateOf(Filter.ALL) }
    var query by remember { mutableStateOf("") }
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Note?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var pendingFormat by remember { mutableStateOf<ExportUtil.Format?>(null) }
    var updateState by remember { mutableStateOf<UpdateUtil.State?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 「发现新版本」后真正发起下载
    fun startDownload(info: UpdateUtil.ReleaseInfo) {
        updateState = UpdateUtil.State.Downloading(0)
        scope.launch {
            updateState = try {
                val apk = UpdateUtil.download(context, info) { pct ->
                    updateState = UpdateUtil.State.Downloading(pct)
                }
                UpdateUtil.State.Ready(apk, info)
            } catch (e: Exception) {
                UpdateUtil.State.Error("下载失败：${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    // SAF 建文档：不申请存储权限，由系统文件选择器决定落盘位置。
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val format = pendingFormat
        pendingFormat = null
        if (uri != null && format != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(ExportUtil.render(notes, format).toByteArray(Charsets.UTF_8))
                }
            }
        }
    }

    val visible = remember(notes, filter, query) {
        notes.filter { n ->
            val hitFilter = when (filter) {
                Filter.ALL -> true
                Filter.TODO -> !n.done
                Filter.DONE -> n.done
            }
            val hitQuery = query.isBlank() ||
                n.title.contains(query.trim(), ignoreCase = true) ||
                n.body.contains(query.trim(), ignoreCase = true)
            hitFilter && hitQuery
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("备忘录") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "导出")
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false }
                    ) {
                        ExportUtil.Format.values().forEach { format ->
                            DropdownMenuItem(
                                text = { Text("导出为 " + format.label) },
                                onClick = {
                                    menuOpen = false
                                    pendingFormat = format
                                    exportLauncher.launch(ExportUtil.suggestFileName(format))
                                }
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("检查更新") },
                            onClick = {
                                menuOpen = false
                                updateState = UpdateUtil.State.Checking
                                scope.launch {
                                    updateState = UpdateUtil.check(BuildConfig.GITHUB_REPO)
                                }
                            }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("新增") },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                onClick = {
                    editing = null
                    editorOpen = true
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (!MemoDatabase.hasAllFilesAccess()) {
                StorageBanner()
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索标题或备注") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Filter.values().forEach { item ->
                    FilterChip(
                        selected = filter == item,
                        onClick = { filter = item },
                        label = { Text(item.label) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()

            if (visible.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (notes.isEmpty()) "还没有记录，点右下角新增" else "当前筛选下没有记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(visible, key = { it.id }) { note ->
                        NoteRow(
                            note = note,
                            onToggle = { vm.toggleDone(note) },
                            onClick = {
                                editing = note
                                editorOpen = true
                            }
                        )
                    }
                }
            }
        }
    }

    if (editorOpen) {
        NoteEditorDialog(
            initial = editing,
            onDismiss = { editorOpen = false },
            onSave = { title, body ->
                vm.save(editing?.id, title, body)
                editorOpen = false
            },
            onDelete = {
                editing?.let { vm.delete(it) }
                editorOpen = false
            }
        )
    }

    updateState?.let { st ->
        AlertDialog(
            onDismissRequest = { if (st !is UpdateUtil.State.Downloading) updateState = null },
            title = { Text("检查更新") },
            text = {
                when (st) {
                    is UpdateUtil.State.Checking -> Text("正在连接 GitHub…")
                    is UpdateUtil.State.NotConfigured ->
                        Text("升级源未配置。请在 gradle.properties 填写 GITHUB_REPO 后重新构建安装。")
                    is UpdateUtil.State.UpToDate ->
                        Text("已是最新版本 ${BuildConfig.VERSION_NAME}")
                    is UpdateUtil.State.Available -> Column {
                        Text("发现新版本 ${st.info.version}（当前 ${BuildConfig.VERSION_NAME}）")
                        if (st.info.releaseNotes.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(st.info.releaseNotes, maxLines = 6, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    is UpdateUtil.State.Downloading -> Column {
                        LinearProgressIndicator(
                            progress = { st.percent / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("下载中 ${st.percent}%")
                    }
                    is UpdateUtil.State.Ready -> Text("安装包已下载，点「安装」拉起系统安装器。")
                    is UpdateUtil.State.Error -> Text(st.message)
                }
            },
            confirmButton = {
                when (st) {
                    is UpdateUtil.State.Available ->
                        TextButton(onClick = { startDownload(st.info) }) { Text("下载") }
                    is UpdateUtil.State.Ready -> TextButton(onClick = {
                        if (UpdateUtil.canInstall(context)) {
                            UpdateUtil.install(context, st.apk)
                        } else {
                            UpdateUtil.unknownSourceSettings(context)
                        }
                    }) { Text("安装") }
                    else -> {}
                }
            },
            dismissButton = {
                if (st is UpdateUtil.State.Downloading) {
                    // 下载中不给关闭入口，避免后台协程写已消失的界面状态
                } else {
                    TextButton(onClick = { updateState = null }) { Text("关闭") }
                }
            }
        )
    }
}

@Composable
private fun StorageBanner() {
    val context = LocalContext.current
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "记录暂存于应用内部，卸载会丢失。授权后保存到 /sdcard/MemoApp，下次打开自动迁移。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = {
                val exact = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val fallback = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(exact) }
                    .onFailure { context.startActivity(fallback) }
            }) { Text("去授权") }
        }
    }
}

@Composable
private fun NoteRow(
    note: Note,
    onToggle: () -> Unit,
    onClick: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    // 已完成条目铺青绿底色，未完成铺淡靛蓝底色，弱化分割线、强化状态。
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (note.done) {
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f)
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Checkbox(
                checked = note.done,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.tertiary,
                    checkmarkColor = MaterialTheme.colorScheme.onTertiary
                )
            )
            Spacer(Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (note.done) TextDecoration.LineThrough else null,
                    color = if (note.done) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                if (note.body.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = note.body,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                val stamp = if (note.done) {
                    "完成于 " + formatter.format(Date(note.doneAt ?: note.updatedAt))
                } else {
                    "更新于 " + formatter.format(Date(note.updatedAt))
                }
                Text(
                    text = stamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (note.done) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                )
            }
        }
    }
}
