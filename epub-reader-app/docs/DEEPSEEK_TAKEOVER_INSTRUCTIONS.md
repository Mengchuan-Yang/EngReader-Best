# DeepSeek API 接管操作指令（Android EPUB Reader）

## 1. 接管目标
让下一位开发代理在不丢失上下文的前提下，继续完成 EPUB 阅读器剩余 bug 修复，并稳定产出 APK。

## 2. 必须先读的文件
1. `docs/HANDOFF_FREEZE_2026-05-23.md`
2. `docs/REQUIREMENTS.md`
3. `docs/MILESTONES.md`

## 3. 本机固定路径
- 项目根目录：`C:\Users\as705\Documents\EPUB READER APK\epub-reader-app`
- APK 产物目录：`app\build\outputs\apk\debug\app-debug.apk`

## 4. DeepSeek 相关代码位置
- Provider 枚举：`app/src/main/java/com/example/epubreader/ai/AiProvider.kt`
- 网络请求实现：`app/src/main/java/com/example/epubreader/ai/NetworkProviderClients.kt`
- Provider 抽象接口：`app/src/main/java/com/example/epubreader/ai/ProviderTranslationClient.kt`
- Key 配置模型：`app/src/main/java/com/example/epubreader/settings/AiProviderSettings.kt`
- Key 安全存储：`app/src/main/java/com/example/epubreader/settings/SecureApiKeyStore.kt`

## 5. DeepSeek 接管约束
- 不删除现有 OpenAI/Gemini/DeepSeek 多 provider 结构
- 只在必要时修改模型名、base URL、请求字段
- 不把 API Key 写死在代码里
- 所有网络异常必须可读（HTTP code + body）

## 6. 开发执行步骤（给新代理照做）
1. 打开项目后先跑编译：
   - `./gradlew.bat assembleDebug`
2. 把当前 6 个严重问题逐项建任务，按优先级处理：
   - 书名/目录乱码
   - UI 常驻控件、沉浸式缺失、顶部留白
   - 左右翻页一页只一段
   - 夜间模式文本不可读
3. 每修完 1 项都立即：
   - 再次 `assembleDebug`
   - 在真机上最小回归（导入同一本中文 EPUB）
4. 全部修完后统一打包：
   - `./gradlew.bat clean assembleDebug`

## 7. 建议调试命令
- 编译：`./gradlew.bat assembleDebug`
- 清理后编译：`./gradlew.bat clean assembleDebug`
- 单元测试：`./gradlew.bat testDebugUnitTest`
- 安装到手机（ADB 已连通前提）：
  - `adb install -r app\build\outputs\apk\debug\app-debug.apk`

## 8. UI 问题修复的硬规则
- 阅读模式默认“内容优先”，控制层可收起
- 正文布局必须使用正确 Insets（状态栏、导航栏）
- 夜间模式必须同时调整背景与正文前景色
- 分页模式必须按可见区域重新分页，不能沿用滚动布局测量

## 9. 提交产出格式（给用户验收）
- 一份“修复清单”：每条包括【问题 -> 根因 -> 修改文件 -> 验证结果】
- 一个可安装的 debug APK
- 如有未完全修复项，必须明确写剩余风险与下一步

## 10. 切换 API 过程注意
如果是切换“开发代理”而非“应用内翻译 provider”，本项目代码无需为了代理切换而改业务逻辑。仅需确保：
- 交接文档完整
- 任务边界明确
- 可重复构建命令明确
