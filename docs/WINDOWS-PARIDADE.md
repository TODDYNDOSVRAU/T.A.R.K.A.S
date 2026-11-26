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

## Metas de precisão do Windows (decisão do dono, 2026-11-25)

Fonte: decisão do dono em 2026-11-25, relayada pela sessão ROOT: o Windows segue as mesmas metas do Android (`CLAUDE.md` do projeto, "Metas de Precisão Oficiais", 48 kHz). A medida ponta a ponta no S23 não se aplica ao Windows. Status medido em 2026-11-25 com `bash .claude/sessoes/gradle.sh win-audio -p windows -q test --tests '*EqDspTest*' --tests '*SpatialEngineTest*'`: saída vazia (`-q`), exit 0; `EqDspTest` 23 testes, 0 falhas; `SpatialEngineTest` 12 testes, 0 falhas (os mesmos arquivos do Android, incluídos em `sharedTest`).

| Meta | O que os testes compartilhados provam no módulo windows | Status |
|---|---|---|
| DSP EQ `<= 5 ms` | `EqDspTest.limiterDelaysBySomeMillisecondsAndCatchesThePeakInAdvance`: o pico sai no máximo 200 amostras depois (`2001..2200`) = `4,17 ms` a 48 kHz | provado (único atraso do EQ é o lookahead do limitador) |
| DSP Spatial `<= 24 ms` (`608 + 512` frames = `23,33 ms`) | `SpatialEngineTest.latencyConstantMatchesImpulse`: o atraso medido por impulso é igual a `LATENCY` (`1024 - 512 + 64 + 32 = 608` frames = `12,67 ms`). Os `512` do buffer de hop (`HOP`) somam `1120` frames = `23,33 ms` por aritmética das constantes; nenhum teste afirma a soma | `608` provado; soma `23,33 ms` só por conta: pendente um teste que afirme `LATENCY + HOP <= 1152` |
| Tempo por frame Spatial `< 10,67 ms` | `SpatialEngineTest.frameTimeOnTheJvm`: mediana `0,336 ms` por frame de `10,7 ms` nesta máquina, na JVM | provado na JVM do PC de desenvolvimento; não é medida do app em reprodução |
| Isolado: `± 0,05 dB` no centro do filtro | `EqDspTest.peakReachesItsGainAtTheCentreAndNothingFarAway`: `+6 dB` em 1 kHz com tolerância `0,05` | provado para um filtro PEAK; shelves e passa-alta só com tolerância `0,1` (outros testes) |
| Curva combinada `± 0,20 dB` (`20 Hz-20 kHz`) | `EqDspTest.responseAddsPreampAndBandsAndSkipsFlatOnes` (pré-amp mais uma banda, `± 0,2` em 1 kHz) e `processingA1kHzToneWithA6dBPeakDoublesIt` (`± 0,03` em ganho linear) | parcial: nenhum teste varre `20 Hz-20 kHz` com várias bandas ativas; pendente |
| Bypass `0,00 dB` (`< -100 dB` de resíduo) | EQ: `flatSettingsLeaveTheSignalUntouched` e `disabledChainIsABypass` (diferença `<= 1e-6` por amostra, `-120 dBFS`). Spatial: `dryPathIsTransparent` afirma erro `< -100 dB` | provado |
| `0` quedas (dropouts) por hora | nenhum teste JVM cobre | pendente: sem número no Windows |
| Latência ponta a ponta no Windows | não se aplica a meta do S23; não há número | pendente: sem número no Windows |

Pendente e método proposto (sem inventar número):
- **Quedas por hora**: em reprodução contínua de uma hora na build de distribuição, contar os underruns da `SourceDataLine` do `DesktopPlayer` (`available()` igual ao tamanho do buffer = fila vazia, ou o intervalo entre `write` maior que a duração do bloco) e registrá-los no painel ENGINEERING. A meta é contagem `0`. Falta o contador no `DesktopPlayer` e a corrida de uma hora; depende de `win-player`.
- **Latência ponta a ponta**: somar a latência do DSP (acima) ao atraso do dispositivo de saída, lido de `SourceDataLine.getBufferSize()` mais `getLongFramePosition()` contra o relógio, ou medida por loopback (impulso na saída, captura na entrada) em placa com cabo. Sem meta definida pelo dono para o Windows: perguntar ao dono antes de escrever um limite.
- **Curva combinada**: um teste JVM novo, com várias bandas ativas, varrendo `20 Hz-20 kHz` em passos logarítmicos e comparando `EqDsp.responseDb` com a soma analítica dos filtros, tolerância `0,20`.

