# Windows port: NOVA UI spec (Android 2.4.x tablet landscape, WidthClass.EXPANDED >= 840 dp)

Source of truth: `docs/TAL_DESIGN_SYSTEM.md`, `docs/TAL_ARCHITECTURE_AND_OPTIMIZATION.md`, code under `app/src/main/java/com/example/` (abbrev. `A/`). Windows module: `windows/src/main/kotlin/com/example/windows/**` (abbrev. `W/`), plus its own `windows/src/main/kotlin/com/example/ui/{theme,components}`.
Android "isTabletConsole" = `WidthClass.EXPANDED && width > height && height >= 480 dp`. On desktop use the window size in dp with the same thresholds (MEDIUM 600, EXPANDED 840, `ReadingColumnMaxDp` 1536).

## 0. Current Windows state (verified by listing and grep)
- Shared verbatim from app (`sharedMain` in `windows/build.gradle.kts`): `geral/ui/theme/{Color,GridLayout,Tokens,ThemeMode}.kt`. So `Grid`, `Space`, `Radius`, `Shapes`, `Size`, `SquircleShape`, `gridHeight` already exist in windows. `SquircleShape` is used by 0 files under `W/` (grep), so cards are still flat.
- Windows own copies: `ui/theme/Theme.kt` (TarkasTheme, no Material You), `ui/theme/Type.kt`, `ui/components/Design.kt` (735 lines vs app 1018), `TarkasAlertDialog.kt`.
- Shell (`W/shell/Shell.kt`): old desktop layout: SideNav 192 dp (48+24 rail below 960 dp), TopBar, transport bar 96 dp full width, SidePane 384 dp. No floating dock, no KeptPages.
- Screens present: Home (550 lines), Library (304), Audio/EQ (AudioScreen 349 plus full `Eq*` set mirrored from Android), Assistente (632), Settings (355), Onboarding, Player (PlayerPanel 588, PlayerExpanded 171). No MiniPlayer composable; the transport bar is the equivalent.

## 1. Tokens NEW in Android, missing or different in Windows
| Token | Android value | File | Windows |
|---|---|---|---|
| `GridText.Title` | 28/34 sp Bold, tracking -0.025 em, SpaceGrotesk | `A/geral/ui/theme/Type.kt` | W has 24/30 SemiBold -0.02. Change to 28/34 Bold -0.025 |
| `AppText` LargeTitle/Title1/Title2/Title3 | 34/41 B, 28/34 B, 22/28 B, 20/25 SB | same | present; values not compared (pending) |
| `Motion` object | `fluid` damping 0.85 / stiffness 480; `fluidFast` 0.86 / 620; `fluidExit` 1.0 / 520; `gel` 0.62 / 348 (`gelExit` 1.0 / 348); `defaultEffects` 1.0 / 1600; `criticalSpatial`; `Settle` cubic (0.22,1,0.36,1); `PlayerEasing` (0.45,0,0.15,1); `ExpandMillis` 300, `CollapseMillis` 260; `rubberBand(overshoot, limit, c=0.55)`; `StaggerItems` 8, `StaggerStepMs` 30 | `A/geral/ui/theme/Motion.kt` (imports `android.content.Context`, not shared) | Missing. W has `W/equalizador/AudioMotion.kt` (own gel only). Create `ui/theme/Motion.kt` as a copy minus `AppMotion.readSystem(Context)`; feed `userReduced` from `state.reduceMotion`. Also copy `subPageTransition`, `sectionTransition(from,to,reduced)`, `springAt()` |
| `SurfaceStyle` SOLID/FROSTED/LIQUID (keys "solid/frosted/liquid"), `LocalSurfaceStyle`, `DefaultLiquidGlassOpacity` 0.25f, `LocalHazeState`, `LocalBackdrop`, `glassSurface`, `glassPill`, `glassControl`, `liquidGlass*` | enum uses `R.string` labels, Haze, AGSL | `A/geral/ui/theme/SurfaceStyle.kt` | Missing. SOLID = `colors.surface`. FROSTED = Haze blur 24 dp, noise 0.05, tint 0.58 dark / 0.62 light (Haze desktop target: pending). LIQUID has no AGSL on Skia desktop: map to FROSTED first (Skia RuntimeEffect later, pending). Start from `W/shell/Backdrop.kt` and `glass()` in Shell.kt |
| Card standard | `SquircleShape(18.dp)`; selectable items 12 dp; chat capsule 26 dp; `Shapes.pill` for dock | `A/geral/ui/theme/Tokens.kt` (shared) | Exists, unused. Recipe: `clip(shape).background(colors.surfaceContainer).border(Grid.hairline, onSurface.copy(0.08f), shape)`. Add one helper `studioCard()` |
| `WidthClass`, `rememberWidthClass()`, `.twoPane`, `readingColumn()` | COMPACT <600, MEDIUM 600-839, EXPANDED >=840 | `A/geral/ui/layout/WindowSize.kt` | Only a stub `androidx/compose/ui/platform/LocalConfiguration.kt` in W. Add `WidthClass` from `BoxWithConstraints` width |
| `LocalBarInset` | screens pad bottom by `barInset + Grid.u12` | `A/geral/ui/theme/*` (grep `LocalBarInset`, definition not read) | Missing; needed when the floating dock replaces the 96 dp transport bar |
| `NavLayout` (`share`, `slotWidths`, `weightFor`) | active nav slot grows by `activeWeight` | `A/geral/ui/NavLayout.kt` | Pure Kotlin, optional for the nav pill |
| `ScrollEdgeFade`, `SheetHandle` | | `A/geral/ui/` | Optional; sheets become dialogs/popovers |
Colours (`Color.kt`) and `TarkasTheme` already match the design doc; Material You is Android only.

