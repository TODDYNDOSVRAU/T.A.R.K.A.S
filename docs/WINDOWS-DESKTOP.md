# T.A.R.K.A.S. for Windows: diagnosis and desktop architecture

Owner correction, 2026-11-18: the Windows build must be a real desktop application. Android is the reference for **functions, behaviour, identity and business rules**, never for **layout**. This file is the contract every Windows area session (win-player, win-biblioteca, win-audio, win-assistente, win-geral) follows. Written by win-geral. The audit evidence comes from a read-only pass over `windows/src` on 2026-11-18.

## 1. Diagnosis (what is wrong now)

Right, and to keep:
- **Technology and logic reuse.** 69 Android files compile unchanged from `app/src` (`windows/build.gradle.kts` sharedMain): DSP (Biquad, EqDsp, SpatialEngine, HRTF, Upmixer), loudness, output profiles, Room entities and DAOs, queue, crossfade, lyrics and LRC, waveform peaks, library sort, search, groups and auto lists, legal texts and DiagnosticStore. There are 22 shared test files.
- **Windows services.** The player engine (`audio/DesktopPlayer`), global media keys, SAPI voice, the updater with a SHA-256 gate, backup, diagnostics, autostart, tray, and MSI/EXE packaging.
- **Shell skeleton.** A side navigation already exists (`shell/Shell.kt`).

Wrong, and to redo:
1. **Every screen is an Android screen moved into a window.**
   - Settings, legal, onboarding and the assistant sit in a 768 dp `readingColumn` centred in a wide window (`geral/Components.kt:39`, `AssistantScreen.kt:205`).
   - The player is one stacked column, with the queue toggled underneath it (`PlayerPanel.kt:120-199`).
   - The library is made of full-width phone lists.
   - The equalizer opens `ModalBottomSheet`s (`AudioScreen.kt:237`, `EqConsolePage.kt:161,175`, `EqSettingsSheet.kt:54`).
2. **One panel per tab, so there is no multi-pane work.** You cannot browse the library while the queue, the now-playing track or the EQ stays in view. Transport controls exist only on the Music tab, and Space works only there (`Main.kt:73`).
3. **No desktop input.** There are no right-click menus, no drag and drop (from Explorer or inside the app), no shortcuts beyond Space and Esc, and no multi-select.
4. **Leftovers.**
   - `Main.kt` still holds the old `PlayerPane` with loose dp/sp.
   - `shell/Shell.kt:98` shows "port pending" for unregistered tabs.
5. **Unconfirmed parity.** Lyrics UI, crossfade wiring, Jamendo/MusicBrainz, speech input and the widget equivalent are not verified. The feature inventory (section 5) tracks them.

## 2. Technology decision

The app stays on **Kotlin + Compose Multiplatform Desktop (JVM)**, with only the presentation layer redone.

| Option | Reuse of the real Tarkas | Windows integration | Memory / distribution | Verdict |
|---|---|---|---|---|
| Compose Desktop (current) | DSP, Room, queue, library and legal logic compile unchanged from `app/src`, together with their JVM tests | JNA for Win32 (media keys, registry, SAPI), AWT tray, MSI/EXE through jpackage | bundled JRE, about 110 MB installer | **chosen** |
| WinUI 3 / .NET | None. Every Kotlin rule, the DSP and its tests would be rewritten in C#, and precision targets re-proven | best native look | small | rejected: rewrites working, tested systems |
| Electron / web | None for the DSP. Audio latency is worse | medium | heavier memory | rejected |

Rule: **reuse the intelligence, not the mobile layout.** A pure Android file (no `android.*`) is compiled from `app/src`. A Windows screen is written for the desktop and may share only models, state and logic with Android, never the Android composable layout.

## 3. Desktop structure (all areas)

```
+----------+-----------------------------------------------+-------------+
| Sidebar  | Top bar: route title · search (Ctrl+F) · actions | (toggle)    |
| 192 dp   +-----------------------------------------------+ Side pane   |
| (48 dp   |                                               | 384 dp      |
| collapsed)| Main area: the area's multi-pane content      | queue /     |
|          |                                               | lyrics /    |
|          |                                               | details     |
+----------+-----------------------------------------------+-------------+
| Transport bar 96 dp: cover · title · prev/play/next · seek with waveform · volume · EQ on/off · queue |
+-------------------------------------------------------------------------+
```