## Area Áudio (`win-audio`, 2026-11-18)

Desktop contract: `docs/WINDOWS-DESKTOP.md`. Sheets became a 384 dp side pane inside the Áudio screen; the parametric editor is a popover hung from its tile. Status measured 2026-11-18:
- **tested (earlier run)**: `gradle.sh windows -p windows -q test createDistributable` exit 0 (`win-audio-final.log`), before the desktop redo.
- **compiled (desktop redo)**: no `e:` line in `windows/equalizador/**` in `win-audio-d3.log`. The full run was blocked by other areas' files (`geral/SettingsScreen.kt`, `player/PlayerPanel.kt`).
- **done (2026-11-25, tablet UI validation)**: `AudioCaptureTest` (opt-in with `AUDIO_CAPTURE_DIR`, skipped otherwise) hosts `AudioScreen` and writes window-only PNGs by `captureToImage` (no screen grab, no sound) at 1024x640, 1366x768, 1920x1080, 2560x1440 in light and dark (8) plus parametric at 1366x768 (2), in `.claude/sessoes/evidencias-audio/`. It asserts per size and theme: no truncated or off-window text, rack `360` dp wide, rack rows `48` dp high, text / secondary text / accent contrast `>= 4.5` on the background, and zero Ice `#7EE3EA` pixels on light. `gradle.sh win-audio -p windows -q test` (with and without `AUDIO_CAPTURE_DIR`) exit 0. Found and fixed: below 1152 dp the top bar clipped the title and route, so it now takes two rows (`TOP_BAR_ONE_ROW_MIN`). Screen order was checked by eye in the PNGs against `docs/WINDOWS-NOVA-UI.md` (the test does not assert it).
- **done (2026-11-25, pro-audio refinement of the tablet Audio/EQ)**: rack, curve, faders and tiles read as one system. The rack column now ends on the faders' baseline (the two cards share the column height, rows spread evenly under a title pinned to the top; below the height for both the column scrolls). Pre-amp, bass and treble are one control: label, minus, 48 dp bipolar knob, plus, value (tap types it, double tap resets, wheel and arrows step, as before). The curve is labelled at +12, +6, 0, -6, -12 to match its gridlines. Fader values stagger over two rows only when neighbouring bands are closer than 45 dp. Parametric tiles take the rows they need at the window width and the curve keeps the rest. The preset list uses the window height instead of a fixed 576 dp cap. The Advanced page is a centred 768 dp column instead of full-width rows. Audio processing and Tarkas Spatial (UI, 3D, logic, DSP, `AudioRows.kt`, `AudioChain`, `AudioState`) are unchanged; `git diff` on those files is empty. `AudioCaptureTest` now also writes the preset pane and the Advanced page (26 PNGs in `.claude/sessoes/evidencias-audio/`) and asserts 48 dp knobs and value targets, one knob column, no card overlap, equal card heights from 768 dp up, and no cut or off-window text on all three views. `gradle.sh win-audio -p windows -q test --tests "com.example.windows.equalizador.*"`, with and without `AUDIO_CAPTURE_DIR`: exit 0, 17 tests, 0 failures (1 skipped without the capture dir). Not asserted: text drawn on the curve canvas (its labels) is not in the semantics tree, so only the PNGs cover it.
- **pending**: `ui-evidence.js` exits 1 (`MISS light-portrait`, `MISS dark-portrait`): the script requires portrait slots and these four sizes are all wider than tall, so portrait captures are not applicable to a desktop window; no portrait file was invented. Spatial sheet and 3D stage not captured (the harness gives no `spatialActions`, so the dock button renders disabled). Parametric balloon editor not captured. Parametric only at 1366x768.

