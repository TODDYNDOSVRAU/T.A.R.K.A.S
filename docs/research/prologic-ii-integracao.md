# Pro Logic II e a cadeia de áudio do T.A.R.K.A.S.

Estudo de 2026-11-12. Nada de código foi alterado. Cada afirmação traz a marca **[código]** (li no repositório), **[fonte]** (veio de uma fonte pública, listada no fim) ou **[não verificado]**.

## 1. Veredito

1. O Pro Logic II (PL II) é um decodificador de matriz ativa: lê dois canais (Lt/Rt) e *estima de onde vem cada parte do som* para distribuí-la em L, C, R, Ls, Rs. Não separa instrumentos nem usa metadados.
2. O **Tarkas Spatial já faz a metade analítica disso**: STFT, coerência entre canais, pan por bin e divisão em direto/ambiência (`SpatialEngine.frame()`) **[código]**. Para fones, ele já posiciona o direto em azimute contínuo e manda a ambiência para trás e para cima. Um PL II na frente dele, para saída em fones, **traria pouco além do que já existe**.
3. O que um upmixer discreto (estéreo para 5.1) acrescenta de verdade:
   - **saída multicanal real** (HDMI, USB, receptor) quando a rota aceitar mais de 2 canais;
   - **fontes 5.1 que hoje não são virtualizadas**: `SpatialAudioProcessor` só aceita 2 canais e devolve `NOT_STEREO` **[código]**; um FLAC 5.1 hoje cai no downmix do Android;
   - **controles de usuário com sentido** (largura do centro, profundidade frente/trás, panorama), como no PL II Music **[fonte]**.
4. Por isso a ordem recomendada é: **primeiro 5.1 para binaural** (maior ganho, usa tudo que já existe), **depois o upmixer próprio**, **por último saída multicanal real**.
5. Não dá para copiar o PL II. O algoritmo interno dele (Gundry, AES 2001) **não foi obtido** neste estudo **[não verificado]**. O que se propõe é um decodificador próprio, com o mesmo objetivo, e nome próprio. "Dolby", "Pro Logic" e "Atmos" são marcas da Dolby e não entram em tela, texto nem arquivo distribuído sem licença.

## 2. Como o PL II reconhece e encaminha o áudio

### Codificação (como o conteúdo chega) [fonte]
Quatro canais viram dois (Lt e Rt). O centro entra igual nos dois lados. Os surrounds entram com defasagem de ±90° e polaridade oposta entre os lados. A mídia precisa preservar fase e amplitude entre os dois canais, senão o decodificador vaza som entre caixas.

### Decodificação passiva [fonte]
Centro = soma (Lt + Rt). Surround = diferença (Lt − Rt) com passa-baixa. É estável e vaza muito: tudo que está em fase vai ao centro, tudo fora de fase ao surround.

### A parte ativa (steering) [fonte]
O decodificador mede qual direção domina no campo sonoro, pela magnitude relativa e pela polaridade de Lt e Rt, e ajusta o ganho de cada saída para reforçar a caixa certa e cancelar o vazamento nas vizinhas. O PL II é uma implementação muito mais estável disso que o Pro Logic original (Jim Fosgate, 2000).

### O que o PL II tem de próprio (documentado em manuais de receptores) [fonte]
- Modo **Music**, para música estéreo, com três ajustes: **Center Width** (0 sem espalhar, 7 centro fantasma), **Dimension** (desloca o campo para frente ou para trás), **Panorama** (estende a imagem frontal para os surrounds).
- Os ajustes só valem no modo Music.

### Não confirmado [não verificado]
Largura de banda dos surrounds (se são de faixa cheia e estéreo no PL II), o modo Game, as constantes de tempo do steering e a matemática exata. A pesquisa não trouxe esses pontos.

## 3. O que já existe no app

Cadeia atual do player, em `AudioEngineManager.kt:437` **[código]**:

```
FloatPcmAudioProcessor → LoudnessMeterProcessor → SpatialAudioProcessor → ParametricEqAudioProcessor
```

- `FloatPcmAudioProcessor` converte para float e **mantém o número de canais** (`FloatPcmAudioProcessor.kt:26`).
- `SpatialAudioProcessor.onConfigure` só aceita 2 canais em PCM 16 bits ou float; qualquer outra coisa é repassada intacta (`SpatialAudioProcessor.kt:60`).
- `SpatialEngine.frame()` (`SpatialEngine.kt:277`), por bin de frequência e a cada hop de 512 amostras:
  - potências suavizadas de L e R e a correlação cruzada;
  - coerência ψ, dominância e `d = max(ψ, dom)^γ`;
  - azimute θ pela razão de níveis;
  - **direto** (projetado no vetor de pan, com polaridade) é posicionado com HRTF em azimute `w·θ` e elevação que cresce com a frequência;
  - **ambiência** (a parte incoerente) vai por filtros HRTF somados com fase decorrelacionada, em 5 direções fixas (90°, 120°, 150° no plano; 60° e 130° a 40° de altura), `buildAmbience`;
  - mistura seca/molhada preservando magnitude, ajuste de loudness e limitador com lookahead.