- **Sidebar.** It holds Início, Biblioteca, Player, Áudio, Assistente and Configurações, with shortcut hints. It collapses to 48 dp icons by itself when the window is narrower than 960 dp.
- **Top bar.** It shows the route ("where am I"), the area's search field and the area's actions. It never repeats the sidebar.
- **Transport bar.** It is always visible, on every area. Play, pause and seek work from anywhere. It replaces the Android mini-player and the Space-only-on-Music rule.
- **Side pane.** It is toggleable (Ctrl+Q for the queue, Ctrl+L for lyrics) and replaces bottom sheets for queue, lyrics, track details and EQ settings. Its width is 384 dp, from the 2x chain.
- **Bottom sheets are not used on Windows.** Anything that was a sheet becomes a side pane, an inline panel or a popup anchored to its control.
- **The reading column** (768 dp) is kept only for long prose (terms, privacy, changelog), inside the content pane. Settings and lists use the full width.
- **Grid and identity are unchanged.** The 2x chain, the 24 dp margin and 12 dp inset, text at 12, 18 and 24 sp, and the `Grid` / `GridText` / `MaterialTheme.colorScheme` tokens all still apply. Hit targets stay 48 dp, but controls are icon buttons and compact rows, not phone cards. The fonts are Space Grotesk, Inter and JetBrains Mono, with the Ice/Teal accent at about 5%.

### Per area

| Area | Desktop layout |
|---|---|
| **Biblioteca** | Three panes: sources and groups (artists, albums, genres, folders, playlists, auto lists) on the left; a **track table** with sortable, resizable columns (title, artist, album, duration, plays) in the middle; the details of the selection on the right. It supports multi-select (Ctrl/Shift-click), a right-click menu (play, play next, add to queue or playlist, show in Explorer, details), drag of tracks to the queue or a playlist, drop of files or folders from Explorer to import, and type-ahead search. Home is a dashboard grid (recent, most played, auto lists) using the full width. |
| **Player** | The now-playing view uses the whole main area: a large cover and track info on the left, a waveform seek across the full width, and lyrics (synced) beside the cover when the window is wide. The queue sits in the side pane and is reorderable by drag. The sleep timer is a popup from the transport bar. |
| **Áudio** | The owner's equalizer decisions (CLAUDE.md, 2026-11-15) stand: order, double-tap reset, faders under their frequency, band colour, parametric 96x48 pieces, presets, and Tarkas Spatial. On desktop, the curve and faders take the full width. Ajustes, Spatial stage and output/AutoEq move to the **side pane** instead of sheets. The parametric editor is a popover anchored to the piece. Mouse wheel over a fader or knob steps it, and arrow keys step the focused control. |
| **Assistente** | Two panes: conversation history on the left, the chat at full width on the right. Text is selectable and copyable, input is multiline, Enter sends and Shift+Enter breaks the line. Curiosity settings open in the side pane. |
| **Configurações** | Windows Settings pattern: a category list on the left (Aparência, Idioma, Reprodução, Windows, Atualização, Backup, Privacidade e legal, Diagnóstico) and the category page on the right. The ESSENTIAL, ADVANCED and ENGINEERING layers become a control at the top of the page. Search filters across categories. Legal documents open in the right pane. |
| **Onboarding / Boot** | Boot shows "MADE BY T.A.L.", centred. Onboarding is a wide two-column card: the steps list on the left, the step on the right. |

## 4. Desktop input (shared contract)

| Key | Action |
|---|---|
| Space | Play / pause anywhere, except when a text field has focus |
| Ctrl+→ / Ctrl+← | Next / previous track |
| Shift+→ / Shift+← | Seek ±5 s |
| Ctrl+↑ / Ctrl+↓ | Volume ±5% |
| Ctrl+F | Focus the search of the current area |
| Ctrl+1…6 | Go to sidebar entry 1…6 |
| Ctrl+Q / Ctrl+L | Toggle the queue / lyrics side pane |
| Ctrl+E | EQ on / off |
| Ctrl+, | Settings |
| Esc | Close the popup or side pane, then go back |
| Delete | Remove the selection (from the queue or a playlist), with undo |
| Ctrl+A / Ctrl+C | Select all, copy (track info, chat text) |

Also part of the contract:
- Right-click menus on every item.
- The window size, position, side-pane state and table column widths are kept between runs (settings scope `window`).
- Media keys and the tray are already done.

## 5. Feature inventory and parity

`docs/WINDOWS-PARIDADE.md` is the inventory: for each feature, where it lives on Android, its logic, its services, its Windows implementation, its test, and its **measured** status. Each area session fills in its rows. A feature is "done" only when it is reachable in the desktop UI, wired to the real service, and covered by a test or a recorded run.

## 6. Validation

- Unit tests: `bash .claude/sessoes/gradle.sh windows -p windows -q test`, exit 0.
- Window sizes: each area is captured at 1024x640, 1366x768, 1920x1080 and 2560x1440, in light and dark themes. The checks are no horizontal scroll, no centred phone column and the sidebar collapsing below 960 dp.
- Package: `packageMsi packageExe`, exit 0. Then install, run, uninstall.
