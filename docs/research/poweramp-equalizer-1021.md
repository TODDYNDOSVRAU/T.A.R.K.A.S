# Estudo: Poweramp Equalizer (build 1021)

Análise estática do `com.maxmpz.equalizer`, build 1021004 (APKM do APKMirror de 2026-02-20), para aprender conceitos aplicáveis ao equalizador do T.A.R.K.A.S. Nenhum código, recurso ou texto foi copiado; tudo está descrito com palavras próprias.

**Método:**
- JADX 1.5.6 sobre o `base.apk`, com boa parte dos nomes ofuscada (`p000/*`).
- Strings e exports JNI da `libpeqcore.so` (arm64).
- Leitura do banco `assets/auto_eq.db` com o `node:sqlite`.
- Comparação com `data/systemaudio/*` e `data/audio/dsp/*` do T.A.R.K.A.S.

Nada foi executado: o que roda dentro da `libpeqcore` só é conhecido pelas assinaturas.

**Marcação:** **[O]** = observado no código, recursos ou strings; **[I]** = inferido.

---

## 1. Como o EQ chega aos outros apps

### Descobrir sessões

São três caminhos.

**1. Broadcast (padrão).** **[O]**
- O app recebe `OPEN/CLOSE_AUDIO_EFFECT_CONTROL_SESSION` com prioridade 999.
- Corrige casos conhecidos de players que mandam o broadcast errado (exemplo: um app que manda CLOSE no lugar de OPEN).

**2. "Advanced Player Tracking", para players que não anunciam a sessão.** **[O]**
- Pede DUMP (por ADB) e acesso a notificações.
- O acesso a notificações não lê notificação nenhuma. Serve para duas coisas:
  - ter direito a `MediaSessionManager.addOnActiveSessionsChangedListener`;
  - manter um componente vivo que registra `AudioManager.registerAudioPlaybackCallback`.
- Esses eventos disparam uma investigação com debounce de 50 ms (gatilho curto) ou 1 s (gatilho longo).
- **Não roda o processo `dumpsys`.** Pega os binders `audio` e `media.audio_flinger` e chama `dumpAsync` num pipe, cruzando duas tabelas:
  - em `audio`: pacote → pid, só players com USAGE_MEDIA ou UNKNOWN e sem SoundPool;
  - na tabela de tracks do AudioFlinger: pid → sessões, separando ativas de inativas e lendo as flags de saída (Direct, Fast, Offload).
- Compara com o estado anterior e gera os mesmos eventos de abrir e fechar do caminho por broadcast. Os dois caminhos compartilham um único ciclo de vida.

**3. "Global" (sessão 0).** **[O]**
- O app avisa honestamente: "descontinuado pelo Google, funciona em Samsung, falha em Pixels recentes".

### Efeito aplicado

- **[O]** Sempre `DynamicsProcessing` (DP), criado pelo construtor oculto de `AudioEffect` com **prioridade 1337**.
- **[O]** Os parâmetros vão como comandos crus `EFFECT_CMD_SET_PARAM` (bytes via JNI), sem os setters Java do DP. **[I]** Mais rápido e sem o estado do wrapper Java.
- **[O]** A criação do DP é tentada até 5 vezes com 100 ms entre tentativas. A ativação, até 3 vezes.
- **[O]** Quando o efeito não "pega", há duas saídas:
  - criar e soltar um DP descartável e, se precisar, baixar e voltar 1 passo do volume de mídia (50 ms), o que força o Android a refazer o caminho de áudio **[I]**;
  - em Samsung, pedir ao AudioFlinger para invalidar o track do app.

### Ciclo de vida

- **[O]** O fechamento de uma sessão só desmonta o efeito após 2 s, para não cair e voltar na troca de faixa. Com "Manter sessão" ligado, espera 8 h. Fechamentos seguidos de reabertura em menos de 500 ms são ignorados.
- **[O]** Tabela de apps conhecidos. Para cada app guarda:
  - ligado ou desligado;
  - uid;
  - última sessão e histórico de sessões;
  - sem DVC;
  - fade ao recriar a sessão;
  - manter sessão;
  - várias sessões.
- **[O]** Depois de reiniciar o processo, refaz os efeitos dos 4 apps vistos por último, desde que tenham sido vistos neste boot e o uid confira.
- **[O]** Serviço em primeiro plano (`mediaPlayback`, START_STICKY). A notificação tem 3 níveis: nenhuma, micro ou padrão.
- **[O]** Tile nas configurações rápidas mostrando o preset atual e ligando ou desligando o EQ.

---

## 2. O DSP

### Bandas

- **[O]** Seis tipos de filtro: passa-baixa, passa-alta, passa-banda, pico, prateleira grave e prateleira aguda.
- **[O]** Frequência de 1 a 24.000 Hz, Q de 0,1 a 12, ganho de ±15 dB.
- **[O]** Cada banda tem canal: L+R, só L ou só R (EQ por canal).
- **[O]** Graves e agudos são duas entradas a mais no preset (90 Hz com Q 0,8 e 10 kHz com Q 0,6), e só reforçam.
- **[O]** O gráfico tem 5 a 32 bandas em escala logarítmica. As opções prontas são 5, 10, 12, 15, 16, 24, 31 e 32. O Q padrão vai de 1,08 a 3,33 conforme o número de bandas.

