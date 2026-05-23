# Claude Code 接管操作指令（Android EPUB Reader - 第二轮）

## 1. 你的身份和任务
你是 Claude Code，正在接管一个 Android EPUB 阅读器项目。上一代理（Codex/GPT-5）已完成底层修复（书名乱码、色彩方案、分页算法、edge-to-edge），但真机验收发现 **UI 交互仍有严重问题**：

1. 点击正文无法唤出/隐藏菜单
2. 侧滑返回直接退出 APP（应该是退回书架）
3. 用户找不到左右翻页模式入口
4. 整体 UI 不符合 Material Design 规范

**你的任务**：按 Material Design 3 规范重新设计并实现阅读器 UI 交互。

## 2. 必须先读的文件（按顺序）
1. `docs/HANDOFF_FREEZE_2026-05-23.md` — 第一次冻结文档（背景）
2. `docs/HANDOFF_FREEZE_2026-05-23_2.md` — **本次冻结文档（当前状态，必读）**
3. `docs/REQUIREMENTS.md` — 需求规格
4. `docs/MILESTONES.md` — 里程碑计划

## 3. 项目路径
- 根目录：`C:\Users\as705\Documents\EPUB READER APK\epub-reader-app`
- APK 产出：`app\build\outputs\apk\debug\app-debug.apk`
- 主 UI 文件：`app/src/main/java/com/example/epubreader/ui/main/MainScreen.kt`（775 行）

## 4. 当前代码架构（重要）
- **没有 NavHost**：`Navigation.kt` 只是一个简单包装，`MainScreen` 通过 `state.screen` 枚举（`AppScreen.Shelf` / `AppScreen.Reader`）切换页面
- **ViewModel**：`MainScreenViewModel`（AndroidViewModel），管理所有状态
- **阅读模式**：`ReaderMode.VERTICAL`（LazyColumn 滚动）和 `ReaderMode.PAGED`（HorizontalPager 翻页）
- **Chrome 可见性**：`readerChromeVisible` 状态控制 TopAppBar + 内联控制栏的显隐

## 5. 修复优先级和具体方案

### Priority 1（立即修复）：BackHandler 拦截返回
**文件**：`MainScreen.kt`
**方案**：在 `MainScreen`  composable 中添加：
```kotlin
import androidx.activity.compose.BackHandler

// 在 MainScreen 函数体内，LaunchedEffect 块之后添加：
BackHandler(enabled = state.screen == AppScreen.Reader) {
    if (readerChromeVisible) {
        readerChromeVisible = false
    } else {
        viewModel.backToShelf()
    }
}
```
**逻辑**：阅读页 → 如果 chrome 可见则先隐藏 chrome → 再按返回回书架。

### Priority 2：点击正文切换 Chrome
**根因**：父层 `pointerInput` 被子层 `ClickableText` 消费事件。
**方案 A（推荐）**：移除父层 tap 检测，改为：
- 在垂直模式的每个 `ClickableText` 段落中，把单击（短按 < 300ms 且无文字选择）当作切换 chrome
- 在分页模式的 `HorizontalPager` 每页中，添加无文字区域的 tap 检测
- 同时保留 `readerChromeVisible` 的"自动隐藏"逻辑：chrome 显示后 5 秒无操作自动隐藏

**方案 B（更简单）**：仅在 `ClickableText` 的 `onClick` 中判断：如果点击位置不在文字上（offset < 0），切换 chrome

### Priority 3：Material Design 3 重构
**书架页（Shelf）**：
- 用 `Scaffold` + `FloatingActionButton`（导入）+ `NavigationBar` 或 `TopAppBar` 的 `actions`
- Grid/List 切换 + 排序切换 → 放在 TopAppBar 下拉菜单或底部 NavigationBar

**阅读页（Reader）**：
- Chrome 隐藏时：纯全屏阅读，顶部半透明书名，底部小指示条（向上箭头提示可唤出菜单）
- Chrome 可见时：用 `BottomSheet` 或底部工具栏替代当前的两行 `TextButton` 挤在顶部的布局
- 控制项重组为：阅读进度条 + 字号/行距滑块 + 模式切换 + 目录/搜索/书签
- 切换翻页/滚动模式放在显眼位置（如底部工具栏第一个按钮）

### Priority 4：验证原始 6 个 Bug 是否真正修复
验收清单见 `HANDOFF_FREEZE_2026-05-23.md` 第 9 节。

## 6. 构建和验证命令
```powershell
cd "C:\Users\as705\Documents\EPUB READER APK\epub-reader-app"
.\gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## 7. 硬约束
- ✅ 不删 DeepSeek/Gemini/OpenAI 多 provider 结构
- ✅ 不硬编码 API Key
- ✅ 每次改动后 `assembleDebug` 必须通过
- ✅ 保留 `ReaderMode.VERTICAL` 和 `ReaderMode.PAGED` 双模式
- ✅ 不新增非必要功能
- ✅ 优先 Material Design 3 组件（`Scaffold`, `TopAppBar`, `BottomSheet`, `FAB`, `NavigationBar` 等）

## 8. 产出要求
完成后输出：
1. 修复清单（每项：问题→根因→修改文件→验证结果）
2. 可安装 APK 路径
3. 剩余风险

## 9. 关键提示
- `MainScreen.kt` 文件很大（775 行，32118 字符），修改时注意不要截断
- 上一代理曾因 PowerShell here-string 长度限制导致文件损坏，建议用 Python 脚本或分段写入
- 字体大小范围：`fontScale` 默认 1f，`lineHeightScale` 默认 1.5f