| Android function | Windows equivalent | Status |
|---|---|---|
| Console: title, on/off, preset row, curve, bands, dock, headroom | `EqPowerConsole.kt`, `EqConsolePage.kt` (side layout from 768 dp wide: dock in a right column) | done at the four sizes in light and dark (captures + assertions above; tablet rack and two-row top bar under 1152 dp) |
| 10-band graphic faders under the curve's log axis | `EqStrip.kt` | tested (EqMathTest); done visually at the four sizes in light and dark (faders sit under their frequencies on the curve's axis) |
| Parametric 96x48 tiles, "+" tile, editor bubble, undo on remove | `EqStrip.kt`: tiles flow in rows (`FlowRow`); editor is a popover centred under its tile, at most 384 dp wide, clamped to the window | tiles and "+" tile seen at 1366x768 light and dark; editor balloon and undo on remove: pending (not captured) |
| Double tap resets band, fader, knob | `EqControls.kt` `detectTapGestures(onDoubleTap)` (double-click) | compiled; pending |
| Mouse wheel and arrow keys step faders and knobs | `DesktopStep.kt` `desktopStep`, used by `EqFader` and `EqKnob` | tested (`DesktopStepTest`: step, ends, drift, stop size); compiled |
| Presets list, save, overwrite, rename, delete | `AudioState.kt`, `PresetPanel.kt` in the side pane | tested (`AudioStateTest`) |
| Headphone correction (AutoEq) and search | `PresetPanel.kt` CORRECTION tab in the side pane; `AutoEqRepository` | compiled; online fetch pending |
| Ajustes sheet (preamp, bass, treble, limiter, spectrum) | `EqSettingsSheet.kt` in the side pane (no ModalBottomSheet); on a wide window the same controls sit in the 360 dp rack (`TabletRack.kt`, cards Tom e ganho / Saida e protecao) | rack: done (captured, widths and 48 dp rows asserted); the sheet in the side pane and the spectrum row: pending |
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
| Full player (always dark, two halves, Fila/Letras on the right when wide) | `player/PlayerExpanded.kt` | **feito**: `PlayerValidationTest` at 1024x640, 1366x768, 1920x1080, 2560x1440 in light and dark, nothing overlaps or leaves the area; live app look pending |
| Pill mini player in the dock (384x96 box, hover shows prev/next, click opens) | `player/MiniPlayer.kt` | **feito** in the same test (pill 384x96, 48 dp targets, no overlap); the shell's glass and border are win-geral's, not covered by this test |
| Open / close time | `ExpandMath` 250 / 200 ms | **feito**: 288 ms / 240 ms measured on the test clock (16 ms frames, includes the start-up frames), targets 300 / 260 |
| Resize with the panel open | `BoxWithConstraints` + `ExpandMath.coverDp` | **feito** (constraints 2560x1440 to 1024x640 and back, cover 384/192, playback untouched); a real OS window drag **pending** |
| Frame rate while the panel opens | `graphicsLayer` only | **pending**: a ui-test has no real frame pacing; the per-frame progress is monotonic with no jump over 25 %; fps needs the running app |
| Full-width waveform seek, wheel +-5 s | `WaveformSeek`, `SeekSection`, `WaveformReader`, `SeekMath.WheelStepMs` | **feito** (`PlayerHooksTest`, `MiniPlayerTest`, `PlaybackAuditTest`: tap, drag with label, one seek on release); real device and hand drag in the live app pending |
| Synced lyrics (.lrc, ID3) | `LyricsLoader`, `LyricsView` | **feito** (`PlayerHooksTest`); look in the app pending |
| Queue pane: drag, Delete+undo, right-click | `QueuePane`, `PlayerHooks.undoQueue` | undo **feito** (tests); drag and right-click by hand **pending** |
| Sleep timer popup, crossfade menu | `SleepTimerButton`, `CrossfadeButton` | compiled; controller logic tested; popups by hand **pending** |
| Hooks for transport bar and shortcuts | `PlayerHooks` | **feito** (`PlayerHooksTest` 9 cases) |
| Media keys | `MediaKeys` | **pending** on a real keyboard |
| AAC/M4A/OGG | not done | **not done** (no decoder in Java Sound; Media Foundation needs a native bridge) |
| SMTC (system media overlay) | not done | **not done** (needs WinRT and a packaged app identity) |
| Cover carousel with side covers, mini swipe | not done | **not done** (mobile-only gesture code) |

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
| Light/dark, 4 sizes, Break UI | **feito** para o desenho novo, ver "Biblioteca e Início NOVA UI" abaixo; Break UI de entrada (nomes enormes, arrasto, 100k faixas) **pendente** |

