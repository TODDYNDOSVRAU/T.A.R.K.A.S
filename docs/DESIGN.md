# T.A.R.K.A.S. design standard

The bar is Apple's own apps (Music, Settings, Podcasts): quiet surfaces, one clear hierarchy per screen, type doing the work, colour used only to mean something. The identity stays: near-black ink, ice cyan accent, the mark.

## Principles

1. **Content first.** Artwork and titles carry the screen. Chrome recedes.
2. **One accent, used for meaning.** Ice cyan marks what is active, selected or tappable text. Never decoration, never glows, never gradients on controls.
3. **Glass only where something floats.** The tab bar, the mini player and transient banners may be glass (the user's surface style). Everything that sits on the page is a flat fill (`surfaceContainer`), drawn with no sheen, rim or border.
4. **Hierarchy through type, not boxes.** Do not wrap sections in cards to separate them; use spacing and a section title. Grouped rounded surfaces are for lists of settings and options only.
5. **Every control explains itself.** Visible label or a content description, 48 dp touch target, real role and state semantics (switch, radio, tab, slider).
6. **Motion is precise.** Critically damped springs for anything that follows the finger or presses; no bounce on presses, alpha or colour. Short (150–250 ms) for state changes, spatial springs for surfaces. Everything honours reduced motion.

## Tokens (code: `ui/theme/Type.kt`, `ui/theme/Tokens.kt`)

| Role | Style | Use |
|---|---|---|
| `AppText.LargeTitle` | 34/41 Bold | Page titles |
| `AppText.Title1` | 28/34 Bold | Player title, big numbers |
| `AppText.Title2` | 22/28 Bold | Section titles |
| `AppText.Title3` | 20/25 SemiBold | Sub-page and dialog titles |
| `AppText.Headline` | 17/22 SemiBold | Emphasised row titles, buttons |
| `AppText.Body` | 17/22 | Row titles, running text |
| `AppText.Callout` | 16/21 | Secondary running text |
| `AppText.Subhead` | 15/20 | Row subtitles (artist, album) |
| `AppText.Footnote` | 13/18 | Group headers and notes |
| `AppText.Caption1` | 12/16 | Tab labels, metadata |
| `AppText.Caption2` | 11/13 | Axis labels only; never smaller |

No `fontSize = …sp` literals in screens: use a role (`style = AppText.Body`), adjust weight with `.copy(fontWeight = …)` only when needed.

Spacing: `Space.xs 4 · s 8 · m 12 · l 16 · page 20 (side margin) · xl 24 · xxl 32 · section 36`.
Radii: `Radius.xs 6 · s 10 · m 14 · l 20 · xl 28`; shapes in `Shapes`.
Colours: `background` page, `surface` grouped cells, `surfaceContainer` control fills, `surfaceContainerHigh` pressed or nested fills, `onSurface` primary text, `onSurfaceVariant` secondary text, `outline` hairlines, `primary` accent, `error` destructive.

## Components (code: `ui/components/Design.kt`)

- `SectionHeader(title, actionLabel?)`: Title2 section title with an optional "Ver tudo".
- `GroupHeader`, `GroupFooter`: Footnote text above and below a group.
- `SubPageHeader(title, backLabel, onBack)`: back control named after its destination, then the large title.
- `InsetGroup { … }` with `ListRow`, `SwitchRow`, `RadioRow`, `InfoRow`, separated by `GroupDivider()`.
- `PrimaryButton` (one per screen at most), `TonalButton` (Play / Shuffle style), `TextAction`, `IconAction`.
- `FilterChip`: switching views of one list (announced as a tab).
- `SearchField`: the one search field.
- `TrackRow`: every song in every list.
- `Modifier.highlightClickable`: the press highlight for custom rows and cells (no ripple, no bounce).

## Layout rules

- Side margin 20 dp; content never touches the screen edge except edge-to-edge artwork and horizontal carousels (which still start at 20 dp).
- Large title at the top of each tab page; sub-pages use `SubPageHeader`.
- Lists end with bottom padding of `LocalBarInset.current + Space.l`, so nothing hides under the mini player and tab bar.
- Horizontal carousels: items snap, show a partial next item, and have the title under the artwork (Subhead + Footnote), not inside a card.
- Empty, loading and error states use `EmptyState` with a single action.
- Dialogs: Material `AlertDialog` with `AppText.Title3` title and plain text buttons.

## Do not

- Glow halos, pulsing orbs, rainbow or neon gradients, drop shadows on flat UI.
- Borders around selected chips; selection is a fill.
- Uppercase letter-spaced labels as decoration.
- Repeating a gear button on every page; Ajustes is reached from the title bar of Início only.
- Text below 11 sp, grey text under 4.5:1 contrast, `maxLines = 1` without an ellipsis.
