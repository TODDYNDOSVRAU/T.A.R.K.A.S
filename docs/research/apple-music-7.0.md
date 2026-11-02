# Estudo: Apple Music para Android 7.0.0

Análise estática do pacote `com.apple.android.music` 7.0.0 (versionCode 1614, APKM do APKMirror de 2026-10-29), feita para aprender conceitos aplicáveis ao T.A.R.K.A.S. Nenhum código, recurso ou texto foi copiado. Tudo abaixo está descrito com palavras próprias.

**Método:** extração do bundle, JADX 1.5.6 sobre o `base.apk` (423 métodos não decompilaram), leitura de strings e exports JNI das `.so` arm64 e leitura de assets e XML de recursos.

**Limites:**
- Nada foi executado. O que roda dentro das bibliotecas nativas (pontuação do AutoMix, busca, decodificadores) só é conhecido pelas assinaturas e mensagens de log.
- DRM (FairPlay, "foothill") ficou fora de propósito.

**Marcação:** **[O]** = observado no código; **[I]** = inferido.

---

## 1. Motor de reprodução

### Pipeline

- **[O]** Usa um fork próprio do ExoPlayer2 antigo, não Media3. Media3 aparece só na camada de sessão (`MediaLibraryService`).
- **[O]** Tem decodificadores nativos próprios de ALAC e AAC/HE-AAC. Dolby (E-AC3-JOC) vai pelo MediaCodec do sistema.
- **[O]** Cada tipo de renderer de áudio existe em dobro, para tocar duas faixas ao mesmo tempo durante o crossfade. Cada um tem a própria sessão de áudio.
- **[O]** A saída é o `AudioTrack` comum, sem AAudio. O buffer do sink vale 4× o mínimo, limitado a 250–750 ms.
- **[O]** A profundidade do decodificador (16, 24 ou 32 bits) define a codificação PCM. PCM inteiro hi-res vira float, com exceção de uma lista de aparelhos Samsung antigos.
- **[O]** Na troca de formato (taxa, canais, bits ou codec), o renderer espera o sink esvaziar antes de reconfigurar. Não faz reamostragem.
- **[O]** Depois de criar o `AudioTrack`, lê as taxas suportadas pelo dispositivo roteado. Se nenhuma alcança a taxa da faixa, avisa o usuário ("seu fone/DAC não reproduz essa taxa"). Não esconde a limitação.

**Para o T.A.R.K.A.S.:** o aviso de "dispositivo abaixo da taxa da faixa" se aplica direto ao princípio de Integridade. O app já tem `AudioCapabilities.kt` e pode comparar `AudioDeviceInfo.getSampleRates()` com a taxa da faixa e informar na camada ENGINEERING.

### Transições: crossfade e AutoMix

- **[O]** Há três modos: Automático (AutoMix), Manual (duração fixa, padrão 6 s, controle de 1 a 12) e Desligado.
- **[O]** O AutoMix não analisa o áudio no aparelho. Os dados vêm do servidor por faixa, em lotes de 50, e ficam em cache local:
  - batidas e compassos em ms;
  - curva de loudness;
  - trechos de fade-in e fade-out já presentes na gravação;
  - BPM, tom, energia.
- **[O]** Um módulo nativo ("seamless composer") recebe as features das duas faixas e devolve uma "receita":
  - quando começa o fade-out da faixa atual e quanto dura;
  - quando entra a próxima e com que fade-in;
  - o deslocamento de início da segunda faixa;
  - um "ponto de cruzamento" que decide qual faixa é a dominante na interface;
  - uma pontuação, que o lado Java ignora.
- **[O]** Sem features, cai para "fim da faixa − duração do usuário". Uma faixa só é elegível se durar pelo menos 2× o fade.
- **[O]** O ganho é aplicado em passos de `setVolume` a cada duração/20, limitado a 200–500 ms. O fade-in é logarítmico e o fade-out exponencial. A transição termina com saída < 0,05 e entrada > 0,95.
- **[O]** Faixas consecutivas do mesmo álbum e disco (número + 1) nunca recebem crossfade. Tocam gapless, com encoder delay e padding aparados (AAC sem metadado assume 2112 amostras).
- **[O]** Crossfade fica desligado em repetir-uma e para conteúdo que não é música.