Command: `gradle.sh win-biblioteca -p windows -q test --tests 'com.example.windows.biblioteca.*'` exit 0; 26 tests (CatalogParse 3, LibraryScanner 5, LibrarySort 4, LibraryStore 2, TableLogic 12), 0 failures. Full `test createDistributable`: not run by this session.

### Home per `WINDOWS-VISUAL-REF.md` (win-biblioteca, 2026-11-18)

| Item | Status |
|---|---|
| Speed dial: 2-column rows, 48 dp cover r6, hover tile r12, favourites + playlists + most played, click plays, right-click menu | built; data logic tested (`HomeDataTest`); on screen **pending** |
| Daily Mixes: 192 dp covers r12, wheel and Left/Right keys, shared `AutoLists` + top genres | built; scrolling by wheel and keys **pending** (not exercised) |
| Listening right now: current + player history | built; **pending** on screen |
| Empty sections show an honest empty state; `glass()` surfaces; Grid tokens only | built; contrast over a real backdrop **pending** |

`gradle.sh win-biblioteca -p windows -q test --tests 'com.example.windows.biblioteca.*'` exit 0. The Online trending lists left the dashboard; Audius/Jamendo remain in search results.

## Início por PEDIDO-2 (win-biblioteca, 2026-11-18)

| Item | Estado |
|---|---|
| Atalhos 4x2 (favoritos, playlists, mais tocadas) | Código + `HomeDataTest`; tela **pendente** |
| Prateleiras Feito para você, Novos lançamentos, Álbuns, Ouvindo agora, "Mostrar tudo" | Código; tela **pendente** |
| Audius/Jamendo/MusicBrainz com dados reais, busca e erros (offline, sem chave) | Clientes em `Catalog.kt`; estados tratados no Início; rede real **pendente**. Chave Jamendo só por `JAMENDO_CLIENT_ID` ou ajuste; nada no instalador |
| Painel direito: faixa atual + sobre o artista (MusicBrainz) | Faixas online buscam sozinhas; arquivo local só após clique (envia título/artista) |
| Rolagem: roda, setas, arrasto com inércia `CoverCarousel.project`, 300 ms desacelerando | Código; sensação **pendente**. `PlayerTravel`/`PlayerExpand` ficam com win-player |

### Biblioteca e Início NOVA UI: validação por captura (win-biblioteca, 2026-11-25)

Capturas só da janela (`runDesktopComposeUiTest(w, h)` + `captureToImage`, sem tela cheia, sem som), em `.claude/sessoes/win-biblioteca-evidencias/` (24 PNG: `<inicio|biblioteca|detalhe>-<L>x<A>-<light|dark>.png`; 1024x640, 1366x768, 1920x1080, 2560x1440). Teste: `BibliotecaEvidenceTest` (roda a captura só com `TAL_EVIDENCE` definida).

| Item | Estado |
|---|---|
| Início 58/42 só quando a coluna livre (largura menos o painel "Tocando agora" de 288 dp) é >= 840 dp | **feito**: 1366, 1920 e 2560 mostram 58/42; 1024 cai no layout antigo (coluna única), conferido na captura |
| Sidebar 210 dp, itens h42, selecionado primary 0.16 | **feito**: visto nas 8 capturas de Biblioteca |
| Master-detail 42/58 com Backspace fechando o detalhe | **feito**: layout visto em `detalhe-*`; `backspaceClosesDetail` passa (Backspace e Alt+Esquerda) |
| Sem texto cortado | **feito com ressalva**: títulos de faixa e nomes de prateleira usam reticências em uma linha (por projeto); botões, cabeçalho de playlist e dica da busca cabem em 1024 e 1366 (mosaico 96 dp abaixo de 420 dp, dica curta no detalhe; Álbum e Execuções somem da tabela abaixo de 560 dp) |
| Grade T.A.L. (tokens `Grid`/`GridText`, `studioCard`) | **feito** no código de `biblioteca/*`; medida em pixels por `ui-evidence.js` **não aplicável** (o script exige retrato e paisagem; as 8 capturas são paisagem, exit 1 por retrato ausente) |
| Contraste >= 4.5:1 (tema claro e escuro) | **feito** para os pares do tema (texto/cartão 16.0 e 15.8; secundário 4.96 e 5.28; rótulo selecionado 4.59 e 8.10); véu sobre capa real **pendente** (é do `Backdrop` do shell, win-geral) |
| Chave duplicada em LazyColumn/LazyGrid | **feito**: Início usa `distinctBy` nas listas; Biblioteca já tinha chaves únicas (`"$i:${t.id}"`, caminho, prefixos); nenhuma queda nas 24 capturas |
| Saudação e atalhos 3 colunas no Início largo | **pendente**: o layout largo (58/42) não os tem; só existem no layout de coluna única |
| Rede real (Audius/Jamendo), sensação de roda e arrasto, `lt` em outros idiomas, ícones do shell | **pendente** (não exercitável por captura) |

