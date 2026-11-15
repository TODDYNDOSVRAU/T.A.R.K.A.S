# Equalizer screen: Compose implementation spec

Owner approved the mock `C:\dev\tarkas\.claude\sessoes\eq-proposta\exemplo-equalizador.html` (CSS lines 8–182, HTML 184–276, script from 280; Spatial stage JS about lines 656–780). The mock is the visual and behavioural reference. Implement it in the Android app (Jetpack Compose), package `com.example.ui.screens.audio`.

## Hard rules (shared checkout, several sessions)

- Edit only: `app/src/main/java/com/example/ui/screens/audio/*`, `app/src/main/java/com/example/ui/components/EqControls.kt`, `app/src/main/res/values/strings_audio.xml`, `app/src/main/res/values-pt/strings_audio.xml`, and tests of these under `app/src/test`. Anything else (theme, ViewModel, `ui/screens/AudioScreen.kt`, `MainActivity.kt`) is another session's file: do not edit; report what you would need changed.
- Never run git checkout/restore/stash/reset/clean/add/commit. Never run gradle/gradlew/adb. The parent session builds. Do not delete build folders.
- `strings_audio.xml` (both languages) is edited by two agents at once: use Edit only (never Write), re-read just before editing, and add new strings at the END of the file, immediately before `</resources>`. Every new string exists in EN (`values`) and PT (`values-pt`). No exclamation marks, no emojis.
- Keep every existing function working and every existing `testTag`. Check `app/src/test` for tests that reference the composables or tags you touch and keep them valid.
- Do not invent capability. If the mock shows something with no backend in the app (check the ViewModel / data layer by reading, not editing), leave it out and list it as pending in your report.

## The T.A.L. grid (must hold in the result)

- Size chain only: 6, 12, 24, 48, 96, 192, 384 dp. 48 = touch target and height of every row, button, pill.
- One side margin 24 dp. Inner inset 12 dp. 6 dp only inside segmented controls. Vertical gaps 12 or 24.
- Sibling widths 1:1 or 2:1. Text sizes 12, 18, 24 sp only. Radii 6, 12, 24 (pill = half height).
- Icons in a 24 dp box inside a 48 dp target.
- One number format: decimal only when the value is not whole (reuse/adjust `formatDb`, `formatHz` in `EqMath.kt`), tabular figures, mono face for metrics.
- Outlines drawn inside the box (`drawBehind`/`border` that does not change size). A state change never moves a neighbour.
- Colours come from `MaterialTheme.colorScheme` / existing theme tokens (the app has a Material You option and a light theme): never hard-code hex in a screen. Accent (`colors.primary`) only for active state.
- The existing theme `Space`/`Size` tokens may not match the chain. Put the grid constants in a new file `audio/EqGrid.kt` (`internal object Grid { val u6 = 6.dp … val margin = 24.dp }`) and use them; do not edit the theme. If `EqGrid.kt` already exists (the other agent made it), reuse it.
- Motion stays: press scale 0.96 in 150 ms, view switch out 150 ms / in 260 ms with a 16–18 ms stagger, springs without bounce (use the existing `Motion` helpers where they fit). There is no reduced-motion rule any more, but existing `rememberReducedMotion()` call sites can stay.
- Accessibility: every control has a label and state description; every drag has a tap or stepper alternative; double tap on a band/fader/knob resets it (must be kept); TalkBack custom actions where a gesture is the only path.

## Portrait layout, top to bottom (360 dp wide reference; content column 312)

1. Top row, 48 high: title "Equalizer" (24 sp, display face) with the route line under it (mono 12 sp, a 6 dp accent dot + route name + sample rate, existing `audio_output_line` data); at the right the power pill 96x48 holding a 48x24 switch (existing `audio_dsp_switch` tag). Holding the pill/compare control plays without EQ (existing compare behaviour in `CurveStatusRow`: hold, tap-to-latch, TalkBack click) and shows a banner line "Sem EQ · volume igualado" (existing strings where possible).
2. Preset row, 48 high, gaps 12: preset pill 144 wide (preset name, modified mark, existing `eq_preset_button`), "Correção" pill 96 wide (opens the headphone correction part of the existing preset panel if it can be opened that way through existing callbacks; otherwise it opens the preset panel), overflow icon button 48 (existing `eq_more` menu, all items kept).
3. Curve card, 192 high, radius 24: response curve 168 + frequency axis 24 with labels 20 / 200 / 2k / 20k (mono 12). Existing `EqGraph` (spectrum, draggable band points) goes here, tag `audio_spectrum_graph_card`.
4. Bands: Graphic view = faders, each exactly under its frequency on the same log x as the curve where the band count allows (thumb 12x48, value above in mono 12, Hz below, dashed zero line); if the bands cannot fit the 312 column at 48 dp targets, keep horizontal scroll and say so in the report. Parametric view = tile grid, 3 columns, tiles 48 high, gaps 24 between rows and 12 between columns, a dashed "add" tile; tapping a tile opens the band editor (type segmented Peak/Low shelf/High shelf…, steppers for Hz / dB / Q, number entry kept, remove with undo).
5. Dock, 48 high: segmented Gráfico/Paramétrico 192 wide (inner inset 6; tags `eq_mode_toggle`, `eq_mode_graphic`, `eq_mode_parametric`) + "Tarkas Spatial" button filling the rest (title + a 12 sp sub line with the current mode and intensity), opens the Spatial sheet (tag `eq_open_spatial` stays reachable).
6. Path bar, 48 high: headroom text ("Folga −3 dB", existing headroom/limiter/clip notes), then right-aligned 48 dp buttons only for actions that exist.
7. Bass/treble knobs, limiter switch and everything else from the old control row that has no place above move into the settings sheet reached from the overflow menu ("Ajustes"), as 48 dp rows; nothing is removed.

Landscape (existing `SIDE_MIN_WIDTH` branch) must keep working; adapting it to the grid is welcome but secondary.

Bottom inset: the page already receives `LocalBarInset` for the floating mini player and tab bar; keep it.

## Sheets

Sheet content: side margin 24, rows 48, radius 24 on top, title 18 or 24 sp, caption mono 12. Spatial sheet order: title row + caption, the 3D stage (192 high, full column width), mode segmented control, the on/off switch row, intensity slider, then a "more modes and fine tuning" row that expands the existing detailed controls (`SpatialTuning`, head tracking). All existing Spatial functions stay.