**Para o T.A.R.K.A.S.:**
- A regra "mesmo álbum e sequência → gapless, senão crossfade" é barata e resolve álbuns ao vivo e conceituais. Vale adotar de imediato se houver crossfade.
- O T.A.R.K.A.S. não tem servidor de análise. Um AutoMix equivalente exigiria análise no aparelho (onset/beat tracking e loudness por janela), cacheada por faixa local. Para Audius/Jamendo, a análise precisaria do stream inteiro, o que é caro.
- Uma alternativa honesta e verificável: detectar no aparelho silêncio e fade já presentes no fim e no início de cada faixa (RMS por janela) e posicionar o crossfade ali. Isso dispensa batidas.
- Como o T.A.R.K.A.S. tem DSP próprio (`ParametricEqAudioProcessor`, `SpatialAudioProcessor`), dá para fazer a rampa por amostra em vez de passos de `setVolume`, o que é mais preciso que a abordagem da Apple.

### Normalização de volume (Sound Check)

- **[O]** Fica ligada por padrão.
- **[O]** O loudness da faixa vem do metadado do arquivo: a caixa MP4 de loudness ou, como reserva, a tag iTunes de normalização convertida em dB com proteção de pico.
- **[O]** O alvo é −14 LUFS. O ganho é 10^((alvo − loudness)/20), **limitado a [0, 1]: só atenua, nunca amplifica**, então não há risco de clipping. Não encontrei limitador em Java.
- **[O]** O ganho multiplica o volume do crossfade antes de ir ao sink.

**Para o T.A.R.K.A.S.:**
- Não encontrei normalização no código atual (verificação por busca de termos, não exaustiva).
- Arquivos locais podem trazer ReplayGain (tags `REPLAYGAIN_TRACK_GAIN` e `_PEAK`). Na falta, um cálculo próprio de LUFS integrado (ITU-R BS.1770) cacheado por faixa.
- A regra "só atenua" é uma boa decisão de segurança. Se quiser amplificar, o limitador que o EQ já tem cobre o pico.

### Equalizador e áudio espacial

- **[O]** A Apple não tem EQ próprio no Android. Abre o painel de efeitos do sistema e copia as propriedades do `audiofx.Equalizer` para a sessão do outro renderer, para que as duas faixas do crossfade soem iguais.
- **[O]** Dolby Atmos é detectado só pela capacidade de codec (decoder E-AC3-JOC ou passthrough direto). Não usa a API `Spatializer` nem verifica fones. Tem modos Automático, Sempre e Desligado.
- **[I]** O T.A.R.K.A.S. está à frente aqui: EQ paramétrico, AutoEq e espacial próprio. Sobra uma lição: se houver dois players simultâneos (crossfade), o DSP precisa estar no caminho de ambos, ou depois da mixagem.

### Fila

- **[O]** É uma lista de IDs sobre "provedores" (biblioteca, catálogo, rádio, autoplay), com cache LRU de 256 itens resolvidos e janela de 100 próximos.
- **[O]** O shuffle é uma permutação uniforme que começa pela faixa escolhida.
  - Ligado no meio da fila, embaralha só a parte do usuário e mantém o autoplay em ordem.
  - A ordem original fica salva, então desligar o shuffle a restaura.
- **[O]** O autoplay usa até 10 itens da fila como semente e repõe quando restam menos de 3.
- **[O]** A posição salva é descartada se tiver mais de 12 h.

**Para o T.A.R.K.A.S.:** o `PlaybackOrder.kt` pode comparar duas regras: restaurar a ordem original ao desligar o shuffle e preservar a parte automática da fila. O descarte da posição após 12 h é uma decisão de produto a considerar (hoje a fila é restaurada sempre).

### Sessão, foco, ruído, timer

- **[O]** Foco de áudio:
  - duck → volume 0,2;
  - perda temporária → pausa e lembra se deve retomar;
  - perda permanente → pausa sem retomar;
  - ganho → 1,0 e retoma.
- **[O]** Mantém wakelock parcial e Wi-Fi lock de alto desempenho só durante a reprodução. Para o serviço 30 s depois de parado.
- **[O]** O receiver de "ruído" (fone desconectado) só existe enquanto toca e apenas pausa.
- **[O]** Timer de sono com dois modos: contagem regressiva ou "fim da faixa/álbum" (respeitando repetir). Não há fade-out ao disparar.
- **[I]** O `AudioFocusPolicy.kt` do T.A.R.K.A.S. já cobre os mesmos casos. Um fade-out curto (por exemplo 3–5 s) no fim do timer seria uma melhoria sobre a Apple.