## 2. Screens at EXPANDED
Frame (all but the full player): padding `start/end = Grid.margin (24)`, `bottom = barInset + Grid.u12 (12)`, gap `Grid.u12`, cards `SquircleShape(18.dp)`. Dock, bottom centre: MiniPlayer `width(384)` and nav bar `width(384)` side by side, gap 12, bottom padding 6 (`A/geral/ui/MainActivity.kt` l.994-1245, `bars(...)`). MEDIUM: stacked 480/384. COMPACT: full width.

| Screen | Layout (dp) | Android components | State |
|---|---|---|---|
| Inicio | Column: `SearchField` full width (tag `home_search_input`); then Row gap 12: left LazyColumn card `weight 0.58` ("Suas musicas": `MineSectionHeader`, first 5 tracks, `DemoToggle`); right Column `weight 0.42` with block 1 `weight 0.48` (Continuar ouvindo + Tocadas recentemente) and block 2 Catalogos online (`CatalogSection`, `TrendingRow`). Search text replaces the panes with results | `A/biblioteca/ui/HomeScreen.kt` `HomeScreen` l.102, tablet branch l.225-330 | `myTracks, demoTracks, recent, resume, searchQuery, showDemos`; `onOpenLibrary, onPlayTrack, onResume` |
| Biblioteca | Row: sidebar `width(210)` card (padding 6, gap 4), 9 items height 42, `SquircleShape(12)`, selected bg `primary 0.16`, `GridText.Small` SemiBold primary. Order SONGS, ALBUMS, ARTISTS, GENRES, FOLDERS, PLAYLISTS, FAVORITES, AUTO, QUEUE (queue shows count). Right Column gap 6: `SongsSearchAndSortBar(padHorizontal=false)` on top, then one card (`AnimatedContent` + `sectionTransition`) or, when a playlist/auto list/group is open, Master-Detail Row: list `0.42` + `DetailPane` `0.58` | `A/biblioteca/ui/LibraryScreen.kt` l.121 `LibrarySection`, l.690-800; `SongsSection`, `PlaylistsSection`, `GroupsSection`, `AutoListsSection`, `FolderPicker`, `TrackArtwork` | `section, openPlaylistId, openAutoName, openGroupKeys, queue, searchQuery, sort` |
| Player cheio | Full screen, always dark (`TarkasDarkOverride`). Two halves in landscape: cover + `CoverCarousel` left; title/progress/transport/actions right, `widthIn(max=440)`. Lyrics on: `tabletSideLyrics` puts lyrics in a side column. Open/close travel in `PlayerTravel`/`PlayerExpand` (300/260 ms) | `A/player/ui/MusicScreen.kt` l.95, 207-470; `PlayerControls.kt` (`TitleRow`, `PlayPauseButton` 88 dp, glyph 64, `ProgressRow`, `ScrubBar`, `TransportRow`, `VolumeRow`, `ActionsRow`); `CoverCarousel`, `PlayerBackdrop`, `LyricsSheet`, `QueueList`, `SleepTimerDialog`, `TrackActionsMenu`, `TrackDetailsSheet`, `WaveformBar`, `PlaybackProblemBanner` | player state (track, neighbors, isPlaying, progress, volume, problem), `favoriteIds`, `sleepTimer`, `showLyrics` |
| MiniPlayer | `Shapes.pill`, 384 wide in the dock: cover, title/artist, prev/play/next, thin progress; click opens player; horizontal swipe = prev/next | `A/geral/ui/CommonComponents.kt` `MiniPlayerBar` l.756 | `track, isPlaying, progress()` |
| Audio / EQ | `BoxWithConstraints`; tablet rack when `maxWidth >= 840 && pageHeight >= 480`. Column gap 12: `TabletTopConsoleBar` (route/path, power 96x48, preset 144 + Correcao 96 + menu 48, mode Grafico/Parametrico 192, Spatial, Compare, Settings/Advanced), then Row: left `weight 1` (Grafico: curve `0.46` + bands `0.54`; Parametrico: curve `weight 1` + bands `heightIn(max=180)`), right `width(360)` `TabletToneRack` = two cards `ToneGainRackBlock` (Tom e Ganho) + `OutputProtectionRackBlock` (Saida e Protecao). Narrower: older Row layout (`SIDE_MIN_WIDTH`) | `A/equalizador/ui/EqPowerConsole.kt` l.117-260, 412, 559-670; `AudioScreen.kt` l.112; `EqGraph`, `EqStrip`, `EqControls`, `EqSettingsSheet`, `EqAdvancedPage`, `SpatialPanel`, `SpatialStage`, `PresetPanel` | `EqSettings`, `AudioCapabilities`, `EqMode`, `activePreset`, `eqModified`, `CompareState`, `SpatialUi` |
| Assistente | All sizes: top bar one line min 40 (expandable status pill + Limpar with Sim/Nao confirm); chat centred `widthIn(max=760)`; empty state 4 cards (3 columns when not compact); bottom capsule `SquircleShape(26)` with buttons 40 (36 compact): `+` quick commands, field, mic, send; `ThinkingIndicator`, `TimerRow`, `MessageBubble` (retry) | `A/assistente/ui/AssistantScreen.kt` l.166-830 | messages, timers, voice state, online flag |
| Ajustes | Row: sidebar `250` card (3 T.A.L. layers ESSENTIAL/ADVANCED/ENGINEERING via `SettingsLayer`, quick links Sobre/Termos/Privacidade, device block, search) + main panel (`weight 1`, about 75%) with grouped `settingsGroup` lists. Sub-pages `SettingsPage` MAIN/ABOUT/TERMS/PRIVACY/LICENSES/CHANGELOG with `subPageTransition` | `A/geral/ui/SettingsScreen.kt` l.141-440, `SettingsLayers.kt` (`settingsGroupVisible`), `ThemeOptionRows`, `SurfaceOptionRows` l.1129 (SOLID/FROSTED/LIQUID picker) | prefs via `SettingsViewModel` |
| Onboarding | Not width-specific. Keep `W/geral/Onboarding.kt`, re-skin with tokens and squircle | `A/geral/ui/OnboardingScreen.kt` | |

