# Quillpad Handoff

更新时间：2026-05-09

## 当前状态

- 工作目录：`C:\Users\T8numen\Documents\Playground\_source_repos\quillpad`
- 协作规则：先读取 `C:\Users\T8numen\Documents\Playground\AGENTS.md`。默认用简体中文；修改项目时默认维护 `history.md`、版本号、APK/源码归档。
- 当前应用版本：`1.5.32 (74)`，位置：`app/build.gradle.kts`
- 最近一次验证：`.\gradlew.bat :app:assembleDebug` 通过。
- 最近一次安装：尚未安装 `1.5.32 (74)`；上一轮尝试安装 `1.5.31 (73)` 时 `adb devices` 显示 `172.19.0.1:32909 offline`，未完成安装确认。上一轮设备安装仍为 `1.5.26 (68)`。
- 最近一次安装后已执行 `adb logcat -c`，所以后续复测后可以直接抓最新崩溃日志。

## 重要用户偏好

- 默认尽可能中文化应用界面。
- 隐私/隐藏相关功能不要写入应用内 `whatsnew.yaml` 更新日志，除非用户明确要求。
- 用户经常会连接手机并允许通过 `adb` 安装、抓日志。
- 版本归档按当前做法放在：
  - APK：`apk-history/debug/app-debug-v<version>-<code>.apk`
  - 源码：`source-history/quillpad-v<version>-<code>-source.zip`

## 已实现的主要改动

- 预测性返回：升级导航依赖并开启相关 manifest 配置。
- 默认中文：首次启动默认 `zh-CN`，并补充多处中文文案。
- 莫奈主题预设：在主题/偏好枚举和 `themes.xml` 中新增若干预设。
- 指纹应用锁：锁定时主页、搜索、归档、回收站、笔记本列表隐藏内容，解锁后恢复。
- 更新日志入口：关于页新增 Changelog，公共更新记录来自 `app/src/main/assets/whatsnew.yaml`。
- LeakCanary debug 桌面入口已隐藏。
- 单向音量快捷切换：A 笔记可绑定 B 笔记；音量键触发切换。隐私笔记只能作为 A，不能作为 B。
- 侧边栏隐藏门：侧边栏“其他”无视觉变化；点击后进入静默倒计时，未输入密码时伪装打开关于页“网址”，设置项伪装为“文本过期时间”。
- 隐私页：目前不是独立可见目的地切换，而是默认主页内部通过 `ActivityViewModel.isPrivacyPageActive` 切换数据源，避免普通主页/隐私主页切换白屏。
- 隐私缩略图防护：隐私主页、隐私搜索页、隐私笔记编辑页会自动启用 `FLAG_SECURE`，离开隐私内容后清除，防止截图和最近任务缩略图暴露隐私内容。
- 图片插入：编辑器“插入图像”默认使用系统图片选择器，也可在设置 > 其他 > 选择图片默认使用中切回路径输入；路径输入弹窗里也有“选择”按钮。
- Markdown 图片私有化：通过系统图片选择器插入 Markdown 图片时，先复制到应用私有 `media` 目录，再把私有副本 URI 写入笔记；相册原图删除后不影响新插入图片显示。
- Markdown 图片选择器现在会保持打开系统选择器时的插入位置，并在复制取消时保留 coroutine cancellation。
- 编辑器底部工具栏：设置 > 其他 > 编辑底部工具栏，可勾选显示/隐藏按钮，并用上下按钮排序。

## 隐私页核心实现

- 隐私模式状态：`app/src/main/java/org/qosp/notes/ui/ActivityViewModel.kt`
  - `isPrivacyPageActive`
  - `setPrivacyPageActive(Boolean)`
- 主页按隐私状态切数据：`app/src/main/java/org/qosp/notes/ui/main/MainFragment.kt`
  - `isPrivatePage` 读取 `activityModel.isPrivacyPageActive`
  - 隐私主页按返回直接关闭隐私模式，不走预测性返回动画
- 主页数据源：`app/src/main/java/org/qosp/notes/ui/main/MainViewModel.kt`
  - `isPrivatePageFlow`
  - 隐私模式读取 `noteRepository.getPrivateNonDeletedOrArchived(sortMethod)`
