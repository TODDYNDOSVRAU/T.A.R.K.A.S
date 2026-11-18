# T.A.R.K.A.S. Windows: sessões

As sessões do Windows são separadas das sessões mobile (Player, Biblioteca, Equalizador, Assistente, Geral).

## Regras
- Cada sessão edita apenas `windows/` e `docs/WINDOWS-*`. O app Android (`app/`) é só lido. O código puro entra pela lista `sharedMain` em `windows/build.gradle.kts` e nunca é copiado.
- Compilação: `bash .claude/sessoes/gradle.sh windows -p windows -q <tasks>`. A trava é `build.lock`; o código 75 significa "ocupado, tente de novo".
- Reserve arquivos com linhas CLAIM/DONE em `.claude/sessoes/QUADRO.md`, com a sessão marcada como `win-<area>`.
- Nada de telas falsas nem de dados estáticos. Mantenha a identidade T.A.L., a grade (`Grid`/`GridText`) e as cores de `MaterialTheme.colorScheme`.
- Sem commit, a menos que o dono peça. Nunca use `git checkout/restore/stash/reset/clean`.
- Nada de capturas de tela inteira nem de som alto sem perguntar ao dono.
- Uma tarefa só está pronta depois de testada: cite o comando, a saída e o código de saída. O que não foi verificado fica marcado como **pendente**.

## Estado atual
- O fatiamento de reprodução já existe: `windows/src/main/kotlin/com/example/windows/{Main.kt,audio/PcmPipeline.kt,audio/DesktopPlayer.kt}`, com 67 testes JVM passando.
- Auditoria: `docs/WINDOWS-AUDITORIA.md`.

## Sessões
| Sessão | Pacote | Escopo |
|---|---|---|
| win-base | `com.example.windows.{base,theme,res,state,db}` | Recursos (`R.string`/`R.drawable` → recursos Compose), tema e grade, `expect`-shims (clipboard, toast, back, lifecycle), estado desktop no lugar do `TarkasViewModel`, banco de dados (Room KMP), navegação em painéis |
| win-audio | `com.example.windows.equalizador` | Aba Áudio: EQ, paramétrico, predefinições, Spatial, Ajustes, saída |
| win-biblioteca | `com.example.windows.biblioteca` | Biblioteca, pastas, playlists, busca, ordenação, catálogo |
| win-player | `com.example.windows.player` | Player cheio e mini, fila, letras, timer, teclas de mídia/SMTC |
| win-assistente | `com.example.windows.assistente` | Chat, voz (SAPI), curiosidades |
| win-geral | `com.example.windows.geral` | Configurações, onboarding, Legal (incluindo as licenças JLayer/jFLAC), atualização, ícone, instalador MSI/EXE, `docs/WINDOWS-PARIDADE.md` |

As sessões de área só começam depois que a win-base escrever `DONE win-base` no QUADRO.
