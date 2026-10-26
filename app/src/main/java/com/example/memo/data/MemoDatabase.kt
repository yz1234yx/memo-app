package com.example.memo.data

import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

@Database(
    entities = [Note::class],
    version = 1,
    exportSchema = false
)
abstract class MemoDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao

    companion object {
        /** 公共目录名，卸载应用后该目录不会被系统删除。 */
        const val EXTERNAL_DIR = "MemoApp"

        @Volatile
        private var INSTANCE: MemoDatabase? = null

        /** 是否已授予「所有文件访问」权限（API 30+ 才有此授权模型）。 */
        fun hasAllFilesAccess(): Boolean =
            Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()

        fun externalDir(context: Context): File =
            File(Environment.getExternalStorageDirectory(), EXTERNAL_DIR)

        fun externalDbFile(context: Context): File =
            File(externalDir(context), "memo.db")

        /** 当前应使用的数据库文件：已授权用公共目录，否则退回应用内部。 */
        fun dbFile(context: Context): File =
            if (hasAllFilesAccess()) externalDbFile(context)
            else context.getDatabasePath("memo.db")

        /**
         * 冷启动、数据库尚未打开时调用：把内部存储里的旧库整体搬到公共目录。
         * 只搬一次——公共目录已有库文件就跳过。-wal/-shm 一并复制，避免丢最近写入。
         */
        fun migrateToExternalIfNeeded(context: Context) {
            if (!hasAllFilesAccess()) return
            val ext = externalDbFile(context)
            if (ext.exists()) return
            val internal = context.getDatabasePath("memo.db")
            if (!internal.exists()) return
            if (!externalDir(context).mkdirs() && !externalDir(context).isDirectory) return
            for (suffix in listOf("", "-wal", "-shm")) {
                val src = File(internal.parentFile, "memo.db$suffix")
                if (src.exists()) {
                    src.copyTo(File(externalDir(context), "memo.db$suffix"), overwrite = true)
                }
            }
        }

        fun get(context: Context): MemoDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MemoDatabase::class.java,
                    dbFile(context).absolutePath
                ).build().also { INSTANCE = it }
            }

        /**
         * 升级表结构时的模板：把 version 提到 2，并在这里加 migration，例如
         * MIGRATION_1_2 = object : Migration(1, 2) { ... }
         * 直接改 version 而不加 migration 会让已装用户启动即崩溃。
         */
    }
}