Comandos: `TAL_EVIDENCE=... bash .claude/sessoes/gradle.sh win-biblioteca -p windows -q test --tests 'com.example.windows.biblioteca.*' -i` exit 0; 9 classes, 39 testes, 0 falhas (BibliotecaEvidenceTest 2, TableLogicTest 13, HomeDataTest 6, LibraryScanner 5, LibrarySort 4, CatalogParse 3, PlaylistAndGreeting 3, LibraryStore 2, ShelfMomentum 1).

#### Auditoria do vídeo 2.1.3 (win-biblioteca, 2026-11-25)

| Item | Status |
|---|---|
| Carrossel de Início andava na vertical com o touchpad | **feito**. Causa: o delta vertical era convertido em horizontal e o evento nunca era consumido (movimento diagonal e a página também rolava). `Modifier.sidewaysScroll` agora decide pelo eixo dominante: mais lateral (`abs(dx) > abs(dy)`) ou vertical com Shift move só a fileira e consome o evento; o resto fica para a rolagem da página. Provado por `SidewaysScrollTest` (roda horizontal move a fileira e não a página; roda vertical move a página e não a fileira) |
| Início largo sem blocos vazios grandes | **feito**: Suas músicas lista tudo com contagem; à direita, cartões na altura natural (Continuar ouvindo, Tocadas recentemente se houver, Álbuns recomendados se houver, Catálogos gratuitos), cada um com estado vazio próprio; Álbuns recomendados e Catálogos gratuitos não repetem capas. Capturas `inicio-*` e `inicio-vazio-*` (4 tamanhos, claro/escuro); revisão visual só em 1366 e 1920, os outros tamanhos passam no teste de contraste mas **sem revisão visual** |
| Biblioteca vazia | **feito**: estado centrado com ícone, texto sem "celular", botão "Adicionar pasta de músicas…"; com pastas configuradas e nada encontrado mostra também "Verificar de novo"; arrastar pastas/arquivos já existia e é citado no texto. Seções Álbuns/Artistas/Gêneros sem itens perdem a seta e o clique. Capturas `biblioteca-vazia-*` (revisão visual em 1366 escuro) |
| Feedback ao importar | **pendente**: o aviso ao soltar arquivos existe (`showNotice`); o botão de pasta só mostra "carregando" durante a verificação, sem contagem por pasta |
| Redesenho dos cartões e filtros da Biblioteca | **pendente**: sem mudança nesta rodada |

Comando: `TAL_EVIDENCE=C:/dev/tarkas/.claude/sessoes/win-biblioteca-evidencias bash .claude/sessoes/gradle.sh win-biblioteca -p windows -q test --tests 'com.example.windows.biblioteca.*'` exit 0; 10 classes, 42 testes, 0 falhas, 0 erros (+2 `SidewaysScrollTest`, +1 `emptyLibrary`). Sem commit.

### Home v2 (win-biblioteca, 2026-11-25)

Capturas só da janela em `.claude/sessoes/win-biblioteca-evidencias/` (48 PNG: `inicio`, `inicio-busca`, `inicio-vazio`, `biblioteca`, `biblioteca-vazia`, `detalhe` x 4 tamanhos x claro/escuro). A fila usada nas capturas é um painel de teste (`QueueStandIn`), não a pilha real do win-player.

