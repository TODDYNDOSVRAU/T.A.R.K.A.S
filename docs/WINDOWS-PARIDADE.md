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
