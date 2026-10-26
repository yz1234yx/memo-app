package com.example.memo.data

import android.content.Context
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
        const val EXTERNAL_DIR = Storage.ROOT_NAME

        @Volatile
        private var INSTANCE: MemoDatabase? = null

        @Volatile
        private var openedPath: String? = null

        /** 是否已授予「所有文件访问」权限（API 30+ 才有此授权模型）。 */
        fun hasAllFilesAccess(): Boolean = Storage.hasAllFilesAccess()

        fun externalDir(context: Context): File = Storage.rootDir()

        fun externalDbFile(context: Context): File = File(Storage.rootDir(), Storage.DB_NAME)

        /** 当前应使用的数据库文件：已授权用公共目录（用户选定），否则退回应用内部。 */
        fun dbFile(context: Context): File = Storage.dbFile(context)

        /** 冷启动、数据库尚未打开时调用：确保目标文件夹有库，必要时把旧库搬过来。 */
        fun prepare(context: Context) = Storage.prepare(context)

        fun get(context: Context): MemoDatabase {
            val file = dbFile(context)
            android.util.Log.i("MemoStorage", "open db=${file.absolutePath} allFiles=${Storage.hasAllFilesAccess()}")
            synchronized(this) {
                val current = INSTANCE
                if (current != null && current.isOpen && openedPath == file.absolutePath) {
                    return current
                }
                runCatching { current?.close() }
                val db = Room.databaseBuilder(
                    context.applicationContext,
                    MemoDatabase::class.java,
                    file.absolutePath
                ).build()
                INSTANCE = db
                openedPath = file.absolutePath
                return db
            }
        }

        /** 存储位置变化后重新打开数据库，界面上的数据流需要一起重绑。 */
        fun reopen(context: Context): MemoDatabase {
            closeInstance()
            return get(context)
        }

        /** 关闭并丢弃当前实例（搬库文件前必须先调用，确保 WAL 落盘）。 */
        fun closeInstance() {
            synchronized(this) {
                runCatching { INSTANCE?.close() }
                INSTANCE = null
                openedPath = null
            }
        }
    }
}
