# T.A.R.K.A.S.: plano para nível de produto

- **Data:** 2026-11-14, sobre a 1.17 (commit `b0fd24a`).
- **Pergunta do dono:** o que falta para o app parecer de empresa grande, completo, sem arame e fita; estrutura de código, aparência e funções, por janela.
- **Como foi levantado:** cinco auditorias de leitura do código (Player, Biblioteca e Início, Equalizador e Espacial, Assistente, arquitetura e Ajustes), mais a varredura no aparelho da 1.15 (`UI-1.15.md`). Os achados de maior peso foram conferidos por mim no código (marcados **[conferido]**). Os demais vêm do relatório da auditoria, com arquivo e linha, e precisam ser reconferidos pela sessão dona antes de mexer.
- **Limite:** nenhum print novo da 1.17. O S23 não estava conectado. Tudo que é "aparência" abaixo sai do código e da varredura da 1.15, não de tela vista hoje.
- **Nada foi alterado no app.** Este arquivo é só o plano.

Cada item tem um código (G = geral, P = player, B = biblioteca, I = início, E = equalizador, A = assistente, S = ajustes). Tamanho: P (horas), M (1 a 2 dias), G (vários dias). "Como saber que ficou certo" é a verificação mínima para chamar de pronto.

---

## 1. O que já está em nível de produto (não refazer)

- **Atualizador:** confere assinatura contra o app instalado, SHA-256, versionCode maior, recusa http, não instala com música tocando; 18 testes.
- **Áudio:** gapless, crossfade, normalização (ReplayGain/R128), foco de áudio, fone desconectado, retomada de faixa/fila/posição, timer de dormir com fim de faixa.
- **Equalizador:** AutoEq offline, pré-amp automático, limitador com lookahead, balanço, largura/mono, graves/agudos, comparar segurando, só as quatro cores da paleta, sliders com semântica de acessibilidade.
- **Textos:** os 5 arquivos de strings PT/EN têm as mesmas 756 chaves.
- **Onboarding:** termos não puláveis, app de trás escondido do leitor de tela.
- **Base:** 459 testes unitários, release minificado, targetSdk 36, aba e pilha de voltar sobrevivem à morte do processo.

## 2. Os dez pontos que mais separam o app de um produto grande

| # | Item | Por quê |
|---|---|---|
| 1 | Falhas em campo são invisíveis (G1) | Sem registro de crash nem exportação de diagnóstico; o R8 ainda apaga os números de linha. |
| 2 | Biblioteca sem Álbuns, Artistas e Gêneros (B1) **[conferido]** | É a primeira coisa que se espera de um player; hoje só Músicas, Playlists, Favoritas e Fila. |
| 3 | Playlists e favoritos em JSON no SharedPreferences (B5) | Leitura corrompida apaga tudo em silêncio; sem versão nem migração. Room está declarado e sem uso **[conferido]**. |
| 4 | Biblioteca não percebe arquivo novo (B6) **[conferido]** | Sem ContentObserver; só atualiza ao reabrir a aba. |
| 5 | Sem letras (P1) | Maior lacuna do player frente a Apple Music, Spotify e Poweramp. |
| 6 | Textos em português fixos no código (G4) **[conferido]** | Avisos do player, notificações, "Artista desconhecido", termos e licenças ignoram o idioma do sistema. |
| 7 | Assistente: erro vira "Não entendi" ou trava em "pensando" (A1, A2) **[conferido]** | Sem try/finally; falha de rede, cota e chave parecem a mesma coisa. |
| 8 | Um ViewModel de 1.478 linhas para tudo (G5) | As quatro sessões editam o mesmo arquivo; ~50 fluxos coletados na raiz. |
| 9 | Sem CI, sem lint, sem teste de interface (G2, G3) **[conferido]** | Nada impede regressão; `androidTest` só tem o exemplo do modelo. |
| 10 | Resíduos de modelo (G6) | `com.example`, `Theme.MyApplication`, cores roxas do template, comentários "AI Studio", contato pessoal no Sobre. |

---

## 3. Geral (sessão Geral; arquivos compartilhados com CLAIM)

