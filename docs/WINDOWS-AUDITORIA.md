# T.A.R.K.A.S. para Windows: auditoria inicial e plano

Data: 2026-11-18. Estado: auditoria parcial (itens marcados **pendente** ainda não foram medidos).

## 1. Versão de referência

- `versionName 2.1`, `versionCode 20`, branch `theme/tal-identity`, último commit `194365a`.
- A árvore de trabalho tem 540 mudanças não commitadas: o código foi reorganizado em `player/ biblioteca/ equalizador/ assistente/ geral/` (cada uma com `ui/` e `funcoes/`) e os arquivos antigos (`data/`, `ui/`) aparecem como apagados.
- Referência funcional = a **árvore de trabalho atual** (layout novo), não o HEAD. Antes de portar, o dono precisa commitar ou confirmar esse estado, senão o port parte de um alvo que muda.

## 2. Arquitetura atual (medido)

- Android nativo, Kotlin 2.2.10, Jetpack Compose (BOM 2026.02.01), Material 3, AGP 9.1.1, minSdk 24 / targetSdk 36, módulo único `:app`.
- 214 arquivos Kotlin em `main`, 109 em `test` (JVM, Robolectric em parte). Por área: equalizador 67, geral 50, player 45, biblioteca 37, assistente 15.
- Dependências: media3 1.4.1 (ExoPlayer + MediaSession), Room 2.8.4 (KSP), Coil 2.7, OkHttp, Haze, Backdrop (vidro líquido), coroutines 1.10.2.
- Serviços externos: Gemini (assistente), Jamendo, Audius, MusicBrainz, atualização própria do app (`geral/funcoes/update`).
- Permissões Android usadas: INTERNET, RECORD_AUDIO, READ_MEDIA_AUDIO, POST_NOTIFICATIONS, WAKE_LOCK, VIBRATE, serviços em primeiro plano (reprodução, projeção de mídia), RECEIVE_BOOT_COMPLETED, instalar pacotes.
- Componentes Android: 1 activity, 5 serviços, 3 receivers (inclui widget).

## 3. Portabilidade (medido por imports)

| Grupo | Arquivos | Leitura |
|---|---|---|
| Sem nenhum import `android.*` / `androidx.*` | 87 | Reusáveis como estão (DSP, `EqDsp`, `Biquad`, espacial, ordenação, busca, modelos). |
| Usam `androidx.compose` | 68 | Reusáveis com Compose Multiplatform; troca de imports de ícones/recursos. |
| Usam `android.*`, media3, lifecycle, room, core | 128 | Precisam de camada de plataforma (`expect/actual`) ou troca de biblioteca. |

Imports mais frequentes fora do Compose: `android.content` 103, `androidx.media3` 54, `android.os` 39, `android.media` 39, `androidx.lifecycle` 30, `android.app` 26, `androidx.activity` 25, `androidx.room` 18.

## 4. Tecnologia recomendada

**Kotlin + Compose Multiplatform Desktop (JVM)**, como projeto Gradle separado em `windows/`, com um módulo `shared` extraído gradualmente do `:app`.

Por quê: 2080 imports de Compose e 87 arquivos puros mantêm UI, tokens da grade T.A.L. e DSP idênticos; reescrever em outro stack (WinUI, Electron) duplicaria o motor de áudio e quebraria a paridade com os testes `EqDspTest` e `SpatialEngineTest`. Instalador via `jpackage` (EXE/MSI) do plugin Compose Desktop.

## 5. O que exige adaptação (mapa Android → Windows)

| Android | Windows |
|---|---|
| media3 ExoPlayer + MediaSession | Saída própria: decodificação (FFmpeg/miniaudio via JNA) → `ParametricEqAudioProcessor` → WASAPI; controles de mídia via SMTC |
| Room (KSP) | Room KMP (suporta desktop) com os mesmos schemas |
| Coil 2.7 | Coil 3 |
| Backdrop (AGSL, vidro líquido) | Skia `RuntimeEffect` (Haze já tem versão KMP); fallback visual a decidir com o dono |
| `systemaudio/*` (MediaProjection) | Captura loopback WASAPI |
| `OutputDetection/Routing` | Enumeração de dispositivos WASAPI |
| Assistente por voz (`android.speech`) | SAPI / reconhecimento do Windows; Gemini igual |
| Serviço em primeiro plano, widget, boot | Bandeja do sistema, início com o Windows, mini janela |
| Notificações, vibração | Notificação do Windows; vibração sem equivalente (documentar) |
| Pasta de músicas (`READ_MEDIA_AUDIO`) | Seletor de pastas + varredura do disco |
| Atualização por instalação de APK | Atualização do instalador |
| Toque/gestos | Mouse, teclado, atalhos, redimensionamento |

## 6. Plano

1. **Auditoria funcional completa (pendente):** lista de telas, funcionalidades e fluxos por área, vira a matriz de paridade (`docs/WINDOWS-PARIDADE.md`).
2. **Estrutura:** `windows/` com Gradle próprio, sem tocar no `:app` nem nos arquivos compartilhados (regra do `QUADRO.md`).
3. **Extrair `shared`:** primeiro os 87 arquivos puros, com os testes JVM correspondentes passando.
4. **Camada de plataforma:** áudio, banco, rede, arquivos, mídia do sistema.
5. **UI desktop:** telas portadas, layout em painéis, teclado e atalhos, mesma identidade.
6. **Testes e comparação** com a versão Android (metas de precisão do `CLAUDE.md` valem: resposta em frequência, latência do DSP).
7. **Instalador** `jpackage` e atualização.

## 7. Riscos e decisões que são do dono

- Alvo móvel: 4 sessões editam este checkout; o port só é confiável sobre um commit estável.
- Metas de latência ponta a ponta e dropouts foram definidas para o S23; para o Windows **não há metas**. Perguntar ao dono, não inventar.
- Vidro líquido e alguns efeitos podem não ter paridade visual exata no Skia; será documentado com alternativa.
- Chaves de API (`.env`) precisam de tratamento seguro no instalador.
