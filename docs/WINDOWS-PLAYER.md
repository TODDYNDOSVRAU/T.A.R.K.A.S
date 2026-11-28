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

## Position publishing and recomposition (2026-11-25, lag fix of Windows 2.1.5, via win-geral)

- **Cause:** `DesktopPlayer` wrote `positionMs` on every 512-frame chunk (about 94 times a second); `PlayerHooks.state` rebuilt a `TransportState` each time, so every screen collecting it recomposed at that rate and allocated on the audio thread.
- **Fix 1 (`audio/DesktopPlayer.kt`):** the worker keeps `lastPubMs` (a `Long`, no per-chunk allocation) and publishes the audible position only when it moved `DesktopPlayer.PositionStepMs` (100 ms). Seek, track change, crossfade takeover, pause/play, end of track and error always publish.
- **Fix 2 (`player/PlayerHooks.kt`, `player/PositionLeaves.kt`):** `PlayerHooks.steady` is `state` with the position held at 0, and only changes on track, play state, volume, queue size, error and the like. `SidePlayerBody`, `MiniPlayerBody` and `TransportBar` read `steady`; the clock is read only in the leaves `PositionSeekLine` (side player: line plus elapsed and total) and `PositionProgress` (mini and bar line). `PlayerPanel` (`SeekSection`, `LyricsView`) and `PlayerExpanded` still read `state`: they now recompose about 10 times a second, not 94.
- Test: `audio/PositionPublishTest` plays 2 s of silence through the real line and asserts at most 25 emissions per second of audio, at least 8, steps up to 400 ms and the full duration at the end (79.5 per second with the step at 0). Skipped on a machine with no sound line.
- **Pending / not verified:** frame rate and lag in the running app (no profiler run); `PlayerPanel` and `PlayerExpanded` are not yet split into leaves.

## Inicio v3, Onda 0: corner player, TiltCard, stack in the expanded player (2026-11-27, win-geral contract)
- **`Panels.corner`** (`player/CompactPlayer.kt`, `CompactPlayerPanel`): the compact player of every page, 384 x 96, in the shell's corner. The shell draws no pill once a corner is registered, so the player draws its own (`Shapes.pill`, `glass()`, hairline). Row one (12 dp from the top, 48 high): 48 cover, title and artist (a playback problem takes the artist's line), previous / play / next at 48. Row two (24 high, 12 below): elapsed, seek line, total (`PositionSeekRow`, the only reader of the clock) and a 96 dp volume line. On the Home only (`LocalCornerInsets.current.card`) a 192 art card sits above it with a 12 dp gap (`CompactMath`). A click on the cover, title or card opens `ExpandedPlayer`. The pill has no rotating layer: only the card leans, and a test keeps the pill still. The pill is liquid glass (see below). `Panels.transport` stays registered as the shell's fallback; `Panels.sidePlayer` still compiles but is no longer drawn.
- **Corner on the Music page: kept.** D10 says the same corner on every page, the old bottom bar was on Music too, and the expanded player (an overlay drawn after the corner) covers it when open. Hiding it would also change the shell's 120 dp reserve, which win-geral owns.
- **`IslandActivity.peaks`** is set to the track's waveform peaks (`assets.peaks`) when the player is built.
- **`TiltEngine`** (`player/TiltEngine.kt`, no Compose): two `Spring`s with the queue stack's numbers (k 150, c 12.5), limits `maxRx` / `maxRy` (8 / 11 by default), `aim`, `lean` (keyboard), `leave`, `reset`, 240 Hz sub-steps, `atRest`, `frames` counter. Reduced motion keeps the pose at 0.
- **`TiltCard`** (`player/TiltCard.kt`): `TiltCard(modifier, reduced, maxRx, maxRy, shape, shadow, keyboard, engine, content: TiltScope.() -> Unit)`. Rotation only through `graphicsLayer` (`rotationX/Y`, `cameraDistance` 16 x density). The pointer is watched from a non-rotated outer box and never consumed, so click, double click and drag inside work as without it. Depth layers: `Modifier.depth(d)` in the content shifts a layer by `ry·d·1.2` and `-rx·d·1.2`. A contrary shadow and a faint reflection (alpha at most 0.12). Frames run only while a spring moves. Tag `tilt_card`. A grid of covers uses `maxRx = 4f, maxRy = 6f`.
- **D5 / D4 on the card:** the art card crossfades in 200 ms on a track change; arrow keys lean it when focused and Esc returns it.
- **D6:** the stack of queue cards (`QueueCardStackBody`) now sits in the expanded player, beside the queue list, in a 192 column (`exp_queue_stack`) when the right card is at least 384 wide (`ExpandMath.stackFits`); not on the Lyrics tab. `Panels.queueStack` is set to null, so the Home no longer draws it. `QueueStackEngine` was **not** rebuilt on `TiltEngine`: it keeps its own pose and tilt springs (same numbers).
- Tests: `TiltEngineTest`, `TiltCardTest`, `CompactPlayerTest`, `ExpandedStackTest`, `LiquidGlassTest`, `LiquidGlassRenderTest`.
- **Pending / not verified:** the look in the running app, the frame rate at 120 Hz, the lean of the card under a real mouse.

