# EPUB Reader 第二次冻结交接文档（2026-05-23 #2）

## 1. 交接背景
第一次冻结后，由 Codex（GPT-5）代理接手完成了 6 项代码修复并产出可构建 APK。
真机验收发现 UI 交互仍存在多个问题，现冻结进度交给下一代理（Claude Code）继续。

## 2. 项目路径
- 根目录：`C:\Users\as705\Documents\EPUB READER APK\epub-reader-app`
- APK 产物：`app\build\outputs\apk\debug\app-debug.apk`
- 文档目录：`docs\`

## 3. 本轮已完成的工作

### 3.1 书名/TOC 乱码修复（EpubTextExtractor.kt）
- `looksLikeRandomAscii` → 重写为 `looksLikeHashOrId`，只过滤真正的哈希/随机 ID
- TOC `buildTocTitleMap` 不再静默丢弃标题
- 效果：中文书名和目录不应再显示 `lglbu8k5` 类乱码

### 3.2 色彩方案完善（Color.kt + Theme.kt）
- 新增 `onPrimary`、`surfaceVariant`、`onSurfaceVariant`、`outline` 四组色值
- 暗色模式文字色从 `#F0E7DC` 微调至 `#F5EFE6`
- Material3 `darkColorScheme` / `lightColorScheme` 均填写完整 8 色参数

### 3.3 Edge-to-Edge 修复（MainActivity.kt）
- `enableEdgeToEdge` 使用 `android.graphics.Color.TRANSPARENT` 替代 `0x00000000`

### 3.4 阅读页交互+分页算法重构（MainScreen.kt）
- `readerChromeVisible` 默认 `false`（打开书时全屏无控件）
- 在 `ReaderContent` 顶层 `Column` 添加 `pointerInput` 捕获 tap 意图切换 chrome
- TopAppBar 显式设置 `colors` 参数
- `buildPagedSegments` 的 `maxCharsPerPage` 改为基于屏幕尺寸和字体动态计算
- `ImmersiveSystemBars` 添加 `WindowCompat.setDecorFitsSystemWindows(window, false)`

### 3.5 构建结果
- `.\gradlew.bat clean assembleDebug` → **BUILD SUCCESSFUL**
- APK 已通过 ADB 安装到手机（`adb install -r`）

## 4. 真机验收发现的未解决问题

### 4.1 🔴 点击正文无法切换菜单（最高优先级）
**现象**：点击阅读正文区域时，chrome 不出现也不消失。
**根因分析**：
- `ReaderContent` 顶层 `Column` 有 `pointerInput(Unit) { detectTapGestures(onTap = { onToggleChrome() }) }`
- 但内层的 `ClickableText`（垂直模式）和 `HorizontalPager`（分页模式）各自消费了触摸事件
- Compose 的 `pointerInput` 在父控件上会被子控件的点击事件拦截，tap 无法穿透到父层
**建议修复**：
- 方案 A：在 `ClickableText` 的 `onClick` 和 `detectTapGestures` 中也调用 `onToggleChrome`
- 方案 B：移除父层 `pointerInput`，在每个子文本区域独立处理 tap-to-toggle 和翻译的区分（如用双击切换 chrome、单击翻译）
- 方案 C：引入 `BackHandler` + 底部小横条提示用户从底部上滑唤出菜单

### 4.2 🔴 屏幕侧边滑动返回直接退出 APP
**现象**：从阅读页左滑/右滑返回时，APP 直接退出而不是回到书架。
**根因分析**：
- `Navigation.kt` 中没有 `NavHost`，只有一个 `MainScreen` 通过 `state.screen` 枚举切换 Shelf/Reader
- `MainScreen.kt` 中没有 `BackHandler` 拦截返回事件
- Android 的预测性返回手势（predictive back gesture）找不到 Compose 层级的返回目标，直接调 `Activity.finish()`
**建议修复**：
- 方案 A（推荐）：在 `MainScreen` 中添加 `BackHandler(enabled = state.screen == AppScreen.Reader) { viewModel.backToShelf() }`
- 方案 B：将 `Navigation.kt` 改为使用 `NavHost` + `composable()` 路由，天然支持返回栈

### 4.3 🟡 看不到左右翻页模式入口
**现象**：用户在阅读页找不到切换"左右翻页/上下滚动"的按钮。
**根因分析**：
- 切换按钮在 `TopAppBar` 的 `actions` 中，但 chrome 默认隐藏（#4.1 导致无法唤出）
- 即使唤出 chrome，TopAppBar 中按钮较多（Hide UI / Bookshelf / Paged-Scroll / Theme / Bookmark），切换入口不够明显
**建议修复**：
- 先修复 #4.1 让 chrome 可唤出
- 在 chrome 可见时，`ReaderContent` 内的控制栏第一行已有 `Prev/Next/TOC/Search/Bookmarks`，但 ReaderMode 切换在 TopAppBar
- 建议把 ReaderMode 切换按钮也放进 `ReaderContent` 的控制栏中
- 或者：chrome 隐藏时在底部加一个浮动 FAB 或小指示条

### 4.4 🟡 整体 UI 不符合 Material Design 规范
**建议方向**：
- 使用 Material3 的标准组件：`NavigationBar`、`FloatingActionButton`、`BottomSheet`
- 阅读页控制考虑用 `BottomAppBar` 或 `ModalBottomSheet` 代替两行 `TextButton` 挤在顶部
- 书架页考虑用 `NavigationBar` 切换 Grid/List
- 导入按钮用 `FloatingActionButton`

## 5. 当前代码入口速查

| 功能 | 文件 |
|------|------|
| 主界面 + 书架 + 阅读器 UI | `app/.../ui/main/MainScreen.kt` |
| 导航（无 NavHost） | `app/.../Navigation.kt` |
| MainActivity | `app/.../MainActivity.kt` |
| ViewModel | `app/.../ui/main/MainScreenViewModel.kt` |
| EPUB 导入 | `app/.../storage/EpubImportService.kt` |
| EPUB 文本提取 | `app/.../storage/EpubTextExtractor.kt` |
| 数据模型 | `app/.../model/Models.kt` |
| 主题/色彩 | `app/.../theme/Theme.kt` + `Color.kt` |
| 字符串资源 | `app/src/main/res/values/strings.xml` |
| 数据层 | `app/.../data/` |
| AI Provider | `app/.../ai/` |

## 6. 构建命令
```
.\gradlew.bat assembleDebug          # 增量构建
.\gradlew.bat clean assembleDebug    # 清理构建
.\gradlew.bat testDebugUnitTest      # 单元测试（已知失败，遗留问题）
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## 7. 关键约束（下一代理必须遵守）
- 不删除现有 DeepSeek/Gemini/OpenAI 多 provider 结构
- 不硬编码 API Key
- 每次改动后必须 `assembleDebug` 验证可构建
- 优先修 UI 交互再扩展功能
- 保留 `ReaderMode.VERTICAL` 和 `ReaderMode.PAGED` 双模式

## 8. 建议修的顺序
1. **BackHandler**：加返回拦截，从阅读页返回书架（最快见效）
2. **Tap-to-toggle**：修点击正文切换 chrome（4.1）
3. **Material Design 重构**：按规范重排 UI 布局
4. 回归验证所有 6 个原始 bug