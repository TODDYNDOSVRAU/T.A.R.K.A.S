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

## Pedido 3 (2026-11-18): motion, expanded layout, bar cover
- Track change: `TrackSwap` (PlayerMotion.kt) slides cover/title/artist 1/4 of their height, next one way and previous the other, 220 ms in / 150 ms out, decelerate, clipped, no layout jump. Used in the bar and the expanded panel.
- Expanded panel: opaque background (no veil/blur); cover is 384, 192 or 96 dp by what fits height and width (`ExpandMath.coverDp`), info block fixed 96 dp.
- Bar cover click toggles the panel (`ExpandedPlayer.toggle`), close animates 200 ms; playback is not touched. Esc also closes.
- Cover root cause: `TrackAssets.follow` ignored `artworkUrl` and returned without `filePath`, and ran only while the MUSIC screen was composed. Now a global collector follows the track, picks API cover first (`CoverSource`), falls back to the file, and drops stale results.
- Pending: live visual check, frame rate, resize behaviour, console errors.

## Validation (2026-11-25, pedido do ROOT)
Test: `windows/src/test/kotlin/com/example/windows/player/PlayerValidationTest.kt` (4 cases). Command: `bash .claude/sessoes/gradle.sh win-player -p windows -q test createDistributable` -> exit 0 (`win-player-v6.log`); player suites 49 tests, 0 failures, 0 errors; no exception or stderr output in them.
- Sizes 1024x640, 1366x768, 1920x1080, 2560x1440 x light and dark: pill is 384x96, its play, progress and prev/next stay inside it with no overlap and 48 dp targets; the full panel fills the area, cover, info, seek, controls and the back row stay inside it, cover/info/seek/controls do not overlap, the cover is 192 at 1024x640 and 384 above; the panel background is dark in the light theme too (luminance < 0.2).
- Timing on the test clock (16 ms frames): open 288 ms, close 240 ms at every size (targets 300 / 260). `ExpandedPlayer.progress` is monotonic, never jumps more than 25 % per frame.
- Resize with the panel open (2560x1440 -> 1920x1080 -> 1366x768 -> 1024x640 -> back): covers 384, 384, 384, 192, 384, 384, nothing leaves the area, playback keeps playing.
- Chevron closes the panel and playback keeps going.
- Found and fixed: the 24 dp progress strip overlapped the play button by 6 dp in the pill (row now 12 / 48 / 24); the panel recomposed every animation frame (visibility is now a `derivedStateOf`, the frame reads happen in the layer block).
- Captures (window only, `captureToImage`, no display capture): `windows/build/ui-evidence/player/{mini,full}-<size>-<theme>.png` and `timing.txt`.
- **Pending:** frame rate (needs the running app), real OS window drag, hover and Esc in the live app, the dock's glass and border (win-geral), media keys on a real keyboard. **Not done:** AAC/M4A/OGG, SMTC, cover carousel.
- Note: the open/close constants are 250 / 200 ms (`ExpandMath`), shorter than the shared `Motion` 300 / 260; both are inside the target.