### Estrutura
- **G1 · Diagnóstico de falhas · M.** (a) Manter `SourceFile`/`LineNumberTable` no R8 e anexar `mapping.txt` a cada release. (b) Gravador local dos últimos 5 crashes (handler encadeado ao anterior) e leitura de `ApplicationExitInfo` na abertura. (c) Página "Diagnóstico" na camada ENGINEERING que exporta versão, aparelho, crashes e ajustes pelo seletor de arquivos. Sem SDK de terceiros, para o texto de privacidade continuar verdadeiro. *Certo quando:* um crash forçado em release aparece na exportação e é decodificado com o mapping; teste prova que o handler anterior ainda roda e que a exportação não contém chave.
- **G2 · CI · P/M.** GitHub Action com `testDebugUnitTest` e `lintDebug`, sem assinatura; depois baseline de lint/detekt para falhar só em problema novo. Job de release assinado fica como decisão do dono (pôr a chave em segredo do GitHub). *Certo quando:* os 459 testes passam no runner a cada push.
- **G3 · Testes de interface · M.** Testes Compose (Robolectric) sobre as testTags que já existem; capturas de tela em claro/escuro e PT/EN; um teste instrumentado de fumaça. Relógio de animação fixo para não oscilar.
- **G4 · Idioma · M.** Tirar do código todo texto visível: avisos do `PlaybackController` (288–304, 648) **[conferido]**, notificações de `GlobalEqService` e `AudioCaptureService`, nome do canal de notificação, rótulo do bloco de Ajustes rápidos, "Artista desconhecido"/"Sem título"/"Biblioteca Local"/"Nova playlist" nos repositórios **[conferido]**, rótulos de área de transferência "Curva"/"Comando", "Limpar busca". `PlaybackNotice` passa a levar id de recurso e argumentos. Termos, privacidade, licenças e novidades ganham versão em inglês. *Certo quando:* um grep por literais acentuados em `data/` volta vazio e a tela em inglês não mostra português.
- **G5 · Dividir o ViewModel · G, em passos.** Primeiro `SettingsViewModel` e `UpdateViewModel`; depois cada tela coleta os próprios fluxos em vez da raiz (`MainActivity.kt` 326–378); depois Player, Biblioteca, Equalizador e Assistente, uma sessão cada. Os repositórios continuam a fonte da verdade. *Certo quando:* testes existentes passam, novos testes de ViewModel existem e a contagem de recomposição da casca cai (Layout Inspector, antes e depois).
- **G6 · Limpeza de modelo · P/M.** `Theme.MyApplication`, cores do template em `colors.xml`, comentários "AI Studio", entradas sem uso (Firebase, Room se não for adotado, Retrofit), plugins sem uso, um `OkHttpClient` só em vez de cinco. Renomear o pacote `com.example` é mecânico mas toca todos os arquivos: fazer num commit isolado, com as quatro sessões paradas.
- **G7 · Navegação · M.** Uma lista selada de rotas na pilha salva, no lugar dos quatro mecanismos atuais (enum de abas, booleano da curiosidade, `settingsSubPage`, `SettingsPage`). Manter a pilha própria: o player sobreposto depende da página de baixo continuar composta. Depois, atalhos por intent (bloco/notificação abre o Equalizador).
- **G8 · Preferências · M.** 7 arquivos de SharedPreferences sem versão; lógica do tema "amoled" escrita três vezes; leitura síncrona na abertura. Unificar chaves, versionar, medir o custo de abertura antes e depois.
- **G9 · Dependências e perfil de base · P.** media3 1.4.1, okhttp 4.10.0, lifecycle 2.8.7: conferir versões atuais e atualizar com a suíte. Perfil de base hoje é escrito à mão, sem medição.

### Aparência
- **G10 · Tokens.** Valores `dp` soltos em todas as telas (64 no Equalizador, 17 no Player, vários na Biblioteca e no Assistente) e três cores literais no Player. Passar para `Space`/`Size`/tema. *Certo quando:* capturas antes/depois são iguais.
- **G11 · Tela grande.** Nenhuma tela usa classe de tamanho de janela; só orientação e altura < 480 dp. Tablet em retrato recebe o layout de telefone esticado. Definir os pontos de quebra uma vez e aplicar por tela.
- **G12 · Nome do app.** Rótulo do lançador "T.A.R.K.A.S" sem o ponto final.

---

## 4. Player (sessão Player)

### Estrutura
- **P10 · `MusicScreen()` com ~36 parâmetros e 1.846 linhas · M.** Agrupar em `PlayerState`/`PlayerActions`; separar em `PlayerCarousel`, `PlayerControls`, `PlayerBackdrop`.
- **P11 · Restauração engole o erro · P.** `PlaybackController.kt` 725–727 **[conferido]**: qualquer exceção apaga o estado salvo, sem registro. Registrar (G1) e preservar o que der.
- **P12 · Estado salvo em JSON com a lista inteira · M.** Mover para o mesmo banco de B5, fora da thread principal.
- **P13 · Testes · M.** Nada cobre `PlaybackController` (timer, falha de faixa, salvar/restaurar) nem `SessionPlayer`.