### A curva amostrada no DP

O motivo de o EQ soar preciso em outros apps está aqui.

- **[O]** O app calcula os biquads das bandas e avalia a resposta exata em **até 300 pontos**, e esses pontos viram as bandas do DP.
- **[O]** A distribuição dos pontos:
  - o primeiro terço é linear até 1 kHz, a cada ~10 Hz, onde Q alto nos graves exige resolução;
  - o resto é distribuído por oitava até 20 kHz.
- **[O]** No Android 15+ há um teste automático: cria um DP de 300 bandas numa sessão nova e lê quantas o aparelho aceitou.
  - Se aceitou menos, usa o modo "AIDL 1" (128 pré + 128 pós).
  - Pixel 9+ e aparelhos com `mixer_paths_aidl.xml` vão para o "AIDL 2" (32 + 32, lista própria a partir de 10 Hz).
- **[O]** Contribuições menores que max(0,2 dB, |ganho|/20) são zeradas.
- **[O]** "Sobreposição de bandas":
  - **Cascata:** as bandas somam e podem dar picos.
  - **Envelope:** no modo gráfico, bandas vizinhas do mesmo sinal ficam com a maior em vez da soma, o que evita o "morro" entre elas.
  - Presets AutoEq paramétricos usam sempre cascata.
- **[O]** O ganho final de cada ponto fica entre −24 e +32 dB.
- **[O]** O tamanho de bloco vai de 4.096 a 16.384 amostras (~85 ms a 48 kHz), com valor separado para hi-res. O Android 9 precisa de bloco grande para não estalar nos graves.

### Volume, folga e limitador

- **[O]** **DVC (controle direto de volume).** Cada sessão também recebe um efeito Volume com prioridade 1337. **[I]** Com ele, o AudioFlinger entrega o volume à cadeia de efeitos: os reforços do EQ acontecem antes da atenuação do volume e não saturam.
  - Desliga sozinho com Bluetooth em "volume absoluto".
  - Pode ser desligado por saída e por app.
- **[O]** Sem DVC, o app aplica −6 dB fixos de folga.
- **[O]** "Suavizar ganhos" (ligado por padrão) limita os reforços positivos:
  - 3 dB + o corte do pré-amp, com DVC;
  - 6 dB + a folga extra, sem DVC.
- **[O]** Pré-amp e balanço vão no **ganho de entrada por canal** do DP. O balanço corta até 48 dB do lado oposto, então funciona também em outros apps.
- **[O]** Limitador com ataque ~0 e ratio 768 (50 no modo AIDL):
  - com DVC: release de 75 ms e teto de 0 dB;
  - sem DVC: release de 175 ms e teto de −3,5 dB.
- **[O]** Compressor de 2 bandas, com crossover em 200 Hz e todos os parâmetros abertos ao usuário. Fica em "Avançado".

---

## 3. Saídas, presets e AutoEq

- **[O]** **Tipos de saída:** fone com fio, alto-falante, Bluetooth, USB, Chromecast e outra.
  - A rota é detectada com `AudioDeviceCallback` e MediaRouter. Na troca de rota, o pipeline é refeito e a taxa de amostragem nativa é relida.
  - Também lê o codec Bluetooth (A2DP).
- **[O]** **Preset por saída.** Um preset pode ser ligado a tipos de saída ou a um aparelho específico pelo endereço.
  - Prioridade: aparelho com nome > tipo de saída. No player Poweramp, a música ainda vence os dois.
  - Diálogo: "Aplicar automaticamente este preset às saídas: …".
- **[O]** **Sugestão de correção.** Quando conecta um fone BT ou USB cujo nome bate com um perfil AutoEq, aparece um aviso de 30 s: "Toque para ver os presets AutoEQ deste aparelho". Há "Não sugerir para este aparelho" e um reset dessas recusas nas configurações.
- **[O]** **AutoEq offline.** O `auto_eq.db` traz 17.703 perfis: metade gráficos, metade paramétricos, com o banco de medição no campo `meta` (exemplo: "Oratory1990 over-ear"). É atualizado junto com o app. A busca usa um índice no nome, sem diferença de maiúsculas e minúsculas.
- **[O]** **Formato binário do preset ("PaP"):**
  - cabeçalho com contagem, flag de paramétrico e pré-amp;
  - por entrada: tipo, canal e flags (banda, paramétrica, travada), ganho e frequência;
  - nas paramétricas, mais Q e cor.
  - Para compartilhar, o preset é exportado em JSON.
- **[O]** **Importação:**
  - texto APO/AutoEq ("Preamp:" e "Filter n: ON PK Fc … Gain … Q …", até 64 bandas);
  - texto "GraphicEQ:" (no mínimo 5 pontos);
  - zip inteiro do projeto AutoEq.
  - Nome repetido recebe " (n)". Bandas importadas do AutoEq ficam **travadas**.