- Latência do Spatial: `LATENCY + HOP` = 1120 quadros, cerca de 23 ms a 48 kHz (`SpatialAudioProcessor.kt:27`).
- `SphericalHeadHrtf.response(azimute, elevação)` devolve a resposta de cada ouvido por bin e já é usada para fontes virtuais fora do eixo.
- O Spatial desliga a troca de faixa com crossfade (`AudioEngineManager.kt:821`, `spatialActive`): a cauda do crossfade usa uma cadeia só com float + EQ.
- Áudio de outros apps (modos ADB e root) é tratado pelo `Virtualizer` do Android, não pela nossa cadeia (`SpatialControl.otherAppsStrength`). **Um upmixer nosso não alcança outros apps.**
- `detectCapabilities` lê `sampleRates` da saída, mas **não lê quantos canais a rota aceita** (`AudioEngineManager.kt:1095`). Esse dado falta para decidir se se pode mandar 6 canais ao sink.
- Existe um protótipo em Python que espelha o Kotlin: `tools/spatial_proto/tarkas_spatial/engine.py`, com testes. O Spatial foi desenvolvido assim (protótipo e depois port) **[código]**; o mesmo fluxo serve aqui.

## 4. Arquitetura proposta

### 4.1 Fonte → upmix → separação → virtualização → saída

```
fonte (2 ou 6 canais)
  │
  ├─ 2 canais ──► [Upmix próprio 2→5.1]  (opcional, modos Música / Filme)
  │                      │
  └─ 6 canais ───────────┤
                         ▼
                 5.1 (L C R Ls Rs LFE)
                         │
        ┌────────────────┼──────────────────────────┐
        ▼                ▼                          ▼
 [5.1 → binaural]   [5.1 → 2.0 estéreo]     [5.1 direto ao sink]
  fones (HRTF)      alto-falante/BT sem       só se a rota aceitar
  = "Atmos-like"    espacial: downmix fixo    6 canais (HDMI/USB)
        │                │                          │
        └────────────────┴──────────► EQ ◄──────────┘
                                       │
                                  AudioTrack (Android)
```

- "estéreo / 5.1 / Atmos" do pedido viram **destinos de renderização**, escolhidos pela rota detectada, não três botões soltos.
- "Atmos-like" = altura. O engine já tem `height` e `ambienceHeight` para fontes elevadas; acrescentar caixas virtuais altas (por exemplo ±45° azimute, 40° elevação) é o caminho de altura, sem metadados de objeto. **Android não entrega objetos Atmos a apps de terceiros**, e produzir Atmos de verdade exige codec e licença da Dolby. O nome na tela não pode ser Atmos.

### 4.2 Como fazer o upmix (próprio, não copiado)

Dentro do mesmo STFT do `SpatialEngine`, por bin:

1. **Direto → frente.** Com o pan θ e a coerência já calculados, o direto vai para L/C/R por pan de potência constante entre pares (L–C e C–R). Em fase e no meio vai ao C; no lado, a L ou R.
2. **Fora de fase → trás.** A parte com correlação negativa (a característica de conteúdo matriz-codificado em surround) vai para Ls/Rs.
3. **Ambiência (incoerente) → Ls/Rs**, decorrelacionada entre os dois lados, com nível controlado. No modo Música, pouco; no Filme, mais.
4. **LFE** = passa-baixa de ~120 Hz da soma L+R (gerenciamento de graves). Opcional.
5. Constantes de tempo (ataque e liberação do steering), largura do centro, profundidade e panorama viram parâmetros do mesmo tipo dos `SpatialKnob` atuais.

Conservar energia: a soma de potência das cinco saídas deve igualar a de entrada por bin, senão o loudness muda.

### 4.3 Como virtualizar 5.1 para fones

Cada canal é uma fonte virtual em azimute fixo (0°, ±30°, ±110°; LFE direto nos dois ouvidos), somando `SphericalHeadHrtf.response` por bin. É linear e barato. Serve também a faixas 5.1 de verdade.

### 4.4 Um estágio ou dois?

| | Dois processadores em série | Um engine fundido |
|---|---|---|
| Latência | soma duas (~23 ms cada) | uma |
| CPU | duas FFT por hop | uma |
| Testar | cada parte separada, mais fácil | só o conjunto |
| Reuso do `SpatialEngine` | nenhum | alto |

Recomendação: **protótipo em Python com os dois lados separados** (para medir cada um), depois **port fundido** em Kotlin, se a medição mostrar que a latência ou a CPU incomodam. Isso é uma decisão a confirmar com a medição, não uma certeza.

### 4.5 Saída do Android

