# T.A.R.K.A.S. for Windows: player (win-player)

Package `com.example.windows.player` plus the engine in `com.example.windows.audio`.

## What exists

| Piece | File | Notes |
|---|---|---|
| Engine contract | `audio/AudioOut.kt` | what the controller needs; tests use a fake |
| Engine | `audio/DesktopPlayer.kt`, `audio/PcmMix.kt` | pause/resume, clamped seek, volume after the EQ, crossfade, `onEnded(crossfaded)` |
| Controller | `player/PlayerController.kt` | queue, order, shuffle, repeat, history, previous (restart past 3 s), crossfade arming, sleep timer, volume, saved state; rules come from the shared Android objects (`PlaybackOrder`, `CoverNeighbors`, `CrossfadePlanner`, `PlaybackRestore`, `SleepTimerPlan`) |
| Screen | `player/PlayerPanel.kt` | full player: cover tint, title, seek, controls, volume, sleep timer, crossfade, queue (move, remove, play) and file open; registered by `PlayerHost.register` in the Music slot |
| Media keys | `player/MediaKeys.kt` | global play/pause, next, previous, stop through JNA `RegisterHotKey` |
| Wave cache | `player/WaveformCache.kt` | same file layout as the Android store; the Android `WaveformStore` calls `android.util.Log`, so it is not shared |
| State | `SettingsPlayerPrefs` | scope `player` on the Room settings table; `PlayerHost.shutdown()` saves the position |

## Crossfade

A second `EqDsp`/`PcmPipeline` renders the incoming file; both follow `setEq`. Streams are mixed with equal power (`PcmMix.crossfade`). It needs the same sample rate and channels and a known length; otherwise the next track starts normally when the first ends. After a fade the active pipeline swaps, so the public `DesktopPlayer.dsp` is the filter of the track that is **not** playing on every second crossfade. Code that reads `player.dsp` (meters, response curve) must use `setEq` as source of truth, not `dsp`.

## Formats and system integration

- WAV, AIFF, MP3 (JLayer), FLAC (jFLAC): play.
- AAC/M4A/OGG through Media Foundation: **not done**. Java Sound has no decoder for them and Media Foundation needs a native bridge; not attempted.
- SMTC (system media overlay): **not done**. It needs WinRT and a packaged app identity; the MSI/EXE build has none. Media keys work through hotkeys instead. A key owned by another app is skipped (`MediaKeys.failed`).

## Pending

- Lyrics sheet, track details sheet, track actions menu, mini player in the shell, waveform seek bar and cover carousel: shared rules (`LrcParser`, `Id3Lyrics`, `PeakAccumulator`, `CoverNeighbors`) are compiled but no screen uses them yet.
- Strings: a few labels ("Open", "Crossfade", "min") are literals, not `Res.string`.
- Tests: `PlayerControllerTest` (19 cases: queue, seek, sleep, crossfade arming, restore) pass.

## Desktop redo (2026-11-18, owner correction)

- `player/PlayerPanel.kt`: now-playing fills the main area: cover and info left, full-width waveform seek (`WaveformSeek`, from `PeakAccumulator`, cached by `WaveformCache`), synced lyrics beside the cover when the area is at least 960 dp wide. Sleep timer and crossfade are popup menus. Queue, lyrics and details are side-pane kinds (`PlayerHost.SidePane`); until the shell sets `hooks.shellDrawsPane = true` the screen draws the pane itself at 384 dp.
- Queue pane: drag handle reorder (`DragMath`), Delete removes the selection with undo (Ctrl+Z), right-click menu (play now, play next, remove).
- `player/PlayerHooks.kt`: `PlayerHooks` for the transport bar and shortcuts: `state` (`TransportState`), `toggle`, `next`, `prev`, `seekFraction`, `seekBy`, `setVolume`, `volumeBy`, `toggleShuffle`, `cycleRepeat`, `togglePane`, `undoQueue`. Also `WaveformReader`, `LyricsLoader` (.lrc sidecar and ID3), `TrackAssets`.
- Status: code written; new tests in `PlayerHooksTest` (9 cases). **Pending**: compile and test run. Last run (`gradle.sh windows -p windows -q test --tests 'com.example.windows.player.*'`) exit 1 with 10 errors, all in `biblioteca/LibraryTable.kt` (other session); none reported in player files. Layout at 1024x640 to 2560x1440, light/dark, drag and context menu by hand: pending. Cover artwork: placeholder tile (no artwork loading on desktop yet).

## Visual spec (2026-11-18, docs/WINDOWS-VISUAL-REF.md)

- `player/PlayerTransport.kt`: `Panels.transport` on the 96 dp glass bar: cover 48 + title/artist left; prev/play/next over a thin progress line; volume line, queue and lyrics right. Queue and lyrics open in the shell `SidePane`; `Panels.toggleQueue/toggleLyrics` are set.
- Now playing: no tint gradient; cover 384 r12 (real art); lyrics at Title size, current line `onSurface`, others alpha 0.35, 250 ms ease; waveform seek kept.
- `player/PlayerArt.kt` (`CoverArt`): embedded ID3v2.3/2.4 APIC, FLAC picture, or `cover|folder|front|album.jpg|png` beside the file; none found gives null (placeholder tile). On each track change `ShellBackdrop.set(art, tint from Track.colorHex)`.
- Status: `gradle.sh windows -p windows -q test createDistributable` exit 0 (`CoverArtTest`, `PlayerHooksTest` included). **Pending**: seeing it in the running app (layout at four sizes, light/dark), M4A/MP4 and OGG covers, 16-bit-depth art edge cases.

## Expanded panel (owner order, WINDOWS-PEDIDO-2 item 3)

Tapping the 48 dp cover of the transport bar opens `ExpandedPlayerPanel` (`player/PlayerExpanded.kt`) over the main area through the new shell slot `Panels.overlay`; Esc closes it first (`Panels.closeOverlay`, wired in `Main.kt` Back), as does the "Back" button. Left: large cover (384 or 192 dp), title/artist, waveform seek, controls, volume. Right: Fila / Letras tabs reusing `QueuePane` and `LyricsView` (highlighted current line, dimmed neighbours, auto-scroll). Motion: `graphicsLayer` alpha and translationY only, 250 ms open / 200 ms close, decelerate curve (0, 0, 0.2, 1); nothing recomposes per frame beyond the animated value read inside the layer block.
Not ported from mobile: `CoverCarousel`/`PlayerTravel`/`PlayerExpand` depend on `android.os.SystemClock` and the touch-origin container transform; the desktop uses its own slide (`ExpandMath`).
Verified: `bash .claude/sessoes/gradle.sh windows -p windows -q test createDistributable` exit 0 (`win-player-r7.log`), `ExpandedPlayerTest` 5 tests. **Pending:** frame rate (60-120 fps) not measured; not seen in the running app; no carousel swipe on desktop.