- **[O]** **Edição fina:**
  - toque acima ou abaixo do controle: ±1/20 da faixa;
  - toque longo no valor: digitar o número;
  - seleção múltipla de bandas para apagar ou travar;
  - alerta "pode dar salto de volume, tire os fones" nos controles arriscados.
- **[O]** **Gráfico paramétrico:** cada banda é desenhada com a própria cor, a 48 kHz, 1 ponto a cada 2 px. Uma banda só é recalculada quando muda.
- **[O]** **Visualizador (MilkDrop):** conta referências (só roda com alguém olhando), limitado a 30 fps. Sem sessão ativa, recua para 1 leitura a cada 2 s.
- **[O]** Não tem desfazer nem comparação A/B. O único "bypass" é o interruptor geral.

---

## 4. Onde o T.A.R.K.A.S. está

Conferido no código atual. As comparações abaixo são observadas em `data/systemaudio/*.kt` e `data/audio/dsp/*.kt`.

| Tema | Poweramp Equalizer | T.A.R.K.A.S. hoje |
|---|---|---|
| Resolução da curva em outros apps | até 300 pontos, densos abaixo de 1 kHz, com teste do limite do aparelho | 31 pontos de terço de oitava no pré-EQ do DP (`SessionEqualizer.kt`) |
| Descoberta de sessões | eventos (playback callback + sessões de mídia), dump por binder, cruza pid → sessão no AudioFlinger | varredura a cada 2 s, `dumpsys audio` e o campo `sessionId:` (`GlobalEqService.kt`, `ActiveAppsReader.kt`) |
| Folga contra saturação | DVC + −6 dB sem DVC + teto de reforço | pré-amp automático só no próprio player; em outros apps o pré-amp vai somado nas bandas |
| Balanço / L-R em outros apps | ganho de entrada por canal do DP | só no próprio player |
| Prioridade do efeito | 1337 | 1000 |
| Limitador em outros apps | ataque ~0, release 75/175 ms, ratio 768 | 1 ms, 60 ms, ratio 10 |
| Preset por saída / sugestão por fone | sim | correção por fone escolhida à mão; sem troca automática |
| AutoEq | 17.703 perfis offline | índice e arquivos baixados do GitHub sob demanda, com cache semanal |
| A/B, morph da curva, glide de coeficientes | não tem | tem |
| Root (auto-concessão do DUMP) | não tem | tem |

Uma ressalva sobre o campo `sessionId:`: no emulador com Android 16 ele aparece em `dumpsys audio` (visto durante o teste da normalização). A fragilidade é das versões mais antigas, e não verifiquei quais.

---

## 5. Prioridades sugeridas

Ordenadas por ganho ÷ esforço. Cada item tem um critério de verificação.

| # | Conceito | Verificação |
|---|---|---|
| 1 | Curva em outros apps com mais pontos (densos abaixo de 1 kHz), testando o limite do aparelho e caindo para 256 ou 64 | Uma banda de 60 Hz com Q 4 medida num app externo fica a menos de 0,5 dB da curva do T.A.R.K.A.S. |
| 2 | Folga em outros apps: pré-amp e balanço no ganho de entrada por canal do DP, e teto de reforço | Curva +12 dB num app externo sem saturar, com o limitador quase sem agir |
| 3 | Sessões por eventos (`AudioPlaybackCallback`) com debounce, varredura só como reserva | O EQ entra num app em menos de 300 ms após o play; o consumo em repouso cai |
| 4 | Cruzar `audio` (pid) com a tabela de tracks do AudioFlinger (sessão), sem depender de `sessionId:` | Teste com saídas reais de dumpsys de 2 ou 3 versões do Android |
| 5 | Desmontar o efeito com 2 s de atraso; novas tentativas na criação | A troca de faixa num app externo não tem "buraco" sem EQ |
| 6 | Correção por fone aplicada sozinha ao conectar (por endereço BT/USB), com sugestão e "não perguntar de novo" | Conectar o fone muda a correção sem tocar na tela |
| 7 | Importar texto APO e GraphicEQ (arquivo e área de transferência) e exportar | Os arquivos do AutoEq importam idênticos ao baixado |
| 8 | AutoEq offline (banco compacto), com o GitHub só para atualizar | A busca funciona em modo avião |
| 9 | Envelope × cascata no modo gráfico; mais opções de bandas (10, 15, 31) | Duas bandas vizinhas +6 dB: o envelope não passa de +6 dB |
| 10 | Edição fina: toque longo para digitar, travar banda, cor por banda no paramétrico | Teste de interface |

Os itens 1 a 5 são do motor e afetam o som. Os itens 6 a 10 são da interface.

## Pendências e incertezas

- **Lógica nativa:** a matemática dos coeficientes, o que exatamente `native_modify_coefs_for_gain` faz, a escala dos graves e agudos e os valores padrão do compressor.
- **Gestos:** os gestos da tela principal ficam no skin, que não está nos layouts decompilados.
- **Achados de agentes, sem conferência linha a linha:** as seções 1 a 3 foram levantadas por dois agentes de leitura. Conferi pessoalmente só o formato do banco AutoEq e as afirmações sobre o código do T.A.R.K.A.S.