| Item | Status |
|---|---|
| Campo de busca grande removido; Início lê `ShellSearch.query` (debounce 350 ms) e mostra Biblioteca, Audius e Jamendo com filtros e ações | feito no código; resultados vistos em `inicio-busca-1366x768-dark.png`; digitar no dock real **pendente** (dock é do win-geral) |
| Bloco "Suas músicas" removido; Início em carrosséis (Em destaque, Continuar ouvindo, Feito para você, Seus álbuns, Ouvir de novo, Álbuns recomendados, Catálogos gratuitos) com `sidewaysScroll`, "Mostrar tudo" abre a grade | feito, visto nos 4 tamanhos x 2 temas; Álbuns recomendados e Catálogos dependem da rede (visto com rede nesta máquina; sem rede **pendente**) |
| Caixa inferior direita com `Panels.queueStack`, sem cobrir conteúdo; 216 dp a partir de 5 x 192 + 96 dp de página, 288 dp a partir de 9 x 192 dp, oculta abaixo (1024x640) | feito (`queueColumnIsHiddenWhenNarrowAndGrowsWithTheWindow`, capturas); com a pilha real do win-player no app **pendente** |
| Faixa superior de 72 dp e player lateral fixo | do shell (win-geral); o Início não adiciona faixa própria; no app real **pendente** |
| Importação de pasta e de arquivos na Biblioteca (botões "Adicionar pasta de músicas" e "Adicionar arquivos de áudio" na barra, também no estado vazio, mais arrastar e soltar), nada perdido do Início | feito no código; seletor (`JFileChooser`) não exercitado em teste, **pendente** |
| Início vazio com aviso e botão "Abrir Biblioteca" | feito, visto em `inicio-vazio-*` |

Comando: `TAL_EVIDENCE=C:/dev/tarkas/.claude/sessoes/win-biblioteca-evidencias bash .claude/sessoes/gradle.sh win-biblioteca -p windows -q test --tests 'com.example.windows.biblioteca.*'` exit 0; 10 classes, 44 testes, 0 falhas, 0 erros (BibliotecaEvidenceTest 3, HomeDataTest 8). Contraste >= 4.5 em todas as capturas. Janela real e `createDistributable` não rodados por esta sessão.

### Home retoque: movimento (win-biblioteca, 2026-11-26)

Todo movimento e so de GPU (`graphicsLayer`, nunca tamanho nem padding); com `reduceMotion` ligado vira corte seco.

| Item | Status |
|---|---|
| Cartao: hover 1,04 com elevacao tonal da capa, press 0,96, mola criticamente amortecida (rigidez 439); anel de foco so por teclado | feito no codigo; regras puras testadas (`cardScale`); sensacao com ponteiro real **pendente** (teste nao move o mouse) |
| Capa entra com fade de 200 ms (so quando chega depois do cartao) | feito no codigo (`TrackArtwork`); visto apenas o estado final nas capturas |
| Skeletons no lugar de Carregando, so apos 300 ms, mesma geometria do cartao, pulso 0,6 a 1,0 em 900 ms | feito: `skeletonShows` testado e `skeletonCardIsAsBigAsACard` mede largura e altura iguais ao cartao; pulso visto em janela real **pendente** |
| Setas e arrasto do carrossel pela mesma mola, mantendo a velocidade se outra tecla chega no meio | feito no codigo (`SpringScroller`, `springVelocity` testado); movimento em janela real **pendente** |
| Pagina em LazyColumn (um item por secao) | feito, capturas dos 4 tamanhos x 2 temas sem mudanca de layout |
| Densidade: destaque + ate 4 carrosseis antes da prateleira; Ouvir de novo some quando repete Continuar ouvindo; AboutCard abaixo da prateleira | feito (`railsToShow` testado) |
| Destaque com crossfade de 300 ms e botao Tocar com o mesmo hover | feito no codigo; troca de destaque em janela real **pendente** |

Comando: `TAL_EVIDENCE=C:/dev/tarkas/.claude/sessoes/win-biblioteca-evidencias bash .claude/sessoes/gradle.sh win-biblioteca -p windows -q test --tests 'com.example.windows.biblioteca.*'` exit 0; 11 classes, 53 testes, 0 falhas, 0 erros (HomeMotionTest 9, HomeDataTest 8, BibliotecaEvidenceTest 3). Quadros por segundo e peso do desfoque nao foram medidos por esta sessao.

### Assistente NOVA UI (win-assistente, 2026-11-24)