### Streaming e erros

- **[O]** Buffer: 50 s, início com 5 s, retomada após rebuffer com 10 s.
- **[O]** A próxima faixa é pré-carregada em cache só se a rede permitir. O cache tem 200 MB por padrão, configurável de 0 a 5 GB.
- **[O]** A qualidade é escolhida por tipo de rede (Wi-Fi, celular, economia de dados). A variante tem bitrate ≤ 0,7× a banda estimada (mediana móvel).
- **[O]** Erros:
  - arquivo corrompido ou HTTP 416 → apaga a entrada do cache;
  - erro de rede ou timeout → **não pula** a faixa;
  - outros erros de fonte → pula para a próxima;
  - no máximo 2 tentativas manuais e debounce de 500 ms para erros repetidos;
  - cada erro chega à interface como um tipo específico, não uma mensagem genérica.

**Para o T.A.R.K.A.S.:**
- A regra "erro de rede não pula, erro de arquivo pula" evita varrer a fila inteira quando a conexão cai.
- O cache de stream com `SimpleCache` do Media3, com tamanho escolhido nas configurações avançadas, ajuda Audius e Jamendo.

---

## 2. Biblioteca e dados

- **[O]** A biblioteca do usuário fica num SQLite nativo. Fila de downloads, cache de respostas da API e cache de análise de áudio ficam em bancos separados.
- **[O]** Em cada faixa, a tabela de reprodução guarda:
  - encoder delay e padding (gapless);
  - energia de normalização;
  - preset de EQ por faixa;
  - início e fim customizados (ms).
- **[O]** As consultas devolvem uma lista de IDs mais "seções" (título e intervalo, para a barra A–Z). O objeto da faixa só é montado quando a linha aparece, com cache LRU.
- **[O]** Escritas passam por um único executor serial. Leituras e busca usam um pool.
- **[O]** As mudanças chegam à interface como eventos "entidade X mudou: IDs adicionados e removidos", não como "recarregue tudo".
- **[O]** O cache de respostas é stale-while-revalidate:
  1. emite o cache mesmo vencido;
  2. busca na rede;
  3. mescla por ID;
  4. emite de novo.

  A chave é (id, tipo, hash dos parâmetros), com validade e teto de 10 MB.
- **[O]** Downloads seguem a máquina de estados IDLE → PREPARED → DOWNLOADING → DOWNLOADED (+ PAUSED). Um gatilho SQL remove o item após 3 falhas. Outros gatilhos apagam álbum ou playlist quando o último filho sai. Falta de espaço gera um evento único e para a fila.
- **[O]** O tamanho pedido de capa é arredondado para múltiplos de 50 px, para que tamanhos próximos compartilhem a mesma imagem em cache.
- **[O]** O histórico só registra uma faixa depois de mais de 5 s de reprodução real. Contadores de reprodução e de pulo são separados.
- **[O]** A busca local usa um matcher Unicode (ICU) por locale. Testa título e depois artista, e mantém a ordem da lista sem pontuação.

**Para o T.A.R.K.A.S.:**
- Gravar encoder delay/padding e loudness por faixa local no primeiro scan evita recalcular a cada reprodução.
- A busca unificada (dispositivo + Audius + Jamendo) se beneficia do mesmo modelo: um tipo de resposta comum e mescla por ID.
- A regra de 5 s para o histórico e o contador de pulos alimentam futuras "mais tocadas" de forma confiável.
- Normalizar acentos e caixa pelo locale (`Collator`/`Normalizer`) na busca local, como a Apple faz com ICU, resolve "ação" vs "acao".

---

## 3. Interface, letras e movimento

### Mini player → player completo

- **[O]** É um BottomSheet. Todas as propriedades derivam da fração de arrasto; não há animação com tempo próprio:
  - mini player: alpha = e^(−300·f), some no primeiro ~1%;
  - player completo: alpha = max(0, 1 + 0,5·ln f), aparece a partir de ~13%;
  - barra inferior: sai com 1 − e^(−20·f);
  - capa: interpola de um retângulo ao outro com escala e translação. O raio do canto é dividido pela escala para parecer constante na tela.
