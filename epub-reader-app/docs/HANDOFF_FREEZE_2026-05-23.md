# EPUB Reader 冻结交接文档（2026-05-23）

## 1. 交接目标
本次冻结目标是把当前 Android EPUB 阅读器工程状态完整交给下一位开发代理（DeepSeek API 驱动）。

## 2. 当前工程位置
- 工作目录：`C:\Users\as705\Documents\EPUB READER APK\epub-reader-app`
- APK 输出：`app\build\outputs\apk\debug\app-debug.apk`
- 项目文档：
  - `docs\REQUIREMENTS.md`
  - `docs\MILESTONES.md`

## 3. Git/版本状态（冻结时）
- 仓库位于上层目录：`C:\Users\as705\Documents\EPUB READER APK`
- 当前分支：`master`
- 现状：尚无首个 commit（`HEAD` 不存在）
- 说明：当前是“本地开发态”，不是“可回滚的版本控制态”。

## 4. 构建环境快照
- Android Gradle 项目，Kotlin + Jetpack Compose
- 关键配置（`app/build.gradle.kts`）：
  - `applicationId = "com.engreader.app"`
  - `minSdk = 34`
  - `targetSdk = 36`
  - `compileSdk = 36`
  - Java/Kotlin toolchain 17
- 主要依赖：Compose、Room、epub4j、Security Crypto、Navigation

## 5. 当前已知严重问题（来自验收截图与反馈）
1. 书名读取乱码或未读取到有效标题（显示如 `lglbu8k5`）
2. 阅读页 UI 控件常驻，阅读区被压缩，沉浸感差
3. 沉浸式状态栏未完成，顶部存在大面积留白
4. 切换为左右翻页后，一页只显示一小段内容
5. 目录（TOC）标题乱码或无语义（同样出现 `lglbu8k5`）
6. 夜间模式仅背景变暗，正文未反白，导致不可读

## 6. 高优先级代码入口（建议从这里接管）
- 阅读主界面与交互：
  - `app/src/main/java/com/example/epubreader/ui/main/MainScreen.kt`
  - `app/src/main/java/com/example/epubreader/ui/main/MainScreenViewModel.kt`
- EPUB 导入与文本抽取：
  - `app/src/main/java/com/example/epubreader/storage/EpubImportService.kt`
  - `app/src/main/java/com/example/epubreader/storage/EpubTextExtractor.kt`
- 数据模型与仓储映射：
  - `app/src/main/java/com/example/epubreader/model/Models.kt`
  - `app/src/main/java/com/example/epubreader/data/repository/Mappers.kt`
  - `app/src/main/java/com/example/epubreader/data/repository/RoomBookRepository.kt`
- 主题/暗黑模式：
  - `app/src/main/java/com/example/epubreader/theme/Theme.kt`
  - `app/src/main/java/com/example/epubreader/theme/Color.kt`

## 7. AI 接口相关现状（供 DeepSeek 继续）
- Provider 枚举已含：DeepSeek / Gemini / OpenAI
  - `app/src/main/java/com/example/epubreader/ai/AiProvider.kt`
- DeepSeek 客户端已接入 OpenAI 兼容聊天接口：
  - `app/src/main/java/com/example/epubreader/ai/NetworkProviderClients.kt`
  - 当前地址：`https://api.deepseek.com/v1/chat/completions`
  - 当前模型名：`deepseek-v4-flash`
- API Key 设置模型：
  - `app/src/main/java/com/example/epubreader/settings/AiProviderSettings.kt`
  - `app/src/main/java/com/example/epubreader/settings/SecureApiKeyStore.kt`

## 8. 建议的接手执行顺序（必须先 UI 再扩展）
1. 先修 EPUB 元数据与 TOC 解码（保证书名/目录可读）
2. 再修阅读页布局（控件收起、沉浸式、状态栏 insets）
3. 再修分页算法（左右翻页的分页高度/宽度测量）
4. 再修夜间模式配色（前景文字与背景对比）
5. 最后回归测试：滚动/分页/夜间/目录跳转/字体行距

## 9. 最低验收标准（回来验收用）
- 中文 EPUB：书名、作者、目录全部正常中文显示
- 阅读页默认只显示正文，菜单可唤起/可隐藏
- 顶部无异常空白，状态栏与内容区边界正确
- 左右翻页模式每页内容量正常（不再“一页一段”）
- 夜间模式文本清晰（前景与背景对比达可读）
- Debug APK 可安装可打开可导入 EPUB

## 10. 对下一代理的硬性要求
- 每次改动后都要可构建（`assembleDebug`）
- 每个 bug 修复都需要“复现 -> 修复 -> 回归”记录
- 优先保证 UI 稳定性，不新增非必要功能
- 不重构大模块，避免引入新风险
