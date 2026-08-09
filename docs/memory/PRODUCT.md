---
memory_type: product
authority: canonical
last_verified: 2026-08-10
update_when: user-visible behavior, scope, or product goals change
---

# Product memory

VoxReader is an offline-first Android EPUB/FB2 reader that combines visual reading with low-latency, character-aware speech. It is a proof of concept intended to feel like opening a normal ebook: speech is generated while reading, the active phrase is highlighted, and recurring characters retain stable voices.

## Current experience

- Open EPUB or FB2 through Android **Open with** or the drawer's **Open** action.
- Restore the latest book and its saved UTF-16 character offset.
- Read in horizontal pages or vertical scrolling. Horizontal chapters begin on a new page.
- Navigate with page taps, progress slider and chapter marks, table of contents, hyperlinks, and back/forward history.
- Configure font, size, alignment, paragraph spacing and indent, padding, colors, image layout, orientation, and keep-screen-on behavior.
- Enable Voice mode from the drawer. Playback starts at the first visible sentence, highlights the spoken phrase, follows it in vertical mode, advances pages, and exposes foreground system media controls.
- Use a sleep timer with configurable reminders and a final interaction grace period.
- Choose Procedural (Android TTS), AI Simple (Piper), or AI Advanced (KittenTTS Micro). Downloaded neural models live in app-private storage.
- Preview and assign character voices. With no imported map, only Narrator is available.
- Import a compatible speaker-analysis JSON. Known speakers receive stable colors; long-pressing highlighted text identifies the speaker. `unknown` remains visible as a diagnostic category.
- Optionally apply emotion-aware post-processing to any engine. Profiles are configurable per supported emotion and the feature defaults off.

## Product boundaries

- No voice cloning.
- No live LLM speaker inference during reading.
- No implicit upload of book text. Cloud/outsourced analysis is deferred and must be explicitly opt-in if implemented.
- No full library manager, DRM support, search, annotations, or bookmarks yet.
- EPUB CSS, complex tables, and advanced image layout are intentionally partial.
- Speaker attribution is supplied by an imported sidecar; without one, playback is narrator-only.

## Success criteria

Opening a book and enabling speech must be quick; transitions between prepared phrases should be seamless. A loading spinner is acceptable only while a model is prepared or when the speech buffer genuinely runs dry. Reader behavior must remain useful without any downloaded neural model or speaker map.