- Telefone, fone com fio e Bluetooth A2DP são estéreo: não mandar 6 canais ao `AudioTrack`; o destino é binaural em 2 canais.
- Só HDMI, USB e alguns DACs aceitam multicanal. Dado a ler: `AudioDeviceInfo.getChannelCounts()` e `getChannelMasks()` (API 23+) na rota ativa, além de `AudioCapabilities.channelCount`, que hoje descreve a faixa e não a saída.
- O `Spatializer` do Android (API 32+) pode virtualizar PCM multicanal para fones em aparelhos que o suportem. Se existe no S23 do projeto e com que qualidade: **[não verificado]**.

## 5. Plano por fases, com verificação

| Fase | Entrega | Como se sabe que está certo |
|---|---|---|
| 0 | Ler `LoudnessMeterProcessor`, `ParametricEqAudioProcessor` e `EqDsp`: aceitam mais de 2 canais? | Leitura do código e teste unitário com 6 canais. **Pendente: não li esses arquivos.** |
| A | 5.1 → binaural, usando as HRTFs existentes. Protótipo Python, depois Kotlin. Troca `NOT_STEREO` por um estado "5.1". | Sinal de teste por canal (seno em um canal por vez): cada um aparece no azimute esperado, loudness igual ao do downmix; teste de 6 canais no JVM; escuta no aparelho |
| B | Upmixer próprio 2→5.1. Protótipo em `tools/`, depois Kotlin. | Matriz de vazamento: codificar sinais conhecidos em L, C, R, Ls, Rs para Lt/Rt (encoder próprio nos testes) e medir quanto cada saída recupera; conservação de energia; sem cliques ao ligar e desligar |
| C | Detecção de canais da rota e saída 5.1 real | Aparelho com HDMI/USB multicanal; nada aqui pode ser dado como pronto sem esse teste |
| D | Crossfade com a cadeia nova (hoje o Spatial o desliga) | Teste de transição |

Observações de teste:
- O `aresample=matrix_encoding=dplii` do FFmpeg gera material Lt/Rt, mas a lista de discussão do FFmpeg relata que a defasagem não é a correta e que o balanço de nível tem tickets abertos **[fonte]**. Para teste do decodificador, usar um encoder próprio, escrito no teste.
- **Metas numéricas** (vazamento máximo em dB, latência máxima, CPU máxima) **não estão definidas**. O `CLAUDE.md` do T.A.L. manda perguntar ao dono em vez de inventar. Ficam como pergunta aberta.

## 6. Riscos e limites

- **Licença e patentes:** a Dolby tem patentes sobre decodificação de matriz ativa. Quais ainda valem e se um algoritmo próprio as toca: **[não verificado]**; exige parecer jurídico antes de publicar.
- **Qualidade:** 5 caixas virtuais discretas soam menos naturais em fones do que o azimute contínuo que o Spatial já usa. Medir por escuta antes de oferecer o modo como padrão.
- **Outros apps:** o upmix só atua no áudio do próprio T.A.R.K.A.S.
- **Memória:** as tabelas HRTF já pesam cerca de 16 MB por taxa de amostragem (`SpatialControl`); caixas virtuais extras entram nas mesmas tabelas e não devem somar.
- **Dono do código:** tudo em `data/audio/**` e `ui/screens/audio/*` é da sessão **equalizador** (QUADRO). `AudioEngineManager.kt` e `SpatialControl.kt` entram na fase A, e mudanças em `TarkasViewModel.kt` ou `MainActivity.kt` exigem CLAIM.

## Fontes

- [Dolby Pro Logic (Wikipedia)](https://en.wikipedia.org/wiki/Dolby_Pro_Logic): codificação Lt/Rt, decodificação passiva, steering e o PL II.
- [Sound on Sound: Surround Sound Explained, parte 2](https://www.soundonsound.com/techniques/surround-sound-explained-part-2).
- [Sound field correcting apparatus (US 5,128,999)](https://image-ppubs.uspto.gov/dirsearch-public/print/downloadPdf/5128999): matriz passiva e filtro passa-baixa do surround.
- [Method and apparatus for audio matrix decoding (US 9,357,323)](https://patents.google.com/patent/US9357323) e [US 7,003,467](https://uspto.report/patent/grant/7,003,467): referências de decodificação ativa por vetor de dominância (lidas só pelos resumos da busca).
- [Matrix decoder with constant-power pairwise panning (WO2015017584)](https://patents.google.com/patent/WO2015017584A1/enExample): pan de potência constante entre pares.
- Manuais de receptores com os controles do modo Music: [Arcam](https://manualshive.com/en/manual/arcam/dv137/2963705?page=17), [Samsung HT-Q45](https://www.manualslib.com/manual/796046/Samsung-Ht-Q45.html?page=63).
- [FFmpeg: stereo upmix e matrix_encoding](https://ffmpeg.org/pipermail/ffmpeg-user/2023-June/056523.html), [ticket 4175](https://lists.ffmpeg.org/archives/list/ffmpeg-trac@ffmpeg.org/thread/YLHEQDK4NNVUWFI6JX6TYGZ2D5WEV5LC/): limites do `dplii` do FFmpeg.
- Não obtido: K. Gundry, "A new active matrix decoder for surround sound", AES 2001 (citado por patentes).