### Aparência e interação
- **P20 · Estado de erro no player.** Hoje é um aviso passageiro; falta estado visível na própria tela quando a faixa não toca.
- **P21 · Acessibilidade.** 7 `contentDescription = null` em `MusicScreen`/`QueueList` para revisar um a um.
- **P22 · Háptica.** Existe; conferir se play, buscar, aleatório e repetir respondem do mesmo jeito.
- Pendências da 1.15 a reconferir no aparelho: F3 (snackbar empurra o layout), F6 (fundo sem capa), F8 (miniatura cortada no mini player).

### Funções
- **P1 · Letras · G.** Ler letra embutida (USLT/SYLT) e arquivo `.lrc` ao lado; tela de letra sincronizada com toque para buscar. Não encontrado no código.
- **P2 · Compartilhar faixa · P.** Não há no menu da faixa.
- **P3 · Salvar a fila como playlist · P.** `createPlaylist` aceita só uma faixa.
- **P4 · Velocidade de reprodução · M.** Não exposta; incluir no comando da sessão.
- **P5 · Notificação e sistema · M.** A sessão mostra uma lista falsa de três itens e nenhum botão próprio. Fila real, favorito e repetir na notificação.
- **P6 · Android Auto / Wear · G.** O manifesto anuncia navegação de mídia, mas o serviço não é `MediaLibraryService`: carros não listam a biblioteca.
- **P7 · Widget de tela inicial · M.** Não existe.
- **P8 · Depois:** repetição A-B, capítulos, pular silêncio, Cast.

---

## 5. Biblioteca (sessão Biblioteca)

### Estrutura
- **B5 · Banco de dados · G.** Playlists, favoritos, contagens e faixas salvas saem do SharedPreferences para Room, com versão e migração a partir do JSON atual. Ids estáveis (hoje `pl_<milissegundos>` e `local_<milissegundos>`: colisão possível e arquivo reescolhido perde favoritos). *Certo quando:* teste de migração com um JSON real e com um JSON corrompido; nada some.
- **B6 · Atualização viva · M.** ContentObserver do MediaStore, varredura com diferença em vez de reler tudo a cada entrada na aba.
- **B7 · Corrida no `MusicRepository` · M.** Estado em `var` escrito em IO e lido na principal. Passar para fluxo imutável.
- **B8 · `LibraryScreen` com ~45 parâmetros e 1.099 linhas · M.** Ordenação roda na thread principal dentro de `remember`; permissão duplicada entre tela e ViewModel.
- **B9 · Busca única · P.** Início e Biblioteca usam regras diferentes; a mesma consulta dá resultados diferentes.
- **B10 · Testes · M.** Nada cobre `scanDevice` nem a persistência.

### Aparência e interação
- **B20 · Carregamento.** Roda de espera simples; o Início tem esqueleto. Usar o mesmo.
- **B21 · Capas.** URI antiga de capa de álbum, pouco confiável a partir do Android 10 (inferido); imagem sem tamanho definido. Usar `loadThumbnail` com tamanho fixo.
- **B22 · Puxar para atualizar** e botão de nova varredura fora do estado vazio.
- **B23 · Toque longo e seleção múltipla** com ações em lote.
- **B24 · Acessibilidade** das linhas e da faixa A-Z.
- **B25 · Desempenho.** Animação de entrada em cada linha e camada fora de tela para o esmaecimento: medir em rolagem rápida com lista grande antes de decidir.

### Funções
- **B1 · Álbuns, Artistas, Gêneros · G.** Seções novas, página de álbum e de artista, gênero lido do MediaStore (hoje fixo em vazio).
- **B2 · Playlists completas · M.** Reordenar faixas, importar e exportar M3U.
- **B3 · Listas automáticas · M.** Adicionadas recentemente, tocadas recentemente, mais tocadas (os dados já existem em `PlayStats`).
- **B4 · Áudio que não é música · P/M.** Hoje só `IS_MUSIC` e mínimo de 30 s; nota de voz longa do WhatsApp entra. Regra por pasta conhecida e por tipo, com opção de mostrar.
- **B11 · Busca agrupada · M.** Músicas, álbuns, artistas e playlists num resultado só.
- **B12 · "Artista desconhecido"** como grupo próprio no fim da ordem.
- **B13 · Depois:** edição de tags.

## 6. Início (sessão Biblioteca)