- 隐私字段：`app/src/main/java/org/qosp/notes/data/model/Note.kt`
  - `isPrivatePage`
- 数据库迁移：`app/src/main/java/org/qosp/notes/data/AppDatabase.kt`
  - 当前已加过隐私字段迁移。

## 最近重点：锁屏/后台隐私收口

用户需求：

- 应用进入后台，或锁屏时：
  - 如果在隐私页主页，自动回默认主页。
  - 如果在隐私页笔记编辑页，先自动保存，再回默认主页。
- 用户不希望解锁后看到“编辑页 -> 隐私主页 -> 默认主页”的逐步返回过程。

当前实现位置：`app/src/main/java/org/qosp/notes/ui/MainActivity.kt`

关键函数：

- `schedulePrivacyReturnIfNeeded()`
  - `onStop()` 中调用。
  - 普通后台：立即调用 `preparePrivacyReturnToHome()`（`PRIVACY_RETURN_DELAY_MS = 0L`）。
  - 如果系统已锁屏：立即调用 `preparePrivacyReturnToHome()`。
- `screenOffReceiver`
  - 监听 `Intent.ACTION_SCREEN_OFF`，锁屏瞬间调用 `preparePrivacyReturnToHome()`。
- `preparePrivacyReturnToHome()`
  - 确认当前处于隐私主页或隐私编辑页。
  - 调用 `EditorFragment.persistCurrentInputState()` 保存编辑内容。
  - 设置 `pendingPrivacyReturnToHome = true`。
  - 调用 `showPrivacyReturnCover()` 加全屏遮罩。
- `applyPendingPrivacyReturnToHome()`
  - `onStart()` 中调用。
  - 在遮罩下执行返回默认主页/关闭隐私模式。
- `hidePrivacyReturnCoverWhenReady()`
  - 遮罩不会按固定时间直接关闭。
  - 只有确认 `navController.currentDestination?.id == R.id.fragment_main` 且 `!activityModel.isPrivacyPageActive.value` 后才移除遮罩。
  - 若短时间内还没到默认主页，会调用 `forceDefaultHome()` 再尝试。
- `updateSecureWindowFlag()`
  - 当前处于隐私主页、隐私搜索页或隐私笔记编辑页时启用 `WindowManager.LayoutParams.FLAG_SECURE`。
  - 离开隐私内容后清除 `FLAG_SECURE`，避免影响普通笔记截图。
- `MainActivity.refreshSecureWindowFlag()`
  - `EditorFragment` 数据加载完成后调用，确保打开既有隐私笔记时也能及时生效。

编辑页保存入口：`app/src/main/java/org/qosp/notes/ui/editor/EditorFragment.kt`

- `persistCurrentInputState()`
  - 保存标题。
  - 列表笔记保存任务列表。
  - 普通笔记保存正文。
- `isPrivateNoteEditor()`
  - 判断当前编辑页是否属于隐私笔记。

最近修过的崩溃：

- 锁屏收口关闭编辑页后，`EditorFragment` 中给三点菜单“共享”绑定长按的 `post/postDelayed` 回调晚执行，Fragment 已卸载还调用 `getString()`，导致 `Fragment not attached to a context`。
- 已在 `bindOverflowButton()` 和 `bindOverflowShareItemLongClick()` 中加 `isAdded || view == null` 保护。

## 最近版本记录

- `1.5.25 (67)`
  - 加全屏遮罩，避免锁屏/后台收口时暴露隐私编辑页逐步返回过程。
- `1.5.26 (68)`
  - 收紧遮罩关闭条件：必须回到默认主页且隐私模式已关闭才移除遮罩。
- `1.5.27 (69)`
  - 后台隐私收口从 15 秒改为立即执行。
  - 侧边栏“其他”隐藏门不再变色；2 秒无密码输入时打开关于页网站，2.5 秒内完成密码则进入隐私主页。
  - 已归档 `apk-history/debug/app-debug-v1.5.27-69.apk` 和 `source-history/quillpad-v1.5.27-69-source.zip`。
- `1.5.28 (70)`
  - Markdown 图片插入可默认打开系统图片选择器。
  - 设置 > 其他中新增“选择图片默认使用”，可选“图片选择器”或“路径输入”，默认“图片选择器”。
  - 插入图像路径输入弹窗新增“选择”按钮。
  - 已归档 `apk-history/debug/app-debug-v1.5.28-70.apk` 和 `source-history/quillpad-v1.5.28-70-source.zip`。
