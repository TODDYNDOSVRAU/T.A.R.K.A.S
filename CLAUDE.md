# T.A.R.K.A.S.

Segue `C:\dev\CLAUDE.md` (identidade T.A.L., leis de Nível 0, skill `tal` e as 6 Super-Skills `tal-super-*`).

## Mapa de Arquivos por Área (`app/src/main/java/com/example/` e `app/src/test/java/com/example/`)

O código-fonte (213 arquivos) e os testes unitários (108 arquivos) estão divididos nas 5 áreas funcionais do produto, separando dentro de cada área a pasta **`ui/`** (telas e componentes visuais) e a pasta **`funcoes/`** (motor, DSP, dados e lógica):

| Área / Sessão | `ui/` (Telas, Componentes e Animações) | `funcoes/` (Motor, Repositórios, DSP e Modelos) |
|---|---|---|
| **`player/`** | `MusicScreen.kt`, `PlayerCarousel.kt`, `PlayerControls.kt`, `PlayerBackdrop.kt`, `PlayerModels.kt`, `PlaybackProblemBanner.kt`, `QueueList.kt`, `LyricsSheet.kt`, `SleepTimerDialog.kt`, `TrackActionsMenu.kt`, `TrackDetailsSheet.kt`, `TrackShare.kt`, `WaveformBar.kt`, `CoverCarousel.kt`, `PlayerExpand.kt`, `PlayerTravel.kt` | `PlaybackController.kt`, `PlaybackService.kt`, `SessionPlayer.kt`, `SessionQueue.kt`, `PlaybackStateStore.kt`, `PlayStats.kt`, `CoverNeighbors.kt`, `CrossfadePlanner.kt`, `AudioFocusPolicy.kt`, `lyrics/*`, `waveform/*`, `widget/*` |
| **`biblioteca/`** | `LibraryScreen.kt`, `HomeScreen.kt`, `SongsSection.kt`, `PlaylistsSection.kt`, `GroupsSection.kt`, `AutoListsSection.kt`, `FolderPicker.kt`, `LibraryModels.kt`, `LibraryParts.kt`, `LibraryStates.kt`, `LibraryCompute.kt`, `TrackArtwork.kt` | `MusicRepository.kt`, `LibraryRepository.kt`, `PlaybackOrder.kt`, `AlbumOrder.kt`, `AutoLists.kt`, `FolderFilter.kt`, `LibraryGroups.kt`, `LibraryMigration.kt`, `MediaDiff.kt`, `LibrarySearch.kt`, `LibrarySort.kt`, `LibraryTree.kt`, `PickedIdRewrite.kt`, `catalogo/*` (`Audius`, `Jamendo`, `MusicBrainz`), `model/*` |
| **`equalizador/`** | `AudioScreen.kt`, `EqPowerConsole.kt`, `EqConsolePage.kt`, `EqAdvancedPage.kt`, `EqGraph.kt`, `EqStrip.kt`, `EqControls.kt`, `EqSettingsSheet.kt`, `EqMath.kt`, `EqText.kt`, `BandColors.kt`, `AudioRows.kt`, `NumberEntry.kt`, `PresetNames.kt`, `PresetPanel.kt`, `SpatialPanel.kt`, `SpatialStage.kt`, `SpatialStageModel.kt`, `EqSize.kt` | `AudioEngineManager.kt`, `EqChainState.kt`, `OutputDetection.kt`, `OutputProfiles.kt`, `OutputRouting.kt`, `dsp/*` (`EqDsp`, `Biquad`, `ParametricEqAudioProcessor`), `spatial/*` (`AcousticPhysics`, `SpatialEngine`, `SphericalHeadHrtf`, `MeasuredHrtf`, `Upmixer`), `loudness/*`, `autoeq/*`, `systemaudio/*`, `model/*` |
| **`assistente/`** | `AssistantScreen.kt`, `MusicalCuriositySettingsScreen.kt` | `AssistantEngine.kt`, `AssistantCommands.kt`, `AssistantActions.kt`, `AssistantGemini.kt`, `AssistantHistory.kt`, `AssistantRequests.kt`, `AssistantSpeaker.kt`, `CuriositySpeechManager.kt`, `CuriosityRepository.kt`, `model/*` |
| **`geral/`** | `MainActivity.kt`, `SettingsScreen.kt`, `SettingsLayers.kt`, `OnboardingScreen.kt`, `BootScreen.kt`, `UpdateSettings.kt`, `UpdatePrompts.kt`, `LanguageSetting.kt`, `LegalScreen.kt`, `CommonComponents.kt`, `Design.kt`, `KeptPages.kt`, `NavLayout.kt`, `layout/*`, `legal/*`, `theme/*` (`Tokens`, `GridLayout`, `Color`, `Type`, `Theme`) | `AppGraph.kt`, `viewmodel/*` (`TarkasViewModel`, `SettingsViewModel`, `UpdateViewModel`, `NavRoutes`), `db/*` (Room `AppDatabase`, `Daos`, `Entities`), `backup/*`, `diagnostics/*`, `update/*`, `PreferencesRepository.kt`, `PrefsVersion.kt`, `Http.kt`, `TextMatch.kt` |