## 3. Touch-only to mouse
| Android | Windows equivalent |
|---|---|
| Jelly edge-pull + `KeptPages` pager (`A/geral/ui/KeptPages.kt`, `JellyEdgePullOverlay` l.334, 96 dp pull, `Motion.gel`, haptic CONFIRM, `pagePosition` 0..3) | Drop the gesture. Click dock/sidebar, `Ctrl+1..5`, `Alt+Left/Right`. Keep the KeptPages idea (each tab keeps its state) and `sectionTransition` with `Motion.fluid` |
| Cover swipe (`PlayerCarousel`, `CoverCarousel`), mini swipe prev/next | Click the side covers; hover shows prev/next buttons; arrow keys |
| `CloseHandle`, pull-down to close | Chevron button + `Esc` |
| Drag fader/knob; double tap reset (owner rule, always kept) | Drag + mouse wheel (1 step, Shift = fine) + arrow keys; double-click resets; `rubberBand` at limits |
| Long press (track, band, preset) | Right-click context menu (`DropdownMenu`) + hover "more" button |
| Queue reorder drag (`QueueList`) | Mouse drag (keep) + `Alt+Up/Down` |
| Pull-to-refresh, bottom sheets | Refresh button / `F5`; sheets become anchored popovers or centred dialogs (`TarkasAlertDialog`) |
| Haptics (`LocalHapticFeedback`) | Remove |
| `ScrubBar` drag | Click-to-seek + drag + wheel +-5 s; hover time tooltip (`TooltipArea`, `Tooltip()` exists in Shell.kt) |
| Master-detail back | Row click selects; `Backspace`/`Alt+Left` closes the detail |
| Fixed widths (210 / 250 / 360) | Layout recomputes at 600/840 dp on window resize; optional draggable splitter for 0.58/0.42 (persist ratio) |
Media keys and shortcuts already exist (`W/player/MediaKeys.kt`, `W/shell/Shortcuts.kt`). Add hover state (+0.04 alpha surface) and pointer cursor to every clickable.

