# T.A.R.K.A.S. 2.1: Windows parity (area Geral)

Written by `win-geral`, 2026-11-18. Scope: the Geral area (settings, onboarding, boot, legal, language, backup, diagnostics, update, tray, autostart, packaging). Other areas (Player, Biblioteca, Audio, Assistente) add their own rows.

Status legend, measured on 2026-11-18:
- **compiled**: the file compiles; `compileKotlin` reported no `e:` line in `windows/geral/**`, `Tray.kt` or `Main.kt` (logs `.claude/sessoes/win-geral-c3.log`, `c4.log`).
- **tested**: a JVM test exists and passed. Measured 2026-11-18: `gradle.sh windows -p windows -q test` exit 0 (`win-geral-t3.log`); geral suites 11 tests, 0 failures (Autostart 2, Backup 2, Licenses 1, Updater 6).
- **pending**: not verified. Nothing here is marked as done without a run.

| Android function | Windows equivalent | Status |
|---|---|---|
| Settings screen, 3 layers (ESSENTIAL, ADVANCED, ENGINEERING) | `geral/SettingsScreen.kt`, `SettingsLayer` tabs, search field | compiled; UI not seen running: pending |
| Theme (system, light, dark, AMOLED) and reduce motion | `TarkasState.themeMode`/`reduceMotion` rows in Settings | compiled; pending |
| Material You colours | Not applicable on Windows (no dynamic system palette). Option follows Windows light/dark only | by design |
| Language (system, pt-BR, en) | `AppLanguage.apply` at start (`GeralPanels.create`) and in Settings and onboarding | compiled; pending |
| Onboarding (welcome, look, language, terms, done; re-asks when terms change) | `geral/Onboarding.kt`, `GeralPanels.Gate` | compiled; pending |
| Boot with `MADE BY T.A.L.` | `BootScreen`; skipped for a start with Windows | compiled; pending |
| Terms, Privacy, Changelog, About | `LegalPage` over the shared `LegalTexts` (pt and en) | compiled; pending |
| Open-source licences | `WindowsLicenses`: Android library list replaced by what this build ships, adding JLayer (LGPL 2.1+) and jFLAC (Apache 2.0) | compiled; `LicensesTest` passed |
| Update (GitHub release, APK) | `Updater`: reads the latest GitHub release, picks `.msi` then `.exe`, requires https and a SHA-256 (digest or `.sha256` file), refuses installs with no published hash, runs the installer | compiled; `UpdaterTest` passed; real download/install not exercised: pending |
| Backup and restore (JSON) | `UserDataBackup` (format 2, `platform: windows`), validates everything before writing; file picker in Settings | compiled; `BackupTest` passed |
| Erase all data | Restores an empty document through the same validated path | compiled; pending |
| Diagnostics (crash log, events, export with redaction) | `Diagnostics` over the shared `DiagnosticStore`; crash handler installed in `Main.kt`; export to a file | compiled; pending |
| Start with the phone | Start with Windows: `HKCU\...\Run` value `TARKAS` with `--minimized`; only offered from the installed `TARKAS.exe` | compiled; `AutostartTest` passed; registry write not exercised: pending |
| Notification / widget | Tray icon (`Tray.kt`) with Open and Quit; option "close to tray" keeps playback running | compiled; pending |
| App icon | `windows/packaging/tarkas.ico`, `tarkas-tray.png`, `tarkas.png`, generated from the official logo by `windows/scripts/GenIcon.java` (never redrawn) | generated; visual check: pending |
| Installer | `packageMsi` / `packageExe` (version 2.1.0, per-user, upgrade UUID fixed, menu group `T.A.L.`) | `packageMsi packageExe` exit 0 (`win-geral-pkg.log`): `TARKAS-2.1.0.msi` and `TARKAS-2.1.0.exe` built; installing them: pending |
| Dynamic Play Store update flow, permissions prompts | Not applicable on Windows | by design |

## Not verified at the time of writing

1. Install and uninstall of the MSI/EXE on a clean machine: pending.
2. Any visual check of the Geral screens (grid, both themes, portrait/landscape equivalents): pending.
3. (WiX: `packageMsi` succeeded, so it was available.)
4. Real update download and install against a published release: pending (no 2.1 Windows release exists yet).

## Area Áudio (`win-audio`, 2026-11-18)

