# MemoApp — 最小可用备忘录（Android / Kotlin + Compose + Room）

按「未完成、已完成、备注、修改、导出」五项需求从零搭建的原生 Android 工程。

## 功能

| 需求 | 实现位置 |
|---|---|
| 未完成 / 已完成 | `Note.done` + `Note.doneAt`，列表顶部 FilterChip 可按状态筛选 |
| 备注 | `Note.body`，编辑对话框内多行输入 |
| 修改 | 点列表项进入编辑；每次保存刷新 `updatedAt` |
| 导出 | 右上菜单，支持 JSON / CSV / Markdown，走系统文件选择器（SAF），不申请存储权限 |
| 附加 | 标题与备注关键词搜索、完成时间线排序、删除 |

导出的三种格式定位不同：JSON 保留全部字段，适合再导入或做备份；CSV 带 UTF-8 BOM，Excel 与 WPS 直接打开不乱码；Markdown 按「未完成 / 已完成」分节，适合贴进笔记或发给别人看。

## 结构

```
app/src/main/java/com/example/memo/
├── MainActivity.kt          列表、筛选、搜索、导出入口
├── MemoViewModel.kt         增删改与状态切换
├── data/
│   ├── Note.kt              Room 实体（含 toggled() 纯函数）
│   ├── NoteDao.kt           查询与写入
│   ├── MemoDatabase.kt      单例数据库
│   └── ExportUtil.kt        JSON / CSV / Markdown 渲染
└── ui/
    ├── Theme.kt             Material3 明暗主题
    └── NoteEditorDialog.kt  新建 / 修改对话框

app/src/test/java/com/example/memo/data/
├── ExportUtilTest.kt        导出契约（9 个用例）
└── NoteTest.kt              完成态切换语义（4 个用例）
```

## 构建

### 命令行（已验证）

```bash
export JAVA_HOME="C:/Users/00152/.workbuddy/binaries/java/extracted/jdk-21.0.12.1+1"
export ANDROID_HOME="D:/android-sdk"
D:/android-tools/gradle-8.9/bin/gradle.bat assembleDebug \
  -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7897 \
  -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7897
```

产物：`app/build/outputs/apk/debug/app-debug.apk`（9.3 MB）。

### Android Studio

1. 打开 `MemoApp` 目录。
2. 确认 Gradle JDK 指向 JDK 17 或 21，SDK 指向 `D:/android-sdk`（已在 `local.properties` 中固定）。
3. Sync 后 Run。首次打开时 IDE 会提示补全 Gradle Wrapper，同意即可；仓库未附带 `gradle-wrapper.jar` 二进制。

## 验证状态

**已编译 + 单元测试通过。**

- `assembleDebug`：BUILD SUCCESSFUL，产物 `app-debug.apk` 9.3 MB。
- `aapt2 dump badging`：包名 `com.example.memo`、minSdk 26、targetSdk 35、启动 Activity `com.example.memo.MainActivity`。
- `testDebugUnitTest`：**13 个用例全部通过，0 失败 0 错误**，覆盖 JSON/CSV/Markdown 三种导出契约与完成态切换语义。

**未做运行验证（外部阻塞）。** 无连接实机，且本机 `HypervisorPresent=False`（未启用 WHPX/HAXM），安装模拟器需管理员权限开启系统组件并重启。因此 Room 建库、屏幕交互、SAF 实际写盘三条链路仍只有编译期与单元测试保证。

版本组合：AGP 8.7.3 / Gradle 8.9 / Kotlin 2.0.21 + Compose 编译器插件 2.0.21 / KSP 2.0.21-1.0.28 / Compose BOM 2024.12.01 / Room 2.6.1 / compileSdk 35 / minSdk 26，字节码目标 Java 17。

## 单元测试

```bash
gradle testDebugUnitTest
```

覆盖范围：

| 用例组 | 断言的关键行为 |
|---|---|
| `ExportUtilTest`（9 个） | JSON 可被解析且字段一一对应；引号、反斜杠、换行、制表符、控制符转义后可无损还原；空列表输出合法结构；CSV 带 UTF-8 BOM、表头顺序固定、内部引号翻倍、含换行备注被正确包裹；Markdown 按状态分节且未完成在前、空节写 `_无_`、多行备注逐行缩进 |
| `NoteTest`（4 个） | 勾选时写入 `doneAt` 并刷新 `updatedAt`；**撤销时必须清空 `doneAt`**；连续切换两次回到初始状态；切换不改动 `id` 与正文 |

为了让导出逻辑能在 JVM 上真实执行，`ExportUtil` 未使用 `org.json`（平台 API 在单元测试中只有桩实现），改为手写最小 JSON 序列化；JSON 合法性由测试侧的 Gson 解析反向校验。

## 下一步可加的东西

- 数据库升级到 version 2 时必须在 `MemoDatabase` 中补 `Migration`，否则已安装用户启动即崩。
- 数据量大后把关键词搜索换成 Room 的 FTS4 表。
- 需要 PDF 导出时引入 `android.graphics.pdf.PdfDocument`，不必依赖第三方库。
- 需要多端同步时，JSON 导出即为天然的中间格式。
- 接入设备后可补 `androidTest`：用 `Room.inMemoryDatabaseBuilder` 验证 DAO，用 Espresso 或 Compose Test 走一遍「新增 → 勾选 → 筛选 → 导出」。
