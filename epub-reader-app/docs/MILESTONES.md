# EngReader Milestone Plan

## Milestone 1 - Reader Foundation
Goal: usable local EPUB reader shell with immersive UI and persistent local library.

### Deliverables
- Edge-to-edge immersive layout (status/nav bars)
- Bookshelf (grid/list, sort modes)
- EPUB import (file picker + share intent)
- Public storage copy pipeline
- Basic reader open + chapter navigation + TOC
- Reading progress persistence
- Global reading settings persistence
- Day/night mode
- In-book search result list jump
- Bookmark add/list/remove

### Acceptance Checklist
- User can import EPUB from file picker
- User can share EPUB from another app into EngReader
- Imported book appears on shelf
- Open/close/reopen preserves progress
- Immersive status and navigation bars render correctly
- User can switch vertical/horizontal reading mode

## Milestone 2 - AI Inline Annotation
Goal: high-value reading assistance with provider fallback.

### Deliverables
- Provider selection + API key input screen
- Encrypted API key storage
- Word tap translation -> inline annotation
- Sentence long-press translation -> inline annotation
- Annotation visibility toggle
- Repeated-word strategy switch
- Translation fallback chain + short error toasts

### Acceptance Checklist
- Tap English word inserts Chinese annotation
- Long press sentence inserts Chinese translation annotation
- Chinese text tap does not translate
- Offline translation shows explicit short message

## Milestone 3 - Backup/Restore + Release
Goal: complete personal daily-use workflow and release package.

### Deliverables
- Full ZIP backup (excluding API keys)
- Full overwrite restore from ZIP
- Delete-book cleanup behavior
- Release signing setup (temporary key)
- Release APK output and install validation

### Acceptance Checklist
- Export backup ZIP includes user data and books
- Restore ZIP on clean install recovers reading state
- API keys not present in backup
- Signed release APK installs and runs

## Main-Agent and Sub-Agent Operating Model
- Main agent owns architecture, navigation, integration, and final merge.
- Worker A owns data persistence module.
- Worker B owns AI provider abstraction and settings contracts.
- Main agent resolves integration issues and runs final verification.