Desktop contract: `docs/WINDOWS-DESKTOP.md`. Sheets became a 384 dp side pane inside the Áudio screen; the parametric editor is a popover hung from its tile. Status measured 2026-11-18:
- **tested (earlier run)**: `gradle.sh windows -p windows -q test createDistributable` exit 0 (`win-audio-final.log`), before the desktop redo.
- **compiled (desktop redo)**: no `e:` line in `windows/equalizador/**` in `win-audio-d3.log`. The full run was blocked by other areas' files (`geral/SettingsScreen.kt`, `player/PlayerPanel.kt`).
- **pending**: not seen running at 1024x640, 1366x768, 1920x1080, 2560x1440 in light and dark; no window captures taken.

| Android function | Windows equivalent | Status |
|---|---|---|
| Console: title, on/off, preset row, curve, bands, dock, headroom | `EqPowerConsole.kt`, `EqConsolePage.kt` (side layout from 768 dp wide: dock in a right column) | compiled; layout at the four sizes pending |
| 10-band graphic faders under the curve's log axis | `EqStrip.kt` | tested (EqMathTest); compiled; pending visual |
| Parametric 96x48 tiles, "+" tile, editor bubble, undo on remove | `EqStrip.kt`: tiles flow in rows (`FlowRow`); editor is a popover centred under its tile, at most 384 dp wide, clamped to the window | compiled; pending visual |
| Double tap resets band, fader, knob | `EqControls.kt` `detectTapGestures(onDoubleTap)` (double-click) | compiled; pending |
| Mouse wheel and arrow keys step faders and knobs | `DesktopStep.kt` `desktopStep`, used by `EqFader` and `EqKnob` | tested (`DesktopStepTest`: step, ends, drift, stop size); compiled |
| Presets list, save, overwrite, rename, delete | `AudioState.kt`, `PresetPanel.kt` in the side pane | tested (`AudioStateTest`) |
| Headphone correction (AutoEq) and search | `PresetPanel.kt` CORRECTION tab in the side pane; `AutoEqRepository` | compiled; online fetch pending |
| Ajustes sheet (preamp, bass, treble, limiter, spectrum) | `EqSettingsSheet.kt` in the side pane (no ModalBottomSheet) | compiled; pending |
| Tarkas Spatial sheet with 3D stage | `SpatialPanel.kt`, `SpatialStage*.kt` in the side pane | tested (spatial delay frames); compiled |
| Output device choice | `AudioState.selectOutput` -> `DesktopPlayer.outputMixer` (Java Sound), shown in the ADVANCED sub-page | tested (`outputChoiceReachesThePlayer`) |
| Loudness normalisation | `AudioChain` gain from `AudioState` | tested (`loudnessGainLowersLoudTracks`) |
| Ctrl+E EQ on/off, Esc closes pane then sub-page | Esc: `BackHandler` in `AudioScreen.kt`. Ctrl+E: not wired by this area | Esc compiled; Ctrl+E pending |
| Spectrum bars, 5.1 without spatial, loudness from tags, per-output correction map | not ported | pending |

## Desktop shell (win-geral, 2026-11-18, after the owner's desktop correction)

| Item | Windows implementation | Status |
|---|---|---|
| Sidebar with icons, Ctrl+1..6 hints, collapses to a rail below 960 dp | `shell/Shell.kt` `SideNav` | compiled (no `e:` in shell/, log `win-geral-shell4.log`); visual check: pending |
| Top bar (route + area actions), side pane 384 dp, transport bar 96 dp slot | `Panels.topActions`, `SidePane`, `Panels.transport` | compiled; areas must register their parts: pending |
| Shortcut map (docs/WINDOWS-DESKTOP.md §4) | `shell/Shortcuts.kt` + `Main.kt` (Ctrl chords global, Space/Shift+arrows only when no control consumed them) | compiled; `ShortcutsTest` written, not run: pending |
| Window size, position, maximised kept (scope `window`), minimum 960x576 | `WindowMemory` | compiled; pending |
| Settings as category list + page, layers control at the top of the page, search across categories | `geral/SettingsScreen.kt`, `visibleRows` | compiled; `SettingsPagesTest` written, not run: pending |
| Old `PlayerPane` with loose dp/sp | removed from `Main.kt` | done (file no longer contains it) |
| Onboarding as two columns; Legal inside the content pane | not changed yet | pending |

Tests and `packageMsi packageExe` could not run: on 2026-11-18 the module had compile errors only in other sessions' files that were still being edited (`biblioteca/LibraryTable.kt`, earlier `assistente/`, `equalizador/`).

## Player (win-player, 2026-11-18)