| Item | Windows implementation | Status |
|---|---|---|
| Top bar 1 line min 40, expandable status pill, Clear with Sim/Nao | `AssistantScreen.kt` `TopBar` | feito: pill height >= 40 dp measured at 4 sizes x 2 themes (`layoutMatchesTheSpecAtTheFourSizes`), panel opens (`statusPillOpensTheControlsPanel`); Clear with Sim/Nao click flow: pendente (not exercised by a test) |
| Chat centred, max 760 dp | `ChatMaxWidth` | feito: measured 1024 <= 760, exactly 760 from 1366 up; the text column inside is 712 (760 less the 24 dp margins) |
| Empty state with 4 cards (3 columns, 2 when compact) | `EmptyState` | feito: 4 cards in 2 rows at the 4 sizes, 2 columns at 480 dp (`compactWidthUsesTheSmallButtonsAndTwoColumns`) |
| Capsule `SquircleShape(26)`, buttons 40 (36 compact): +, field, mic, send; Enter sends | `Capsule`, `CapsuleIcon` | feito: three buttons 40x40 (36 at 480 dp) and capsule inside the 760 column measured; Enter/Shift+Enter tested; the radius 26 is read from code, not measured: pendente |
| History pane | kept only at WidthClass.EXPANDED | tested; previews cut on purpose with an ellipsis after 2 lines |

### Assistente validation (win-assistente, 2026-11-25)

Evidence: `AssistantCaptureTest` (window only, `captureToImage`, no sound, no microphone), 16 PNGs in `.claude/sessoes/assistente-capturas/` (1024x640, 1366x768, 1920x1080, 2560x1440 x light/dark x empty/chat), each read by eye. Run with `ASSISTANT_CAPTURE_DIR=<folder> bash .claude/sessoes/gradle.sh win-assistente -p windows -q test --tests 'com.example.windows.assistente.*'`: exit 0, 52 tests, 0 failures.

| Item | Status |
|---|---|
| Layout at 4 sizes x 2 themes: pill >= 40, chat <= 760, capsule in the column, buttons 40, 4 cards, nothing outside the window | feito (measured) |
| No cut text at 4 sizes x 2 themes, empty and conversation (the detector is proven by `theCutTextDetectorSeesAnEllipsis`; `hasVisualOverflow` is true for every text on this build, so it is not used) | feito |
| Text contrast >= 4.5:1 in both themes: onSurface, onSurfaceVariant, onPrimary, primary, error | feito |
| Scroll to the last message on every send, also at the 200-message history cap (Android F23): the old effect keyed on the count, which stops changing at the cap; now keyed on the last id | feito, fixed and tested (`everySendScrollsToTheLastMessageEvenAtTheHistoryLimit`) |
| Named error states: offline, key refused, quota, cut off, no reply, server. The failed reply was stored empty and showed a blank bubble; the text now comes from `AssistantFailure` at display time, in the app language | feito, tested for all 6 (`failedRepliesNameTheCauseInsteadOfNotUnderstood`) |
| Light theme: assistant bubbles, tool card, chips and status pill were invisible (container 1.03:1 against the background); now carry the 0.08 hairline of `studioCard` | feito, seen in the captures |
| Grade T.A.L.: tokens `Grid`/`GridText` only, button sizes measured; spacing between elements read from the captures, not measured with a ruler | parcial: pendente (no pixel ruler test) |
| Light theme page and container colours are nearly the same grey (E9E9E7 and E6E6E4) | pendente: `ui/theme` tokens belong to win-geral |
| Voice/microphone: not tested. This machine has 0 speech recognizers, no microphone was used and no sound was played | pendente |
| Speech output (SAPI) audible, real Gemini call, Clear with Sim/Nao click | pendente |

### Home v2 (win-player, 2026-11-25)
| Feature | Where | State |
|---|---|---|
| Side player (cover, title, artist, progress, prev/play/next, cover opens the full player) | `player/SidePlayer.kt`, `Panels.sidePlayer` | built and tested (`SidePlayerTest`); look in the running app pending |
| Queue card stack from the real queue (throw, click a card behind, fan, tilt, reduced motion) | `player/QueueCardStack.kt`, `player/QueueStackEngine.kt`, `Panels.queueStack` | built and tested (`QueueCardStackTest`); placement in the Home is win-biblioteca's; look and frame rate pending |