- **[O]** No topo da lista de fila ou de letras, movimentos pequenos para baixo são ignorados. Isso evita que um fling que bate no topo feche o player.

**Para o T.A.R.K.A.S.:** o `PlayerExpand.kt` pode usar essas curvas sobre a fração. É compatível com a skill `tal-apple-design`, porque o gesto fica interrompível por natureza.

### Tela "tocando agora"

- **[O]** Escala da capa: pausada 0,85 (elevação 8 dp), tocando 1,0 (15 dp), durante o arraste da barra 0,95. Transição de 300 ms com desaceleração.
- **[O]** Fundo animado: não extrai paleta. Usa a própria capa:
  - três cópias giram em períodos de 120, 90 e 70 s;
  - saturação 2,5, véu escuro e claro, blur raio 25 sobre um bitmap ~1/24 do tamanho da tela;
  - redesenho a ~24 fps;
  - troca de capa por crossfade de 1 s.
- **[O]** Proteção de desempenho: com 3 quadros seguidos acima de 15 ms, para de girar e usa uma malha estática.
- **[O]** Movimento reduzido torna as rotações 2,1× mais lentas.
- **[O]** Não há slider de volume no player; fica com o sistema. Não usa háptica.

**Para o T.A.R.K.A.S.:**
- A proteção "3 quadros lentos → modo estático" é verificável e honesta. Vale para qualquer efeito pesado (haze/backdrop já usados no projeto).
- Na identidade T.A.L., um fundo saturado da capa competiria com a regra de acento ≤ 5%. Se usado, deve ser contido (baixa saturação e véu Absolute Black forte).

### Letras sincronizadas

- **[O]** Formato TTML com:
  - linhas e palavras temporizadas;
  - vocais de fundo;
  - cantores ("agentes": pessoa, grupo, personagem);
  - seções, tradução e pronúncia.

  O processador de tempo devolve o próximo prazo em ms, e a interface agenda o próximo tique em vez de checar a cada quadro.
- **[O]** Antecipação:
  - a linha ativa entra 500 ms antes;
  - a antecipação se adapta ao intervalo até a próxima linha (intervalo de 200–750 ms → antecipação de 480–750 ms, que também vira a duração da rolagem);
  - palavras entram 100 ms antes;
  - com áudio Atmos aplica-se um offset extra, para compensar a latência do decodificador.
- **[O]** O destaque palavra a palavra é uma máscara de gradiente de alpha (borda suave de 30 dp) sobre o texto, não uma troca de cor.
- **[O]** Linhas inativas ficam com escala 0,98, sem blur.
- **[O]** A rolagem dura 750 ms com curva (0,4; 0,1; 0; 1). As linhas abaixo da ativa atrasam em onda: ~25, 44, 56 e 62 ms.
- **[O]** Duetos: alternam o alinhamento a cada troca de cantor, respeitando RTL. Grupo fica sempre no início. Largura máxima de 85%.
- **[O]** Intervalo instrumental de 7 s ou mais mostra três pontos que se preenchem e "respiram". A saída é sincronizada para terminar 800 ms antes da volta da voz.
- **[O]** Palavras longas (≥ 1 s, ≤ 7 letras, fora de escritas como CJK) ganham brilho e escala até 1,14, proporcionais à duração.
- **[O]** Texto: 34 sp principal e 22 sp secundário, 32 dp entre linhas.

**Para o T.A.R.K.A.S.:**
- Letras sincronizadas existem em fontes abertas (LRC e LRC "enhanced" com tempo por palavra; LRCLIB tem API pública).
- Os conceitos que valem: antecipação adaptativa, agendar pelo próximo evento em vez de recompor a cada quadro, máscara de gradiente para o destaque, onda de atraso nas linhas abaixo e indicador de intervalo instrumental.
- Em Compose, a máscara pode ser feita com `drawWithContent` e `BlendMode.DstIn`.
- O offset de latência por saída (Bluetooth tem 100–300 ms) é um ajuste da camada ADVANCED que a Apple só aplica ao Atmos. O T.A.R.K.A.S. pode medi-lo com `AudioTrack.getTimestamp`.

---

## 4. Arquitetura geral

