# EngReader Requirements Freeze (2026-05-23)

## Product Goal
Build an Android 14+ EPUB-only reader APK for personal use, optimized for fast English reading with inline AI Chinese annotations.

## Confirmed Scope
- App name: EngReader
- applicationId: com.engreader.app
- Languages: follows system language, only Simplified Chinese and English UI
- Device: portrait only (v1)
- Reader modes: horizontal paging + vertical scrolling (switchable)
- Edge-to-edge immersive status/nav bars required

## Core Reading Features (MVP)
- Import local EPUB via file picker
- Import EPUB via Android share-to-app
- Bookshelf with grid/list switch
- Sort modes switchable (default by recent read time)
- Remember reading progress
- Font size and line spacing controls
- Day/Night theme
- TOC chapter jump
- In-book keyword search with snippet results
- Bookmarks

## AI Annotation Features
- Tap English word -> inline annotation in text: word(中文释义)
- Long press sentence -> inline sentence translation annotation
- Translation target language fixed to Simplified Chinese
- Chinese-selected text should not trigger translation
- Annotation visible toggle (default: visible)
- Repeated word strategy switchable (default: annotate only on tap)
- Annotation style: smaller size + gray color
- Single annotation delete + undo
- No-network behavior: reading works, translation unavailable with clear short message
- Translation fallback order: DeepSeek -> Gemini -> OpenAI
- Fallback only on network/timeout/API errors
- Skip providers with missing API key

## API Provider UX
- User selects provider then pastes API key only
- No advanced parameters exposed in UI
- Default provider: DeepSeek
- Supported providers in v1: DeepSeek, Gemini, OpenAI

## Security & Backup
- API keys must be encrypted at rest
- API keys must NOT be exported in backup
- Full local backup/restore in ZIP:
  - books
  - annotations
  - reading progress
  - settings
  - bookmarks
- Restore strategy: full overwrite current local data
- Storage preference: visible public directory
- Imported books copied into app managed public folder

## Sync & Auth
- v1 works fully without login
- Future sync architecture reserved (Google Drive / OneDrive), no self-host server

## Delivery
- Milestones: 3
- Internal test builds for Milestone 1 and 2
- Signed release APK at Milestone 3 (temporary release signing key for now)