- **I1 · Ordem do conteúdo.** A tela abre com carrosséis do Audius e Jamendo antes da música do próprio usuário. Primeiro "Continuar ouvindo" e "Tocadas recentemente" (`lastPlayedMs` já existe), depois o resto. **Decisão do dono** sobre o lugar do conteúdo online.
- **I2 · Faixas de demonstração** misturadas na mesma lista: separar ou esconder quando há biblioteca.
- **I3 · Filtro sem `remember`** a cada recomposição (`HomeScreen.kt` ~170).

---

## 7. Equalizador e Espacial (sessão Equalizador)

### Estrutura
- **E10 · `AudioEngineManager` com 1.180 linhas · G.** Separar detecção de saída, formato e estado do EQ.
- **E11 · Thread de áudio · M.** `EqDsp.process` aloca ao mudar o layout e recalcula o plano ao mudar a taxa (123–143). Preparar fora e trocar por referência. *Certo quando:* teste de degrau sem descontinuidade ao adicionar banda e ao trocar taxa.
- **E12 · Espacial cai para bypass em silêncio · P.** `SpatialAudioProcessor.kt` 193–197 **[conferido]**: o efeito some sem aviso. Registrar e mostrar o estado no painel.
- **E13 · Falha ao anexar o EQ de sessão** só vai para o logcat; conferir se chega à tela.
- **E14 · Duas listas de bandas para a mesma curva** (`bands` e `paramBands`), com remendos para o estado "editado". Unificar.
- **E15 · Testes.** Limitador, deslize de parâmetros e coeficientes em 96/192 kHz: conferir dentro de `EqDspTest` antes de escrever.

### Aparência e interação
- **E20 · Gráfico e alças das bandas sem semântica** (grep vazio em `EqGraph`/`EqStrip`): invisíveis ao TalkBack.
- **E21 · Qual predefinição e qual fone estão ativos** no topo do console (S7 da 1.15, a reconferir).
- **E22 · Desfazer geral**, não só ao apagar banda.
- **E23 · Tela larga:** sem layout próprio além do que a 1.16 fez para paisagem.

### Funções
- **E1 · Perfil completo por saída · G.** Hoje só a correção do fone troca com a saída; curva e espacial são globais. A política sai do ViewModel para a camada de áudio.
- **E2 · Comparação com volume igualado · M.**
- **E3 · Indicador de atuação do limitador / clipping · P/M.**
- **E4 · Importar e exportar predefinição por arquivo e compartilhar · M.** Hoje só área de transferência.
- **E5 · Crossfeed · M.**
- **E6 · Rastreio de cabeça · G.** Previsto no código, não iniciado.
- **E7 · Depois:** saída bit-perfect para DAC USB, escolha de HRTF, EQ por álbum, qualidade do reamostrador.

---

## 8. Assistente (sessão Assistente)

### Estrutura
- **A1 · Resposta perdida · P.** `TarkasViewModel.kt` 1140–1144 **[conferido]** sem try/finally: uma exceção deixa "pensando" para sempre. Sem cancelar nem fila para dois envios.
- **A2 · Erros com nome · P/M.** Offline, chave recusada e cota hoje viram "Não entendi". Mensagem própria para cada um e botão de tentar de novo. `maxOutputTokens = 120` corta frases; conferir `finishReason`.
- **A3 · Chave do Gemini no APK · decisão do dono.** A chave entra pelo `BuildConfig`. A varredura da 1.15 mostrou "Não configurada", então hoje não há chave publicada; o risco aparece no dia em que houver. Caminhos: chave do próprio usuário guardada cifrada em Ajustes, ou servidor intermediário.
- **A4 · Privacidade · P.** Com a chave ativa, texto digitado e título/artista vão ao Google sem aviso nem chave de desligar na interface (`useGeminiIfAvailable` existe e nada o controla).
- **A5 · Camada de intenções · M.** Trocar a cadeia de 300 linhas de `contains()` por uma tabela de comandos com sinônimos em PT e EN, negação e teste por comando. Hoje os comandos são só em português, e os chips em inglês enviam o comando em português.
- **A6 · Histórico** só em memória: guardar e dar "Limpar conversa".
- **A7 · Testes** do pedido/resposta do Gemini e dos caminhos de erro.

### Aparência e interação
- **A20 · Indicador de "pensando"** é uma linha de texto parada.
- **A21 · Mensagens** sem copiar/selecionar, sem hora, largura fixa em 320 dp.
- **A22 · Campo de uma linha:** pedido longo rola na horizontal.
- **A23 · Chips fixos:** não mudam com o contexto (tocando, pausado, sem IA online).