## Audit of the 2.1.3 video (2026-11-25, owner via win-geral)
Causes found in the code, then fixed (no Spatial file touched, no feature removed):
- **Previous/Next felt wrong.** `DesktopPlayer.seek` only set a flag and the position stayed old until the worker reopened the file, so a second Previous read the old position (> 3 s) and restarted again instead of going back. `seek` now publishes the requested position at once. A track played outside the current list (`PlayerController.play(track, null)`) left Next/Previous resolving against the previous list; the list is now `[track]` when it is not in it.
- **Seek.** A tap or drag on the waveform and on the thin lines seeks; the engine is asked once, on release (a drag used to be able to ask on every move). The elapsed time follows the pointer while dragging (`SeekSection`). `seekFraction` does nothing while no length is known (it used to seek to 0), and the total falls back to the library length while the file opens (it read 0:00). After a natural end or an error the thread is gone: seek and play now start the file again from there (they were ignored).
- **Sync.** The published position is the audible one (frames written minus the line's buffered frames), not the decoder's. A stale seek no longer leaks into the next `play()`. `PlayerState.loading` and `TransportState.loading` show a progress ring (`PlayGlyph`) in the full player, the expanded panel, the mini player and the bar from the call to play until sound starts; before this the button read "paused".
- **Music page.** The middle band is centred in the room left, the cover drops one step (192 to 96) when the window is short, and the waveform has a playhead line, a hand cursor and a time label that follows the drag; the lines show a 12 dp knob on hover and drag. Grid tokens only.
- Tests: `PlaybackAuditTest` (13: rapid Next, double Previous, outside-list play, fractional seek without length, loading state, bars follow the engine, tap/drag/volume/disabled on the line, drag on the waveform with label then one seek, loading ring). Player suites 62 tests.
- **Pending / not measured:** `DesktopPlayer.play()` calls `stop()`, which joins the old worker on the caller (UI) thread for up to 2 s; rapid Next on a slow device could stall the UI. Not changed (engine threading). Real audio device behaviour, frame rate and the look in the running app are not verified.

## Home v2: side player and queue card stack (2026-11-25, owner via win-geral)
- **`Panels.sidePlayer`** (`player/SidePlayer.kt`, `SidePlayerPanel`): vertical player of the shell's left column. Real cover, title, artist, thin progress with elapsed/total, previous / play-pause / next at 48 dp. The cover opens `ExpandedPlayer`. Width 192 (below 1536) or 384 (shell's choice); the cover is `min(width - 24, height - 192)` and is dropped below 48, so a 576 dp window cuts nothing. The loading ring, problem line and track swap are the ones of the other transports. Registering it makes the shell hide the bottom transport.
- **`Panels.queueStack`** (`player/QueueCardStack.kt` + `player/QueueStackEngine.kt`, `QueueStackPanel`): Compose port of the Mascot Card Stack engine of the Home v2 prompt, fed by the real queue. Front card = the playing track; the cards behind = `PlayerController.neighbors().after` (the queue first, then list or shuffle order; at most 3, two drawn, the third hidden). No second queue, no invented tracks; the same song twice gets two cards (`id#copy` keys).
  - Engine numbers kept from the original: POSE spring 175/19, TILT 150/12.5, tilt 8 deg / 11 deg toward the pointer, swing-in pose (-0.86 W, -0.045 H, 0.9, -7 deg), throw at 20 % of the width or 420 dp/s (speed from the last 90 ms, zero if the hand rested 80 ms, clamp 3800), right drag damped, fan 0.29 / 0.11 when the area is 600 dp or more and 0.125 / 0.07 below, veil of the back cards by scale, 240 Hz sub-steps, front card armed 380 ms after the first show, text of the front card arriving line by line.
  - Gestures to the player: drag left -> `next()`, drag right -> `previous()` (only when the player can; otherwise the card springs back); click on a card behind -> `playQueueItem(k)` for an item of "A seguir" (queue) else `play(track)` of that list entry; Left/Right keys on the focused stage. Buttons or shortcuts elsewhere move the stack too, because it only follows the player: `sync(keys)` animates the difference (fly out left on Next, swing in from the left on Previous, glide and fade otherwise).
  - Size: `StackLayout.fit` picks the largest card on the grid chain (384, 192, 96) with as many back cards as fit the constraints the Home gives, nothing cut. Card = surface container, 1 dp hairline, cover 12 dp in, status (Tocando / A seguir), title, artist, album; 96 is the cover alone. No glow, no blur.
  - Motion only in `graphicsLayer` blocks that read `engine.tick`; no allocation per frame; the frame loop (`withFrameNanos`, woken by input or a sync) ends when everything is at rest. `reduceMotion` on: swaps are a fade, no tilt, no fan opening, no flight.
  - A11y: the cards behind have no focus and no actions; the stage has a live region (title, position of total) and a focus border; empty queue shows `queue_stack_empty`.
- Tests: `SidePlayerTest` (5) and `QueueCardStackTest` (22: pure engine, layout fit, throw thresholds, sync diffs, reduced motion, hit tests, then the stack over a real `PlayerController`: queue order, click on cards behind, drag left/right, short drag, buttons in sync, empty, single track, a11y); 27/27 pass. A `return@...` out of a composable lambda (`BoxWithConstraints`, `key`) compiled to a method the JVM refuses to load (`$$$$$NON_LOCAL_RETURN$$$$$.<anonymous>`, `ClassFormatError`); the stack uses if/else there. Test drags rest 200 ms before release, because injected events have no time between them and would read as an infinite throw speed.
- **Pending / not verified:** the look in the running app (tilt sign, z-order of the card entering on Previous), frame rate at 120 Hz, behaviour when a parent clips the card's swing outside the stage, real covers (the tests use files that do not exist, so the placeholder shows).
