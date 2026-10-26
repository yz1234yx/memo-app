@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.memo

import android.content.Intent
import android.graphics.Color as AwtColor
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
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
        // 状态栏与蓝色顶栏同色、白图标
        @Suppress("DEPRECATION")
        window.statusBarColor = AwtColor.parseColor("#2563EB")
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
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
    var settingsOpen by remember { mutableStateOf(false) }
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

    fun checkUpdate() {
        settingsOpen = false
        updateState = UpdateUtil.State.Checking
        scope.launch {
            val result = UpdateUtil.check(BuildConfig.GITHUB_REPO)
            updateState = if (
                result is UpdateUtil.State.Available &&
                !UpdateUtil.isNewer(result.info.version, BuildConfig.VERSION_NAME)
            ) {
                UpdateUtil.State.UpToDate
            } else {
                result
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
            // ── 蓝色头部：标题 + 设置入口 + 筛选 + 统计 ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "备忘录",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.16f),
                        onClick = { settingsOpen = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = null,
                                tint = Color.White
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("设置", color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Filter.values().forEach { item ->
                        HeaderPill(
                            label = item.label,
                            selected = filter == item,
                            onClick = { filter = item }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "共 ${notes.size} 条记录 · 记得定期导出备份 · 版本 ${BuildConfig.VERSION_NAME}",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 12.sp
                )
            }

            if (!MemoDatabase.hasAllFilesAccess()) {
                StorageBanner()
            }

            // ── 搜索框：白色圆角卡片 ──
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索标题或备注", color = MaterialTheme.colorScheme.outline) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )

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

    if (settingsOpen) {
        SettingsSheet(
            version = BuildConfig.VERSION_NAME,
            updateState = updateState,
            onExport = { format ->
                settingsOpen = false
                pendingFormat = format
                exportLauncher.launch(ExportUtil.suggestFileName(format))
            },
            onCheckUpdate = { checkUpdate() },
            onDismiss = { settingsOpen = false }
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
                        Text("已经是最新版本（${BuildConfig.VERSION_NAME}）")
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

/** 顶栏里的筛选药丸：选中白底蓝字，未选中半透明白底白字。 */
@Composable
private fun HeaderPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) Color.White else Color.White.copy(alpha = 0.18f),
        onClick = onClick
    ) {
        Text(
            text = label,
            color = if (selected) MaterialTheme.colorScheme.primary else Color.White,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

/** 设置面板：浅灰底 + 白色分区卡片 + 大圆角按钮，对照参考版式。 */
@Composable
private fun SettingsSheet(
    version: String,
    updateState: UpdateUtil.State?,
    onExport: (ExportUtil.Format) -> Unit,
    onCheckUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "设置",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            SectionCard {
                Text(
                    text = "导出",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "CSV" to ExportUtil.Format.CSV,
                        "JSON" to ExportUtil.Format.JSON,
                        "Markdown" to ExportUtil.Format.MARKDOWN
                    ).forEach { (label, format) ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primary,
                            onClick = { onExport(format) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Caption(
                    "会先让你选保存位置，再写出文件。可以直接用微信发出去，也能在电脑上打开。" +
                        "建议隔一段时间导出一次，留个备份。"
                )
            }
            SectionCard {
                Text(
                    text = "关于",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "备忘录  版本 $version",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 15.sp
                )
                Spacer(Modifier.height(10.dp))
                BigButton("检查更新", filled = true) { onCheckUpdate() }
                Spacer(Modifier.height(8.dp))
                Caption(
                    when (updateState) {
                        is UpdateUtil.State.UpToDate -> "已经是最新版本（$version）"
                        is UpdateUtil.State.Available -> "发现新版本 ${updateState.info.version}，已弹出确认框"
                        is UpdateUtil.State.Error -> updateState.message
                        else -> "将连接 GitHub 检查是否有新版本"
                    }
                )
            }
            BigButton("关闭", filled = false) { onDismiss() }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) { content() }
    }
}

/** 大圆角整宽按钮：filled=蓝色主按钮，否则浅灰次按钮。 */
@Composable
private fun BigButton(text: String, filled: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 13.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = if (filled) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun StorageBanner() {
    val context = LocalContext.current
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "记录暂存于应用内部，卸载会丢失。授权后保存到 /sdcard/MemoApp，下次打开自动迁移。",
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "去授权",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clickable {
                        val exact = Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        val fallback = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(exact) }
                            .onFailure { context.startActivity(fallback) }
                    }
            )
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

    // 白色圆角卡片；已完成整卡淡蓝底、勾选框蓝色，与参考版式一致的克制风格。
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (note.done) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Checkbox(
                checked = note.done,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    checkmarkColor = MaterialTheme.colorScheme.onPrimary
                )
            )
            Spacer(Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = note.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 23.sp,
                    textDecoration = if (note.done) TextDecoration.LineThrough else null,
                    color = if (note.done) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                if (note.body.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = note.body,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(5.dp))
                val stamp = if (note.done) {
                    "完成于 " + formatter.format(Date(note.doneAt ?: note.updatedAt))
                } else {
                    "更新于 " + formatter.format(Date(note.updatedAt))
                }
                Text(
                    text = stamp,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