- `1.5.29 (71)`
  - 设置 > 其他中新增“编辑底部工具栏”，支持底部编辑器工具按钮显示/隐藏和排序。
  - 已归档 `apk-history/debug/app-debug-v1.5.29-71.apk` 和 `source-history/quillpad-v1.5.29-71-source.zip`。
- `1.5.30 (72)`
  - 隐私主页、隐私搜索页、隐私笔记编辑页启用 `FLAG_SECURE`，防止截图和最近任务缩略图暴露隐私内容。
  - 已归档 `apk-history/debug/app-debug-v1.5.30-72.apk` 和 `source-history/quillpad-v1.5.30-72-source.zip`。
- `1.5.31 (73)`
  - Markdown 图片选择器插入时复制到应用私有媒体目录，并在媒体清理时保留 Markdown 正文引用的私有图片。
  - 已归档 `apk-history/debug/app-debug-v1.5.31-73.apk` 和 `source-history/quillpad-v1.5.31-73-source.zip`。
- `1.5.32 (74)`
  - 忽略并清理本地 `.gradle-local` 缓存，避免 Gradle 缓存污染 Git 状态。
  - Markdown 图片选择器插入位置固定为打开选择器时的光标范围，并修正媒体复制取消处理。
  - 已归档 `apk-history/debug/app-debug-v1.5.32-74.apk` 和 `source-history/quillpad-v1.5.32-74-source.zip`。

对应记录：`history.md`

## 建议下个对话优先复测

1. 隐私页主页锁屏，再亮屏进入应用。
   - 期望：看不到隐私主页，直接看到默认主页或短暂普通背景遮罩。
2. 隐私页编辑页输入未保存内容后锁屏，再亮屏进入应用。
   - 期望：看不到编辑页到隐私主页的过程；回到默认主页；再次进隐私页笔记时内容已保存。
3. 隐私页主页切到后台后再返回。
   - 期望：遮罩覆盖中间态，最后默认主页。
4. 普通笔记编辑页锁屏/后台。
   - 期望：不触发隐私收口，不自动退回主页。
5. 普通/隐私笔记编辑页插入图像。
   - 期望：默认点击“插入图像”直接打开系统图片选择器；设置切到“路径输入”后先显示路径输入弹窗，弹窗“选择”按钮可再打开图片选择器。
6. 设置 > 其他 > 编辑底部工具栏。
   - 期望：可勾选隐藏按钮；可用上下按钮调整顺序；保存后进入编辑器，底部工具栏按配置显示。
7. 隐私主页/隐私搜索/隐私笔记编辑页切到最近任务。
   - 期望：最近任务缩略图不显示隐私内容；普通笔记页仍可正常截图。
8. Markdown 图片选择器插入图片后删除相册原图。
   - 期望：笔记中仍能显示图片；媒体清理后图片不会被误删。

如果复测后闪退，优先执行：

```powershell
adb logcat -d -v threadtime AndroidRuntime:E *:S
```

如果要看遮罩是否还有一闪而过的隐私画面，继续用用户提供的视频抽帧；本机已安装 ffmpeg，之前路径类似：

```powershell
C:\Users\T8numen\AppData\Local\Microsoft\WinGet\Packages\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe\ffmpeg-8.1-full_build\bin\ffmpeg.exe
```

## 注意事项

- 当前工作树有大量未提交改动，包含本轮所有功能。不要使用 `git reset --hard` 或 `git checkout --`。
- `fragment_privacy` 仍存在于导航图中作为兼容路径，但主要隐私主页体验已经转为 `fragment_main` 内部状态切换。
- 新增/修改隐私隐藏功能时，通常只更新 `history.md` 和版本归档，不写入 `whatsnew.yaml`，除非用户明确要求公开显示。
- 每次修改后按当前规则执行：
  - 更新 `versionCode/versionName`
  - 更新 `history.md`
  - `.\gradlew.bat assembleDebug`
  - 若设备在线则 `adb install -r app\build\outputs\apk\debug\app-debug.apk`
  - 归档 APK 和源码快照