### Funções
- **A10 · "Tocar X" · M.** Busca na biblioteca por música, álbum, artista ou playlist. É o comando mais útil e não existe.
- **A11 · Ligar ao que o app já faz · M.** Timer de dormir real (o "timer" do chat é outro e não pausa a música), fila, espacial, volume.
- **A12 · Desfazer** para predefinição e graves/agudos aplicados pelo chat.
- **A13 · Voz:** hoje abre o diálogo do sistema; sem estado de escuta na tela e sem resposta falada.
- **A14 · IA online que age:** hoje o Gemini só conversa; com chamada de função ele poderia executar os mesmos comandos de A5.

---

## 9. Ajustes, Atualização e Sobre (sessão Geral)

- **S1 · Três camadas · M.** Ajustes é uma lista plana de ~13 grupos. Organizar em ESSENTIAL, ADVANCED e ENGINEERING (a regra da identidade), com busca. O Diagnóstico (G1) entra em ENGINEERING.
- **S2 · Exportar sem restaurar · M.** Só exporta, e deixa de fora reprodução, normalização e arquivos da biblioteca. Completar e criar a restauração; definir as regras de backup do Android (hoje são o modelo). *Certo quando:* exportar, apagar dados, restaurar e comparar.
- **S3 · Idioma · P/M.** Seletor de idioma (`localeConfig`) e textos legais em inglês (ver G4).
- **S4 · Atualização · P.** Mostrar as notas da versão (já são lidas e nunca exibidas); permitir cancelar o download; a reconferência na instalação usar também o `.sha256` ao lado.
- **S5 · Licenças · P.** Lista feita à mão; faltam Guava, Okio (inferido) e o texto completo do MIT do AutoEq. Gerar a partir das dependências.
- **S6 · Sobre · decisão do dono.** Contato é caixa pessoal e perfil social; o próprio arquivo registra que os textos não passaram por advogado.

---

## 10. Ordem proposta

| Fase | O que entra | Por quê primeiro |
|---|---|---|
| **1. Enxergar e não quebrar** | G1, G2, A1, A2, P11, E12, G12 | Baixo risco; sem isso não dá para saber o que falha em campo. |
| **2. Idioma e acabamento** | G4, S3, S4, S5, G6 (sem renomear pacote), G10 | Tira as marcas de projeto pessoal sem mudar comportamento. |
| **3. Dados sólidos** | B5, P12, B6, B7, S2, G8 | Tudo que vem depois (álbuns, listas automáticas, restauração) apoia-se no banco. |
| **4. Funções que se veem** | B1, I1, B3, P2, P3, P1, A10, A11, E3, E2 | A diferença que o usuário percebe. |
| **5. Estrutura** | G5, P10, B8, E10, E14, A5, G7, G3 | Em passos, cada um publicável; com testes da fase 1 a 3 como rede. |
| **6. Sistema e alcance** | P5, P6, P7, E1, E6, G11, S1 | Maior esforço; depende das fases 3 e 5. |

Cada fase fecha com a suíte completa, uma passada no S23 (claro/escuro, PT/EN, retrato/paisagem) e uma release.

## 11. Decisões que são do dono

1. **Chave do Gemini (A3):** chave do usuário em Ajustes, servidor intermediário, ou manter sem IA online.
2. **Início (I1):** a música do usuário passa à frente do conteúdo online?
3. **Assinatura no CI (G2):** a chave vai para os segredos do GitHub ou a release continua manual.
4. **Renomear `com.example` (G6):** quando parar as quatro sessões para o commit único.
5. **Contato e revisão jurídica (S6).**
6. **Metas numéricas (definidas pelo dono em 2026-11-16):** latência DSP ≤ 5 ms (EQ) e ≤ 24 ms (Spatial); ponta a ponta no S23 (cabo) ≤ 40 ms (EQ) e ≤ 60 ms (Spatial); 0 quedas (dropouts/underruns) por hora em reprodução contínua na build perf no S23; desvio da resposta em frequência ± 0,20 dB na curva combinada (20 Hz–20 kHz), ± 0,05 dB no centro do filtro isolado e 0,00 dB em bypass/plano (< −100 dB residual float).
7. **Escopo da fase 6:** Android Auto, widget e rastreio de cabeça são os itens mais caros; quais entram.

## 12. O que este plano não cobre

- Nenhuma tela da 1.17 foi vista hoje; achados visuais novos podem aparecer na passada no aparelho.
- As auditorias leram os arquivos grandes por amostra (`MusicScreen`, `AudioEngineManager`, `CommonComponents`). Itens marcados "inferido" nos relatórios não foram provados.
- Tamanhos são estimativas, não compromissos.
- Pendências antigas seguem abertas: causa da distorção, escuta do espacial e do 5.1, telas em inglês, TalkBack.