- **[O]** Activity única com Fragments e Views. Compose está entrando nas telas novas. Cada aba tem a própria pilha de navegação, todas com acesso ao mesmo grafo de detalhes.
- **[O]** Na inicialização, as bibliotecas nativas carregam em ordem de dependência. Uma falha de link é capturada e reportada com a lista real da pasta de libs, em vez de derrubar o app.
- **[O]** Um registro de inicializadores roda em ordem. Cada um recebe o resultado dos anteriores, e há uma máquina de estados que impede rodar duas vezes.
- **[O]** As configurações do motor de reprodução ficam num arquivo de preferências separado das de interface. Há um listener que propaga as mudanças ao player.
- **[O]** Diagnóstico em duas camadas:
  1. "Ajustes de depuração" para o usuário enviar ao suporte: logs, fila atual, arquivos da biblioteca e um relatório completo.
  2. Uma tela interna com ~50 chaves.

  O Crashlytics fica desligado no manifesto e só liga se o usuário permitir diagnóstico.
- **[O]** Logger em arquivo numa thread dedicada, com cabeçalho de aparelho, sistema e versão.
- **[O]** A escuta em grupo roda um SDK JavaScript num WebView invisível. Os assets são servidos do APK com uma origem HTTPS, e a ponte é por strings.
- **[O]** Configurações de áudio expostas:
  - Atmos (auto/sempre/desligado);
  - Lossless e Hi-Res por rede, com aviso de DAC externo;
  - crossfade (auto/manual/desligado);
  - Sound Check;
  - cache de reprodução;
  - economia de dados.
- **[O]** Seguem o idioma do sistema e declaram `localeConfig` para o idioma por app do Android 13+. Uma troca de idioma também recarrega conteúdo do servidor.

**Para o T.A.R.K.A.S.:**
- Separar as preferências do motor das de interface casa com a camada ADVANCED. Hoje o `PreferencesRepository.kt` concentra tudo.
- "Ajustes de depuração" com exportar logs, fila e estado do motor é exatamente a camada ENGINEERING do `CLAUDE.md`.
- Crash reporting só com consentimento combina com a política de privacidade atual ("sem backend").
- `localeConfig` mais strings EN cumpre o "PT e EN no mínimo" da identidade. Hoje a interface é só PT-BR.

---

## 5. Prioridades sugeridas para o T.A.R.K.A.S.

Ordenadas por ganho ÷ esforço. Cada item tem um critério de verificação.

| # | Conceito | Verificação |
|---|---|---|
| 1 | Normalização de loudness: ReplayGain da tag, ou LUFS calculado e cacheado; só atenua | Faixas com loudness conhecido medem −14 LUFS ± 0,5 na saída |
| 2 | Crossfade manual com exceção de álbum sequencial (gapless) e rampa por amostra no DSP | Teste unitário da regra; gravação da saída sem descontinuidade |
| 3 | Erro de rede não pula a fila; erro de arquivo pula; máximo de 2 tentativas | Teste com fonte que falha por tipo |
| 4 | Aviso de dispositivo abaixo da taxa da faixa (ENGINEERING) | Fone BT a 48 kHz com FLAC de 96 kHz mostra o aviso |
| 5 | Shuffle reversível (restaura a ordem original) | Teste de `PlaybackOrder` |
| 6 | Histórico só após 5 s, contador de pulos | Teste do contador |
| 7 | Curvas exp/log sobre a fração de arrasto no expand do player | Inspeção visual + teste de interrupção do gesto |
| 8 | Letras sincronizadas (LRC/LRCLIB) com antecipação adaptativa e máscara de gradiente | Desvio de sincronia medido < 100 ms |
| 9 | Tela de diagnóstico exportável (logs, fila, estado do motor) | Arquivo exportado abre e contém as três partes |
| 10 | AutoMix com análise local (silêncio/fade, depois batidas) | Pendente de pesquisa; não prometer antes de medir |

## Pendências e incertezas

- **Lógica nativa não observada:** como o composer pontua as transições, a forma exata do fallback e se há limitador, EQ ou dither em C++.
- **Achados por agentes, sem confirmação linha a linha:** as seções 1–4 foram levantadas em paralelo por agentes de leitura. Os números citados vêm dos arquivos decompilados, mas não foram revisados um a um aqui.
- **Comparação com o T.A.R.K.A.S.:** baseada em busca de termos no código atual, não em leitura completa.
