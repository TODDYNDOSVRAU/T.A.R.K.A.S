# Windows: visual language from the reference video (owner, 2026-11-18)

Source: the owner's video, app interface only (about 6–20 s). Brand, logo and marketing screens are ignored. No name, logo or proprietary element of the reference is copied; identity stays T.A.L. (Space Grotesk / Inter / JetBrains Mono, Ice/Teal accent ≈ 5%, single-colour logo).

Owner decisions:
- Target: **Windows** only.
- Import of a playlist by link: **left out for now** (its own task later).
- Backdrop from the cover: **yes, under a dark veil**. Text contrast ≥ 4.5:1, accent unchanged.

## What the reference shows, and the T.A.R.K.A.S. equivalent

| Reference | Windows implementation | Owner session |
|---|---|---|
| Dark window; the current cover, blurred, fills the background with its dominant colours | `shell/Backdrop.kt`: `ShellBackdrop.art` (ImageBitmap) and `.tint`, drawn blurred under a veil (`background` at 0.80 alpha on dark, 0.88 on light; `BackdropTest` keeps body text ≥ 4.5:1 and secondary ≥ 3:1 over a pure white or black cover); the sidebar, side pane and transport bar become glass (surface at 0.55 alpha + hairline) | win-geral (done in shell) |
| Thin icon sidebar, rounded selected state | `Shell.kt` SideNav (already icons + collapse) | win-geral |
| **Home: Speed dial** — section title with a small icon; 2-column grid of rows, each 48 dp cover (r6) + title (Body) + subtitle (Small, onSurfaceVariant), 12 dp rounded hover tile | Speed dial = favourites, most-played tracks and playlists (real data: `PlayStats`, playlists DAO). Click plays, right-click menu | win-biblioteca |
| **Home: Daily Mixes** — horizontal row of large square covers (192 dp, r12), caption under them; scrolls sideways with the wheel and arrows | Daily Mixes = the shared `AutoLists` (recent, most played, forgotten, per-genre); click plays the list | win-biblioteca |
| **Home: Listening right now** — row of covers of what is playing / recently played | Recent history from the player (`PlayerUi.history`) | win-biblioteca |
| **Bottom player** — slim bar: cover 48 + title/artist left; prev / play / next centred over a thin progress line; volume, queue, lyrics right | `Panels.transport` 96 dp slot; register it and draw on the glass bar | win-player |
| **Now playing** — cover (384, r12) with title, artist, thin seek and controls under it on the left; lyrics on the right, large (Title size), current line bright (onSurface), others at 0.35 alpha, line advances with a 200–300 ms ease; past lines fade upward | `PlayerScreen` + `LyricsView`, synced from the shared LRC parser; keep waveform seek; drop the per-screen tint gradient (the shell backdrop replaces it) | win-player |
| **Cover is the backdrop** | The player sets `ShellBackdrop.art` / `.tint` when the track changes (real embedded artwork; tint from `Track.colorHex` when there is none) | win-player |
| Import a playlist by link | not in scope (owner) | — |

## Rules kept
- T.A.L. grid tokens only; every drag has a tap/keyboard alternative; double-tap resets; motion 150–300 ms ease-out.
- Real data only. An empty section shows an honest empty state, never sample covers.
- Validated at 1024x640, 1366x768, 1920x1080, 2560x1440, light and dark.