## Liquid glass on the compact pill (owner, 2026-11-27)

Only the 384 x 96 pill (`LiquidGlass.kt`, used by `CompactPill`). Nothing else changed: the art card, the page and the Island keep their look, and the 24 margin and the card at x = 48 stay.

- **Refraction.** Compose has no `backdrop-filter`, so the shell records what is under the corner (`shell/GlassBackdrop.kt`): the window `Backdrop` and the page are each recorded into a `GraphicsLayer` while they draw as usual (`Modifier.recordsGlass`), and the pill replays both inside itself, shifted by the difference of their window positions. That copy (48 dp larger on every side, clipped to the capsule) gets one RenderEffect: an SkSL runtime shader that displaces by gradient noise (0.05 cycles per dp, 1 octave, seed 1, 35 dp at the extremes = the reference's `scale` 70 on R and B), then a blur of sigma 4 dp. The noise needs no sigma-2 blur: gradient noise at a 20 dp period is already as smooth. Shader and filter are built once per density; no uniform changes per frame, nothing allocates while drawing.
- **Border.** The nine shadows (inset -3 -3 strong glow .85, inset 3 3 almost faded .09, two 1 px hairlines .6, two soft inner fills .12 / .06, three outer shadows .03 / .08 / .15) are baked once per size, density and colours into one bitmap (`GlassRim.bake`) and always drawn, with or without the refraction. Inset shades use the theme's text colour (white in dark, black in light), outer ones the scrim. No loose colours.
- **States.** Hover grows the capsule 5% in 300 ms on cubic-bezier(.4, 0, .2, 1), pointer only, and the picture behind it is counter-scaled so the glass does not swim; no growth when disabled or with reduced motion. Disabled (nothing playing): content and border at 50%, glass stays. Keyboard focus: a 2 dp ring in the accent, one hairline outside the capsule, only when the input mode is keyboard.
- **Fallback.** No `LocalGlassBackdrop` (the shell not wired) or a layer not recorded yet: tint and border. A machine that cannot compile the shader: blur only (`GlassFx.Made.refracts` tells which).
- **Wiring in `Shell.kt`** (geral's file): create `val glass = remember { GlassBackdrop() }`, wrap the `Backdrop` and the corner in `CompositionLocalProvider(LocalGlassBackdrop provides glass)`, put `Modifier.recordsGlass(glass, GlassPart.Back)` on `Backdrop(...)` and `Modifier.recordsGlass(glass, GlassPart.Page)` on the `shell_content` box. A test (`recordingTheSourcesChangesNothingOutsideThePill`) keeps the recording from changing a pixel of the page.
- **Contrast.** Over the marble the title and the artist keep AA in both themes (`theTextOfThePillKeepsAAOverTheMarbleInBothThemes`), and the glass is no brighter than the plain tint. Over a pure white page (dark theme) or pure black one (light) the artist line falls below AA, as it did with the plain tint: that is the shell's `GLASS` alpha (.55), not the refraction (`build/glass-render/worst-page-contrast.txt`).
- **Pending:** GPU frame time at 120 Hz (an offscreen render cannot measure it) and the look under a real mouse in the running app.
- Evidence: `windows/build/glass-render/{dark,light}-{marble,cards}.png`.

### Glass effect contract on the pill (contract of `expo-glass-effect` v58, 2026-11-27, via win-geral)

`GlassModel.kt` (pure model, no window) and `LiquidGlass.kt` (`GlassView`) translate the vocabulary of the Expo page to Compose Desktop; no code is copied. The pill is `GlassView(effect = PillGlass.effect, tint = PillGlass.tint, interactive = true, ...)`.

- **`glassEffectStyle`** = `GlassEffect(style, animate = false, animationMs = null)`. `Regular` (default): more blur (3 dp, displace 3) and the translucent scrim (60% of `ShellBackdrop.GLASS` .55). `Clear`: less blur (1.5 dp, displace 6), no scrim, the backdrop shows. (Values at the owner's minimum, see below.) `None`: the plain surface, the scrim and a 1 dp hairline (`HAIRLINE_ALPHA` .12 of `onSurface`), no refraction. Object form animates a change of style over `animationMs` (300 ms T.A.L. when null) on cubic-bezier(.4, 0, .2, 1); without `animate`, with reduced motion, with reduce-transparency or without glass it changes at once.
- **No alpha on the glass, ever.** A style is a triple of weights (regular, clear, none, sum 1); the change interpolates the weights, drawing two backdrop layers (regular below, clear above, `regularAlpha` / `clearAlpha` give the exact mix) and a scrim of `GLASS` x `scrimShare`. A fade is therefore the animated switch glass to `none`.
- **`tintColor`** optional (`PillGlass.tint`), drawn at `TintAlpha` .16 over the scrim. **`isInteractive`** (ON for the pill): hover growth 1.05 in 300 ms, the backdrop counter-scaled, and a pre-baked radial highlight that follows the pointer (hover .6, press 1.0, peak .16; a non-consuming initial-pass pointer input, zero allocation per move). **`colorScheme`** `Auto | Light | Dark`: a forced scheme wraps the glass and its content in the T.A.L. light or dark colour scheme. The `GlassView` keeps the caller's modifier, `testTag` and semantics.
- **`isGlassAvailable()`** is true when the Skia blur filter can be built (`GlassFx.blurAvailable`). `effectiveStyle(requested, available, reduceTransparency)` returns `None` when the glass is unavailable or `LocalReduceTransparency` is on: the pill then falls back to the plain translucent `glass()` surface plus its 1 dp border, with no error. **The app has no "reduce transparency" setting yet**, so `LocalReduceTransparency` has no provider and is false; when geral adds one it only has to provide that local.
- **`GlassContainerModel`** (`spacing`, `gap`, `merges`, `groups`): model and tests only; there is one glass on screen.
- **Semantics probe** for tests: `GlassProbeKey` on the tagged node gives `GlassProbe(style, weights, refracts, available)`.
- **Contrast (AA 4.5, measured, `build/glass-render/worst-page-contrast.txt`).** Over the marble: AA holds for title and artist in every style and both themes. Over the worst page (white page in dark, black in light): Regular and None title 3.62 / 5.42 (dark / light), artist 1.21 / 1.68, limited by the shell's `GLASS` alpha (.55); Clear title 1.12 / 1.05, artist 3.36 / 3.39 because by contract it has no scrim. No hidden protective tint was added; a decision for the owner if Clear is ever used over unknown pages.
- **Evidence:** `windows/build/home-render/glass-{regular,clear,none}-{dark,light}.png` (the shell at 1280 x 800, from `GlassHomeRenderTest`; a copy in `build/glass-render/home/`, because `ShellHomeRenderTest` clears `home-render`). The idle pill is disabled there (nothing playing), so content and border are at 50%.
- **Pending / not verified:** GPU frame time at 120 Hz, the hover and highlight under a real mouse, and the contrast of the playing (enabled) pill over real pages.

### Glass at its minimum (owner, 2026-11-27: "coloca o liquidglass no minimo")

Read as the lowest visible intensity, not as off (`GlassEffect.None` and the fallback stay the way to turn it off). The API is unchanged; only the defaults went down:
- `GlassSpec.Regular` = blur 3 dp, refraction 3 dp, scrim 60% of the shell's glass alpha; `Clear` = blur 1.5 dp, refraction 6 dp, no scrim; `TintAlpha` .08; pointer highlight peak .10.
- Border (`GlassRim.Shades`, still nine): thin and discreet, bottom right stronger (edge light .30, hairlines .20 top left / .40 bottom right), soft fills .04 / .03, outer shadows .02 / .04 / .08 at 12 / 6 / 2 dp (no heavy shadow; the 192 art card's shadow above the pill is not the glass).
- **Legibility:** the idle pill no longer halves its content. `GlassView(dimContent = false)` (the pill passes it) leaves the text at its full colour; only the border and the previous / next icons dim (`GlassSpec.DisabledAlpha`). Before, "Nada tocando" and the times were at 50%.
- **AA measured in the shell** (`GlassHomeRenderTest`, `build/glass-render/home/contrast-home.txt`, 1280 x 800, idle pill, worst pixel beside the text; asserted >= 4.5): title 16.1-17.3 and times 4.95-5.28 in every style and both themes (lowest: Clear dark 4.95). The old synthetic worst page (pure white under dark, black under light) is still reported in `build/glass-render/worst-page-contrast.txt`; with the lighter scrim Regular drops there (title 1.91 / 2.48, artist 1.57 / 1.30), None is unchanged: that page is not a real one, but a page that bright under the corner needs the plain style.
- PNGs: `windows/build/home-render/glass-{regular,clear,none}-{dark,light}.png` (copy in `build/glass-render/home/`). `ShellHomeRenderTest` wipes `home-render` on start, so re-run `GlassHomeRenderTest` after it.

## Música page states (2026-11-27, page review via win-geral)

- **Nothing playing is an empty state, not a loading one.** `PlayerScreen` shows `EmptyState` (music-note icon, "Nothing playing", a one-sentence explanation `pd_empty_message`, and the single action `pd_empty_action` "Open Library", which calls `selectTab(LIBRARY)`; tags `music_empty` / `music_empty_action`). The 384 cover, the info block and the lyrics pane are not drawn at all in that state.
- **Lyrics have four states** (`LyricsStatus.of(hasTrack, settled, lyrics)`): `Idle` (no track: `pd_lyrics_idle`), `Loading` (track, lyrics not looked for yet: "Reading lyrics"), `None` (looked for, none found: the existing no-lyrics text), `Ready`. `TrackAssets.lyricsReady` is false from `follow()` until the lyric lookup ends, and a `finally` sets it when the cover or waveform read throws, so "Reading lyrics" cannot stay on screen for good. The expanded panel and the side pane use the same flag.
- **The artist line is not squeezed out.** The cover in the wide page is `pageCoverDp(bandHeight)`: 384 only when the band also holds 12 + title + artist (>= 492 dp), else 192. At 1280 x 800 the band is 428 dp, so the page now draws the 192 cover and keeps the artist line (before: the Column squeezed the line to 0 height, which is what the "no subtitle" in the review was).
- Tests: `MusicPageStatesTest` (7).

## Artist label and the pill's secondary text (2026-11-27, page review via win-geral)

- **"Unknown artist" is shown translated.** The scanner stores `LibraryScanner.UNKNOWN_ARTIST` ("Unknown artist") for a file without the tag; MiniPlayer, CompactPlayer, PlayerPanel (list, queue row, details), PlayerTransport, QueueCardStack and SidePlayer show it through `displayArtist(artist)` ("Artista desconhecido" in pt). Display only: the track's stored value is unchanged. Test: `UnknownArtistDisplayTest` (4: pill pt, pill en, a real artist untouched, queue stack pt).
- **The pill's secondary text is `PillText.secondary(cs)`** = `onSurface` at alpha 0.72 (`PillText.ALPHA`), used for the artist line and the elapsed and total times. With `onSurfaceVariant` (Technical Gray) it fell under AA over the lightest silk pixel, because the pill's glass is the surface at 0.55 x the style's scrim (0.33 regular, 0 clear). The glass stays at the owner's minimum (not raised to the reading alpha 0.72 of the Library cards). This departs from Technical Gray on the pill only; intermediate tone is base opacity, as the identity allows.
- **Measured by `PillWorstSilkContrastTest`** (worst silk pixel, same method as `LibraryPageLayoutTest.textOnGlassKeepsContrast`; before = `onSurfaceVariant`, after = `PillText`; title, accent for reference):

| style, theme (fill alpha) | title | secondary before | secondary after | primary |
|---|---|---|---|---|
| regular dark (0.33) | 11.44 | 3.81 | 6.81 | 8.59 |
| clear dark (0) | 9.25 | 3.08 | 5.69 | 6.94 |
| none dark (0.55) | 13.15 | 4.38 | 7.58 | 9.87 |
| regular light (0.33) | 13.51 | 4.18 | 6.89 | 4.15 |
| clear light (0) | 11.72 | 3.63 | 6.36 | 3.60 |
| none light (0.55) | 14.88 | 4.61 | 7.24 | 4.57 |

- **Open:** the accent `primary` as text on the pill (a playback problem in the artist line, the time while scrubbing) is 4.15 / 3.60 over the worst silk in the light theme (regular / clear), under 4.5. The play glyph is a graphic (3:1) and passes. Left as is, pending the owner's choice (a darker accent for text, or `onSurface` for the problem line).

## Cards behind the art card of the Home (owner via win-geral, 2026-11-27)

The playing cover keeps its 192 x 192 and its lean toward the pointer (`TiltCard`, untouched); the next two tracks of the queue sit smaller behind it, as in the owner's video. The Player page's `QueueCardStack` is unchanged (only `loadCover` and `CardCover` became `internal` to be shared).

- **Where:** `player/ArtCardStack.kt`. `ArtCard` in `CompactPlayer.kt` wraps the same `TiltCard` in `ArtCardStack`; the corner's layout (card 24 from the pill, 12 gap, pill 384 x 96) does not move. The stack's own box is 192 + 45 dp wide so nothing grows the corner.
- **Geometry (`ArtStackMath`, pure, tested):** the `Fan.Narrow` fan of the queue stack, reduced to the card: scale 1 / 0.93 / 0.86, centre shifted 24 / 45.6 dp to the right, sunk by `0.42 * (1 - scale)`, veiled with the page background 0 / 0.3 / 0.6. Hover opens the fan x1.3 with a tilt of 1.6 degrees per slot (`StackTuning.OpenFactor`).
- **Which tracks:** `neighbors()` current + the next `MaxBack = 2` (same source as the Player page), so it follows queue, shuffle and repeat. The manual Next wraps at the end of the list whatever the repeat mode, so the last song shows the first one behind it. One song or an empty queue draws no card behind.
- **Motion:** GPU-only (`graphicsLayer`), springs `0.72 / StackTuning.PoseK` per card keyed by the track id, so on Next the card behind rises to the front's place and on Previous the old current card slides back behind. Reduced motion places every card at once (`snap`).
- **Click:** the sliver of a card behind plays that track (`playCard`, same call as the Player page); the front card still opens the expanded player.
- **Evidence:** `ArtCardStackTest` (15 tests: numbers, corner unchanged, next / previous / wrap, click on the sliver, front click, hover, reduced motion) and `ArtCardStackRenderTest`, which writes light and dark prints at 1280 x 800 and 1920 x 1080 into `windows/build/art-stack-render/`, with a one-song baseline that proves the pixels behind the card.