## Várias sessões no mesmo checkout

O trabalho está dividido em sessões por tópico: **Player**, **Biblioteca**, **Equalizador**, **Assistente** (mais **Geral**). Elas editam este mesmo checkout (`references/sessions.md`). Antes de editar ou compilar, leia `.claude/sessoes/QUADRO.md`: ele diz de quem é cada arquivo, como reservar arquivos compartilhados (`CLAIM` / `DONE`) e como compilar (`bash .claude/sessoes/gradle.sh <sessao> <tasks>`, com trava `build.lock`, código 75). Nunca use `git checkout/restore/stash/reset/clean` nem commite sem o usuário pedir; para prever conflito entre branches sem tocar na árvore, use `git merge-tree --write-tree`.

## Metas de Precisão Oficiais (definidas pelo dono em 2026-11-16, 48 kHz)

| Métrica | Meta Oficial | Verificação Obrigatória |
|---|---|---|
| **Latência interna DSP** | `≤ 5 ms` (EQ lookahead) · `≤ 24 ms` (Spatial: `608 + 512` frames = `23,33 ms`; tempo por frame `< 10,67 ms`) | Testes JVM (`EqDspTest`, `SpatialEngineTest`) |
| **Latência ponta a ponta (S23 cabo)** | `≤ 40 ms` (EQ) · `≤ 60 ms` (Spatial) | Medida no Samsung S23 (`perf` build, saída cabeada) |
| **Interrupções / quedas (dropouts)** | `0` por hora em reprodução contínua | Medida no Samsung S23 (`perf` build, painel ENGINEERING) |
| **Desvio da resposta em frequência** | `± 0,20 dB` na curva combinada (`20 Hz–20 kHz`) · `± 0,05 dB` no centro do filtro isolado · `0,00 dB` em bypass/plano (`< −100 dB` residual float) | Testes JVM (`EqDspTest`, `SpatialEngineTest`) |

## Interface: a grade T.A.L. vale em toda tela (`tal-super-ui` / `references/ui.md` §6)

Toda tela segue a grade T.A.L. (`2x` em ambas as dimensões: `6, 12, 24, 48, 96, 192, 384 dp`; margem lateral `24 dp`; recuo interno `12 dp`; irmãos `1:1` ou `2:1`; texto `12, 18, 24 sp`). No código, use os tokens `Grid` e `GridText` de `com.example.ui.theme` e as cores de `MaterialTheme.colorScheme`; nenhum `dp`, `sp` ou hex solto numa tela. O app tem a opção de seguir as cores do celular (Material You, desligada por padrão), e cor fixa não acompanha essa escolha. Antes de mostrar uma tela, aplique o teste de estresse *Break UI* e meça em tema claro e escuro, retrato e paisagem (validando os 4 prints com `node C:\dev\TAL\plugin\skills\tal\scripts\ui-evidence.js <pasta>`).

## Equalizador: decisões do dono (2026-11-15)

O estilo aprovado é o do exemplo `.claude/sessoes/eq-proposta/exemplo-equalizador.html`; a especificação para Compose está em `.claude/sessoes/eq-proposta/SPEC-compose.md`. Não mude estas decisões sem o dono pedir:

- **Ordem da tela:** título com a rota e o liga/desliga 96x48; predefinição 144 + Correção 96 + menu 48; curva 192; bandas; dock com Gráfico/Paramétrico 192 e Tarkas Spatial; barra de folga.
- **Duplo toque redefine** uma banda, um fader ou um botão giratório, sempre.
- **Faders** ficam embaixo da própria frequência, no mesmo eixo logarítmico da curva.
- **Cor da banda:** neutra em 0 e mais forte quanto maior o ajuste, igual para corte e reforço.
- **Paramétrico:** as bandas são peças 96x48 em grade, com uma peça "+" para adicionar. Tocar numa peça abre o editor como balão sobre a grade (0,75x dela, peças atrás levemente desfocadas); o lixeiro fica no balão e remover tem desfazer.
- **Predefinições:** o botão abre a lista com todas; ao lado dele fica o botão de correção dos fones.
- **Tarkas Spatial:** ocupa no dock o lugar do antigo "segurar: sem EQ". A folha tem o palco 3D com a cabeça de costas: desligado mostra o fone; Sutil e Padrão, o fone vira falantes que se afastam um pouco; Amplo, dois falantes à distância das caixas; Cinema 5.1, eles viram as seis caixas.
- **O que saiu da tela principal** (pré-amplificação, graves, agudos, limitador, espectro) mora na folha Ajustes; nenhuma função foi removida.
