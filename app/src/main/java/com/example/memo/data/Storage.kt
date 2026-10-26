package com.example.memo.data

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import java.io.File

/**
 * 记录存放位置的解析与搬迁。
 *
 * 设计要点（对应"卸载重装后记录还在"这条需求）：
 * 1. 数据库是一个普通文件 memo.db，放在公共存储里用户选定的文件夹；卸载应用不会删它。
 * 2. 选定的路径写在 /sdcard/MemoApp/storage.conf（指针文件）。SharedPreferences 会随卸载清空，
 *    公共目录里的这个文本文件不会，所以重装后能凭它找回原文件夹，不需要用户重新选。
 * 3. 没有「所有文件访问」权限时退回应用内部数据库（可用但卸载会丢）。
 */
object Storage {

    const val ROOT_NAME = "MemoApp"
    const val DB_NAME = "memo.db"
    private const val POINTER_NAME = "storage.conf"

    /** 未授权时数据库在应用私有目录里的提示文案 */
    const val INTERNAL_HINT = "应用内部（卸载会丢失）"

    fun hasAllFilesAccess(): Boolean =
        Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()

    /** 固定的公共根目录，同时也是指针文件所在处。 */
    fun rootDir(): File = File(Environment.getExternalStorageDirectory(), ROOT_NAME)

    fun internalDbFile(context: Context): File = context.getDatabasePath(DB_NAME)

    /**
     * 指针文件里记录的文件夹。失效（被删、SD 卡拔掉、不可写）时退回 /sdcard/MemoApp。
     */
    fun chosenDir(): File {
        val root = rootDir()
        val raw = runCatching { File(root, POINTER_NAME).readText().trim() }.getOrNull()
        if (!raw.isNullOrBlank()) {
            val f = File(raw)
            if ((f.isDirectory || f.mkdirs()) && f.canWrite()) return f
        }
        return root
    }

    fun chosenDirPath(): String = chosenDir().absolutePath

    fun writePointer(dir: File) {
        runCatching {
            rootDir().mkdirs()
            File(rootDir(), POINTER_NAME).writeText(dir.absolutePath)
        }
    }

    /** 当前实际使用的数据库文件。 */
    fun dbFile(context: Context): File =
        if (hasAllFilesAccess()) File(chosenDir(), DB_NAME) else internalDbFile(context)

    /**
     * 冷启动、数据库尚未打开时调用：
     * 目标文件夹里没有库时，依次尝试从「应用内部」和「旧的公共目录」搬一份过来。
     */
    fun prepare(context: Context) {
        if (!hasAllFilesAccess()) return
        val target = chosenDir()
        target.mkdirs()
        val db = File(target, DB_NAME)
        if (db.exists()) return

        val internal = internalDbFile(context)
        if (internal.exists()) {
            copyDb(internal.parentFile ?: return, target)
            return
        }
        val legacy = File(rootDir(), DB_NAME)
        if (legacy.exists() && legacy.parentFile?.absolutePath != target.absolutePath) {
            copyDb(rootDir(), target)
        }
    }

    /** 把 from 目录里的库文件（含 -wal/-shm）整份复制到 to。 */
    fun copyDb(from: File, to: File) {
        to.mkdirs()
        for (suffix in listOf("", "-wal", "-shm")) {
            val src = File(from, DB_NAME + suffix)
            if (src.exists()) {
                runCatching { src.copyTo(File(to, DB_NAME + suffix), overwrite = true) }
            }
        }
    }

    /**
     * 切换存放文件夹。
     * - 新文件夹里已经有 memo.db：以它为准（重装后找回旧记录的路径）
     * - 空文件夹：把当前库搬进去
     * 返回切换后的文件夹，失败抛异常由调用方提示。
     */
    fun switchTo(context: Context, newDir: File) {
        if (!hasAllFilesAccess()) error("需要先开启「所有文件访问」权限")
        if (!newDir.isDirectory && !newDir.mkdirs()) error("无法创建文件夹：${newDir.absolutePath}")
        if (!newDir.canWrite()) error("该文件夹不可写，换一个试试")

        val target = File(newDir, DB_NAME)
        if (!target.exists()) {
            val current = dbFile(context)
            if (current.exists()) copyDb(current.parentFile ?: newDir, newDir)
        }
        writePointer(newDir)
    }

    /**
     * SAF 返回的目录 URI 转成绝对路径。
     * 有「所有文件访问」时可以按路径直接读写，因此这里只做映射；映射不出来（个别云盘）返回 null。
     */
    fun uriToDir(uri: Uri): File? {
        val docId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
        val parts = docId.split(":", limit = 2)
        val volume = parts[0]
        val relative = if (parts.size > 1) parts[1] else ""

        val base = when (volume) {
            "primary" -> Environment.getExternalStorageDirectory()
            "home" -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            else -> File("/storage/$volume")
        } ?: return null

        var dir = base
        relative.split('/').filter { it.isNotBlank() }.forEach { dir = File(dir, it) }
        return dir
    }
}