| Feature | Windows | Status |
|---|---|---|
| Now-playing in main area, cover left, lyrics beside when wide | `player/PlayerPanel.kt` | code written, compile pending |
| Full-width waveform seek | `WaveformSeek`, `WaveformReader` | tests written (`PlayerHooksTest`), run pending |
| Synced lyrics (.lrc, ID3) | `LyricsLoader`, `LyricsView` | tests written, run pending |
| Queue pane: drag, Delete+undo, right-click | `QueuePane`, `PlayerHooks.undoQueue` | tests written, run pending; by hand pending |
| Sleep timer popup, crossfade menu | `SleepTimerButton`, `CrossfadeButton` | compile pending |
| Hooks for transport bar and shortcuts | `PlayerHooks` | tests written, run pending |
| Media keys | `MediaKeys` | pending on a real keyboard |
| AAC/M4A/OGG, SMTC | not done | not done |

## Assistente (win-assistente, 2026-11-18, desktop redo)

| Item | Windows implementation | Status |
|---|---|---|
| Two panes: history (192 dp) and chat at full width, no reading column | `assistente/AssistantScreen.kt` `HistoryPane` + chat column | tested: `AssistantScreenUiTest` asserts `assistant_history_pane` |
| Selectable text across messages; right-click Copy message / Copy conversation | `SelectionContainer` around the list, `ContextMenuArea` per bubble | compiled; clipboard result by hand: pending |
| Multiline input: Enter sends, Shift+Enter breaks the line | `ComposerField`, `insertNewLine` | tested: `ComposerNewLineTest` 2, `AssistantScreenUiTest` (types one, Shift+Enter, two, Enter sends `one\ntwo`) |
| Curiosity settings in a 384 dp side pane | hosted inside the Assistente panel (`assistant_side_pane`), not in the shell `SidePane` | compiled; moving it into the shell side pane: pending |
| Chat, commands, history, timers, Gemini REST, DPAPI key store, curiosities | engine and stores | tested: 41 assistente tests, 0 failures |
| Speech output (SAPI) | `Sapi.kt`; 2 voices installed here | compiled; audible check needs the owner: pending |
| Speech input | `Sapi.listen`; this machine has 0 speech recognizers, so only the Unavailable path (`assistant_voice_unavailable`) was exercised | real microphone recognition: pending |
| Real Gemini call, wiring of `DesktopAssistantHost.actions`, player commands, `Main.kt` registration | by the player, library and geral sessions | pending |

Full run `test createDistributable` (exit 1): 386 tests, 3 failed, all `player.PlayerHooksTest` (player session). Assistente: `--tests com.example.windows.assistente.*` exit 0.

## Biblioteca and Home (`win-biblioteca`, 2026-11-18)

| Item | Status |
|---|---|
| Three panes: sources/groups, sortable resizable track table, details | built; compiles (`compileKotlin` exit 0); on-screen check **pending** |
| Ctrl/Shift multi-select, arrows, right-click menu (play, play next, queue, playlist, show in Explorer, details) | logic tested (`TableLogicTest`, 12 tests); menu on screen **pending** |
| Drag to queue/playlist; drop of files and folders from Explorer | built; real drag and drop **pending** |
| Type-ahead, sort and column widths persisted | logic tested; persistence on screen **pending** |
| Home dashboard grid (resume, numbers, recent, most played, added, Audius, Jamendo) | built; online states and real network **pending** |
| Light/dark, 4 sizes, Break UI | **pending** |

Command: `gradle.sh win-biblioteca -p windows -q test --tests 'com.example.windows.biblioteca.*'` exit 0; 26 tests (CatalogParse 3, LibraryScanner 5, LibrarySort 4, LibraryStore 2, TableLogic 12), 0 failures. Full `test createDistributable`: not run by this session.

### Home per `WINDOWS-VISUAL-REF.md` (win-biblioteca, 2026-11-18)

| Item | Status |
|---|---|
| Speed dial: 2-column rows, 48 dp cover r6, hover tile r12, favourites + playlists + most played, click plays, right-click menu | built; data logic tested (`HomeDataTest`); on screen **pending** |
| Daily Mixes: 192 dp covers r12, wheel and Left/Right keys, shared `AutoLists` + top genres | built; scrolling by wheel and keys **pending** (not exercised) |
| Listening right now: current + player history | built; **pending** on screen |
| Empty sections show an honest empty state; `glass()` surfaces; Grid tokens only | built; contrast over a real backdrop **pending** |

`gradle.sh win-biblioteca -p windows -q test --tests 'com.example.windows.biblioteca.*'` exit 0. The Online trending lists left the dashboard; Audius/Jamendo remain in search results.