## 4. Ownership and order
| Session | Files |
|---|---|
| win-geral | `W/shell/*` (dock, sidebar replacement, WidthClass, LocalBarInset), `W/geral/*` (Settings, Onboarding, Components), `ui/theme/*` (Motion, SurfaceStyle desktop, GridText.Title fix), `ui/components/Design.kt` |
| win-player | `W/player/*` (MiniPlayer, PlayerExpanded two halves + lyrics column, travel) |
| win-biblioteca | `W/biblioteca/*` (Home 58/42, Library sidebar 210 + master-detail) |
| win-audio | `W/equalizador/*` (top bar, 360 rack, two blocks) |
| win-assistente | `W/assistente/*` (760 column, capsule, empty-state cards) |
Order: (1) win-geral tokens: `Motion.kt`, `SurfaceStyle` (SOLID first), `GridText.Title`, `WidthClass`, `studioCard()`; (2) shell: floating dock, remove the 96 dp transport bar, `LocalBarInset`; (3) Ajustes (sidebar 250; hosts the SurfaceStyle setting); (4) Biblioteca + Inicio; (5) Audio rack; (6) Player + MiniPlayer; (7) Assistente; (8) FROSTED blur, LIQUID last. Each step: JVM tests in `windows/src/test`; build via `bash .claude/sessoes/gradle.sh <sessao> <tasks>`.

## 5. Pending (not verified)
- `LocalBarInset` definition, `MiniPlayerBar` internal dp values, `PlayerCarousel` sizes, `TabletTopConsoleBar` internals (l.412-558) and `ToneGainRackBlock` contents were not read line by line.
- Settings main-panel "75%" is from the design doc; code was only seen as sidebar 250 plus the remaining width.
- Haze on Compose Desktop and an AGSL substitute for LIQUID: untested.
- `AppText` equality beyond `GridText` not compared.
- EQ top-bar element order comes from the owner decisions in CLAUDE.md, not re-confirmed in code.
- Doc says `pagePosition 0..3` (4 tabs); Windows `TarkasNavTab` mapping not checked.
