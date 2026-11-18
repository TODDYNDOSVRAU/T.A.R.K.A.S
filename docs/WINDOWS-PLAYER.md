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
