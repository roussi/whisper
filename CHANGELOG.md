# Changelog

All notable changes to the Whisper plugin are documented here. Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [1.1.1] - 2026-10-04

### Fixed
- Extend the IDE compatibility upper bound to `262.*` so build `IU-262.10968.63` is no longer rejected by the plugin loader.
- Preserve custom language/model values and unrelated settings changed from another settings panel.
- Keep model installation separate from saving settings, prevent duplicate inline installs, and ignore stale API-key test results.

## [1.1.0] - 2026-05-05

### Added
- **Whisper tool window** on the right sidebar — embeds the full settings UI with *Apply* and *Reset* buttons so you don't have to open Settings to switch backends.
- **Redesigned settings UI** under *Settings → Tools → Whisper*:
  - Card-style backend picker (Local / OpenAI / Groq) with Ready/Setup status badge per card.
  - Inline `Install / Update` button for the local backend — installs whisper.cpp + the selected model without leaving Settings.
  - Inline `Test` button for OpenAI and Groq cards — verifies the API key against the provider.
  - Audio section shows detected `sox` and `ffmpeg` paths and an install hint when neither is present.
  - Hotkey reminder shown in the Advanced section.
- Language picker suggests common languages and accepts custom language codes.
- Custom-binary override field for the local backend.

### Changed
- API key fields are now masked (`JBPasswordField`).
- Setup status is shown in the tool window instead of an automatic startup notification; the setup guide remains available on demand.

## [1.0.0] - 2026-04-XX

### Added
- Initial release: voice-to-text dictation for JetBrains IDEs.
- Three backends: local (whisper.cpp), OpenAI Whisper API, Groq (Whisper Large V3).
- Three dictation modes: Dictate, Code, Command.
- Status bar widget with red mic icon while recording.
- Default hotkey: ⌘M / Ctrl+M.
