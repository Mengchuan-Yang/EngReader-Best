# Changelog

## v0.1.0-pre-alpha (2026-05-23)

### Added
- EPUB 文件导入，自动解析章节、目录、元数据
- 垂直滚动 (Vertical) 和左右翻页 (Paged) 双阅读模式
- 点击英文单词 AI 翻译（DeepSeek / Gemini / OpenAI）
- 长按段落 AI 整句翻译
- 多 Provider fallback 容错链（DeepSeek → Gemini → OpenAI）
- 翻译结果自动标注在原文段落中
- 单词级翻译的整章/整书自动重复标注模式
- Material Design 3 阅读界面：沉浸式全屏 + 底部控制栏
- 字体大小、行距、段落间距、页边距独立调节
- 日间/夜间/系统三套配色方案
- 阅读样式弹窗调节（⚙ Style 按钮）
- 书架网格/列表双视图、多排序方式
- 阅读进度百分比显示
- EPUB 内嵌封面自动提取和缩略图显示
- 默认书籍封面 logo（无封面时）
- 书签添加/删除
- 全文搜索（关键词定位跳转）
- 目录 (TOC) 弹窗导航
- 备份/恢复（ZIP 格式含全部数据）
- API Key 加密存储（EncryptedSharedPreferences）
- 侧滑返回手势（阅读页 → 书架 → 退出）
- PAGED 模式浮动翻页箭头（`<` `>`）

### Fixed
- 书名/TOC 乱码（`looksLikeHashOrId` 过滤算法）
- Material 3 色彩方案不完整（补齐 8 色 darkColorScheme/lightColorScheme）
- Edge-to-edge 状态栏透明色值错误
- 分页算法每页只显示一段内容（动态 `maxCharsPerPage` 计算）
- 暗色模式下正文文字不可读（前景色对比度修正）
- 点击正文无法唤出控制栏（事件消费冲突修复）
- 翻译 API 主线阻塞（`withContext(Dispatchers.IO)` 修复）
- 阅读样式按钮全部挤在底部栏一行无法点击（改为弹窗模式）
- PAGED 模式卡封面页无法翻页（添加浮动导航箭头）
- 书架显示原始时间戳而非阅读进度（改为百分比）
- 右侧 margin 重复应用导致边距翻倍

### Known Issues
- 章节间无法连续滚动，需手动点击下一章
- EPUB 内嵌 `<img>` 图片暂不渲染
- PAGED 模式不支持点按查词（仅长按翻译）
- 部分 EPUB 封面提取可能失败
- 无单元测试覆盖
- `ClickableText` 使用了已 deprecated 的 API
