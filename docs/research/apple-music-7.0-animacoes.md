# Animações do Apple Music 7.0 que faltam no T.A.R.K.A.S.

Estudo estático do pacote `com.apple.android.music` 7.0.0 (versionCode 1614), feito em 2026-11-04 para decidir onde o T.A.R.K.A.S. ainda pode ganhar animação e função. Nenhum código, recurso ou texto foi copiado. Tudo está descrito com palavras próprias.

## Método e limites

- **Marcação:** **[R]** = observado nos recursos do pacote nesta sessão; **[J]** = conferido no código decompilado nesta sessão; **[N]** = observado no código pelo estudo anterior (`apple-music-7.0.md`) e ainda não reconferido; **[T]** = conferido por busca no código do T.A.R.K.A.S.
- **Ferramentas:** as ferramentas do REA não estavam conectadas nesta sessão. Usei `unzip`, `aapt2` e `apkanalyzer` sobre os recursos e, depois de instalar o JADX 1.5.6, decompilei o `base.apk` (24.519 arquivos; 424 métodos não decompilaram, como no estudo anterior).
- **O que isso significa:** os itens marcados [J] foram conferidos no código. Os marcados [N] ainda vêm só do estudo anterior. A seção "Conferido no código" mais abaixo diz o que bateu e o que diferiu.
- **Comparação com o T.A.R.K.A.S.:** feita por busca de termos e leitura de trechos, não por leitura completa. Onde digo "falta", o termo não apareceu no código; vale um olhar antes de implementar.

## O que os recursos mostram [R]

| Tipo | Quantidade | Observação |
|---|---|---|
| `anim` | 84 | quase todas de bibliotecas (AppCompat, Material, Navigation, Glance): fade, slide de sheet, caixa de seleção, barra indeterminada |
| `animator` | 38 | estados de botão, card e chip; FAB; transições de fragmento |
| `interpolator` | 26 | curvas do Material (padrão, enfatizada, desacelerada, acelerada) |
| `transition` | 2 | `move_image` e uma transição de elemento compartilhado entre telas |
| Lottie, GIF, vídeo | 0 | não há arquivo de animação pronto; o movimento é todo feito em código |

Poucas animações são próprias do app:
- subida e descida de tela de 300 ms com curva de desaceleração;
- fade de 50 ms na folha de compartilhar letra;
- uma rotação infinita de 500 ms (indicador de carregamento);
- a transição de elemento compartilhado, em que a capa passa de uma tela para a outra.

O código usa muito `android.animation` (581 símbolos). Não vi nomes de Compose; o código está ofuscado, então isso não prova que Compose não é usado.

**Conclusão:** o movimento que dá a sensação do app não está nos XML. Está no código do player, da folha e das letras, descrito no estudo anterior.

## Onde cada animação entra

Colunas: o que o Apple Music faz, o que o T.A.R.K.A.S. tem hoje, onde colocar e como saber que ficou certo.

### Prioridade alta

| # | Animação | Apple Music | T.A.R.K.A.S. hoje | Onde colocar | Verificação |
|---|---|---|---|---|---|
| 1 | Mini player e player ligados à fração do arrasto | todas as propriedades vêm da fração `f`: mini player some com `e^(-300 f)`, player entra com `1 + 0,5 ln f`, barra inferior sai com `1 - e^(-20 f)`, capa interpola de um retângulo ao outro [N], confirmado no código [J]: ver itens 28 a 32 | arrasto do player existe (`pull` em `MusicScreen`) e a abertura usa mola em `PlayerExpand.kt`; o mini player é `MiniPlayerBar` em `CommonComponents.kt` [T]. Não vi as propriedades derivadas de uma fração única | `PlayerExpand.kt` e `MiniPlayerBar`: uma fração só alimenta alpha do mini player, alpha do player, saída da barra e raio do canto | Soltar o dedo no meio do caminho: nenhum elemento pula; interromper e reverter parte da posição atual |
| 2 | Ignorar pequenos movimentos para baixo no topo de uma lista dentro do player | evita que um fling que bate no topo da fila ou da letra feche o player [N]. Confirmado [J]: o movimento é ignorado só se for menor que o limiar de toque do sistema, a lista está no topo e a folha não está em arrasto; vale para a fila e para as letras | o player arrasta de qualquer ponto da página [T]; não vi tratamento da rolagem interna | `MusicScreen` (fila) com `NestedScrollConnection` | Fling para cima em uma fila longa não fecha o player |
| 3 | Capa pequena com 0,95 durante o arrasto da barra de progresso | pausada 0,85, tocando 1,0, arrastando 0,95, 300 ms com desaceleração [N] | escala pausada/tocando e sombra existem (`PausedArtScale`) [T]; não vi o estado "arrastando" | `MusicScreen`, onde a escala da capa é calculada | Arrastar a barra: a capa vai a 0,95 e volta ao soltar |
| 4 | Fundo animado vindo da própria capa | três cópias da capa giram (períodos de 120, 90 e 70 s), saturação alta, véu escuro, blur forte sobre bitmap ~1/24 da tela, ~24 quadros/s, troca por crossfade de 1 s [N] | fundo é um gradiente vertical estático (`MusicScreen.kt:261`) [T] | `MusicScreen`, atrás da página. Restrição de identidade: acento no máximo ~5%, então baixa saturação e véu de Absolute Black forte | Medição de quadros no S23 com o efeito ligado; com "reduzir animações" o fundo fica estático |
| 5 | Proteção de desempenho para efeitos pesados | mede o tempo que o app gasta montando o fundo; se passar de 15 ms mais de 3 vezes seguidas, desliga a rotação. Existe também um modo de efeitos reduzidos (escala e saturação diferentes) e as durações das rotações são multiplicadas por um fator de acessibilidade [J] | não achei equivalente; `SurfaceStyle.kt` usa Haze [T] | função nova junto de `AppMotion` em `Motion.kt` | Teste que simula 3 quadros lentos e confere a troca para o modo estático |

### Prioridade média (dependem de uma função nova)

| # | Animação | Apple Music | T.A.R.K.A.S. hoje | Onde colocar | Verificação |
|---|---|---|---|---|---|
| 6 | Letras sincronizadas: linha ativa | a linha ativa entra 500 ms antes; a antecipação se adapta ao intervalo até a próxima linha (480-750 ms); palavras entram 100 ms antes [N] | não há letras. "Curiosidades" é outra coisa | tela nova de letras, chamada pelo player | Desvio de sincronia menor que 100 ms contra um LRC de referência |
| 7 | Letras: destaque palavra a palavra | máscara de gradiente de alpha com borda suave de 30 dp sobre o texto, não troca de cor [N] | não há | letras, com `drawWithContent` e `BlendMode.DstIn` | Captura de tela com a máscara no meio de uma palavra |
| 8 | Letras: rolagem em onda | rolagem de 750 ms com curva `(0,4; 0,1; 0; 1)`; as linhas abaixo atrasam em ~25, 44, 56 e 62 ms; linhas inativas a 0,98 sem blur [N] | `listEntrance` existe para entrada de listas, não para rolagem [T] | letras | Gravação de tela da troca de linha |
| 9 | Letras: pontos de intervalo instrumental | intervalo de 7 s ou mais mostra três pontos que se enchem e "respiram"; a saída termina 800 ms antes da voz [N] | não há | letras | Faixa com intervalo conhecido: pontos entram e saem no tempo |
| 10 | Letras: palavra longa com brilho e escala até 1,14 | só palavras de 1 s ou mais e até 7 letras, fora de CJK [N] | não há | letras, camada ADVANCED | Teste da regra de elegibilidade |
| 11 | Letras: alternância de lado em duetos | alterna o alinhamento a cada troca de cantor, respeitando RTL; grupo sempre no início; largura máxima de 85% [N] | não há | letras, só se a fonte trouxer o cantor | Pendente de fonte: LRC simples não traz cantor |

### Prioridade baixa ou condicional

| # | Animação | Apple Music | T.A.R.K.A.S. hoje | Onde colocar |
|---|---|---|---|---|
| 12 | Elemento compartilhado: a capa viaja da lista para a tela de detalhe | `move_image` e a transição compartilhada entre telas [R] | não achei `sharedElement` [T]; só o player abre a partir do cartão (`opensPlayer`) | quando houver tela de álbum ou playlist; hoje o ganho é pequeno |
| 13 | Esqueleto de carregamento | enquanto o cache responde e a rede atualiza (stale-while-revalidate) [N] | só em `HomeScreen.kt` [T] | `LibraryScreen` e a busca do Audius |
| 14 | Folha de compartilhar letra com fade de 50 ms | [R] | não se aplica sem letras | depois das letras |
| 15 | Deslocamento de latência por saída (Bluetooth 100-300 ms) | o app só aplica um offset extra no áudio Atmos [N] | não achei [T] | camada ADVANCED; medir com `AudioTrack.getTimestamp` |

## A capa que viaja de um lugar a outro [J]

É o tipo de animação que você descreveu: a capa do mini player vira a capa do player, saindo de onde está até onde a capa grande fica. Li o código que faz isso e descrevo com palavras próprias. Os números abaixo são do código, não de gravação de tela.

| # | Animação | Como o Apple Music faz | T.A.R.K.A.S. hoje | Onde colocar |
|---|---|---|---|---|
| 28 | **Mini player → player: a própria capa viaja** | Existe uma única capa grande. A cada quadro do arrasto a folha informa a fração `f` (0 recolhida, 1 aberta). A capa grande tem posição X e Y, largura, altura e raio do canto interpolados de forma linear entre o retângulo da capa do mini player (em `f` = 0) e o seu lugar final (em `f` = 1). O raio é dividido pela escala para o canto parecer do mesmo tamanho durante o crescimento. Não há curva própria: o "feeling" vem do dedo e da física da folha | `expandFromOrigin` (`PlayerExpand.kt`) faz um *container transform*: a página inteira é recortada por um retângulo arredondado que cresce do elemento tocado até a tela, pintado com a cor da capa (`PlayerOrigin.tint`), e o conteúdo aparece depois de ~12% do caminho. A capa não viaja: ela já está dentro da página que aparece. A abertura é por mola e por toque, sem seguir o dedo | `PlayerExpand.kt` e a capa em `MusicScreen`. A capa passa a ser um único elemento que lê os retângulos `PlayerOrigin.mini` e o da capa grande e se posiciona por interpolação. É o item 1 com um destino concreto |
| 29 | **O mini player some quase de imediato, sem crossfade** | Em `f` > 0 o conteúdo do mini player fica com opacidade `e^(-300 f)`: com 1% de arrasto já está em 5%. O resto do player sobe com `max(0, 1 + 0,5 ln f)` (zero até ~14% do arrasto, plena em 100%). Assim, no meio do caminho só a capa em trânsito é visível, e o olho a lê como o mesmo objeto. A elevação troca nas pontas: mini player 1 → 0 e folha 0 → 1 | o mini player faz crossfade com a página [T, não conferi o detalhe] | `MiniPlayerBar` e `expandFromOrigin`: a opacidade do mini player cai quase imediatamente e a da página sobe depois. Isso vale mesmo sem arrasto, na mola de abertura |
| 30 | **A capa chega ao tamanho final antes do resto** | Quando existe capa em vídeo ou animada, ela segue uma fração própria `f / f₀` (limitada a 1) que fecha antes de a folha abrir por completo. `f₀` sai da altura da capa e da altura recolhida da folha, e o raio dela é `1 − f/f₀`. A capa ainda tem elemento de reserva para o vídeo, com uma matriz que corrige a proporção quando o retângulo muda | não há capa animada | não aplicar a capa em vídeo. A ideia de dois canais, um mais rápido para a capa e outro para o resto, vale para o item 28: a capa pode terminar em ~70% da abertura |
| 31 | **Capa → miniatura quando abre letras, fila ou outra vista do player** | As três vistas (música, letras, fila) nomeiam três elementos de transição: contêiner da capa, imagem da capa e superfície do vídeo. Ao trocar de vista, o conjunto compartilhado muda posição, tamanho, raio do canto, opacidade e matriz do vídeo em 300 ms com curva `(0,2; 0,06; 0; 1)`. Em paralelo, uma capa flutuante vai de sua posição para o canto superior (miniatura) em 500 ms, curva `(0,25; 0,1; 0,25; 1)`, com o raio indo de 0 a 1 e a opacidade acompanhando. A vista que sai também faz fade de 500 ms | a fila e o EQ abrem como seções do player [T, não conferi se a capa encolhe] | `MusicScreen`, quando uma seção abre: a capa encolhe e sobe para o cabeçalho em vez de sumir. Precisa de uma mola de resposta ≤ 0,3 s, porque 500 ms passa do limite de 300 ms da identidade |
| 32 | **Regras de cuidado dessa transição** | (a) O ponto de partida é capturado uma só vez, na primeira atualização, e no fim a escala e o raio originais são restaurados. (b) Na primeira atualização depois da captura as opacidades não são aplicadas, para não piscar. (c) A superfície do vídeo só troca perto do zero, com histerese (0 ao subir, 0,01 ao descer), para não oscilar. (d) Se o retângulo ainda não tem largura ou altura, a rotina sai sem fazer nada | n/a | entram como testes do item 28: sem tamanho medido, nenhuma mudança; reverter no meio do caminho volta pelo mesmo caminho |

### O resto da tela também anda com a fração [J]

A mesma fração `f` move tudo em volta da capa. Isso é o que faz a abertura parecer uma coisa só.

| # | Animação | Como o Apple Music faz | T.A.R.K.A.S. hoje | Onde colocar |
|---|---|---|---|---|
| 33 | **Barra de abas sai rápido, por fórmula** | A barra desce `(1 − e^(−20 f))` vezes a sua altura (mais a folga inferior): com 15% do arrasto já saiu 95%. Sai antes de a capa chegar ao meio | `AnimatedVisibility` com fade de 150 ms ao sair e entrada por mola ao voltar (`MainActivity.kt:945-960`); o tempo é fixo e não acompanha a abertura do player | parte do item 1: a cápsula de abas usa a mesma fração do player. Sem arrasto, a mesma fórmula roda sobre o progresso da mola |
| 34 | **Barras do sistema acompanham** | A cor da barra de status ganha um véu de opacidade `e^(5 (f − 1))`, que só aparece perto do fim. A barra de navegação mistura para preto com `1 − e^(−20 f)`, limitado a 0,998. Ao abrir, os ícones do sistema viram claros; ao recolher, voltam ao que eram | não conferi a barra de status e a de navegação durante a abertura [não conferi] | `MainActivity`, onde as barras do sistema são configuradas. Cuidado no tema Claro: o player abre escuro, então os ícones precisam virar claros só enquanto ele está aberto |
| 35 | **Variante plana: a barra fica sob a folha** | Em telas largas a barra de abas permanece embaixo e o fundo, os ícones, o texto e o divisor dela se misturam com a cor do player por uma fração (metade de `f`). A área de página do player sobe com `(1 − f)` da própria altura e opacidade `max(0, 1 + 0,25 ln f)`. Com o player recolhido, a folha só captura toques fora da barra de abas | n/a: o player cobre a barra | só vale se o T.A.R.K.A.S. ganhar layout para tablet ou dobrável |
| 36 | **Porta de toque no cabeçalho sobre a lista** | Um toque sobre o cabeçalho da fila ou das letras fica retido: os eventos esperam e só vão para a lista depois de passar o limiar de toque; sem movimento, vira clique; segurar libera tudo. Evita que o gesto de arrastar a folha e o de rolar a lista briguem | não conferi [não conferi] | `QueueList.kt` e `MusicScreen`, junto do item 2 |

Onde o Apple Music **não** usa isso, pelo que li: a abertura de álbum ou playlist a partir de uma grade. Há o recurso `move_image`, mas não achei uso dele no código Java; só o vídeo em tela cheia abre com fade de atividade. Então o item 12 continua sendo de baixa prioridade.

Como verificar o item 28: gravar a tela abrindo o player devagar e conferir quadro a quadro que a capa nunca está duas vezes na tela; interromper em ~40% e reverter; repetir com o arrasto rápido e com o toque.

## Mais animações encontradas no código [J]

Só 34 classes do app usam as animações de View do Android (19 no player, 10 em views comuns, 3 em coleção, 1 na tela inicial, 1 na biblioteca). As durações mais usadas no app são 250 ms (curta), 500 ms (média), 750 ms (longa) e 1000 ms (troca de capa). Nomes de Compose não aparecem por causa da ofuscação, então pode haver mais movimento que esta busca não enxerga.

| # | Animação | O que o app faz | T.A.R.K.A.S. hoje | Onde colocar |
|---|---|---|---|---|
| 16 | Linha ativa da letra cresce | a linha ativa aumenta até 1,2 em 750 ms, com o ponto fixo na borda inicial do texto e no meio da altura (respeita RTL), curva suave `(0,25; 0,1; 0,25; 1)` | não há letras | tela de letras. Duração acima do limite de 300 ms da identidade; adaptar para mola de resposta ≤ 0,3 s e documentar |
| 17 | Pontos do intervalo instrumental | os três pontos aparecem em sequência, 50 ms entre cada, com fade de 750 ms acelerado; ao sair encolhem a 0,5 e somem em 250 ms; enquanto ficam, pulsam entre 1 e 1,2 em repetição | não há | letras (itens 9 e 6) |
| 18 | Barras dançantes | indicador de barras que sobe e desce, 270 ms por ciclo, curva linear, vai e volta sem parar | não conferi se há indicador de faixa em reprodução na lista | linha da faixa atual em `LibraryScreen` e `QueueList`; parar quando pausado ou com movimento reduzido |
| 19 | Anel de progresso | o arco anima 750 ms com curva cíclica ao mudar de valor | não conferi | downloads futuros ou análise de loudness por faixa |
| 20 | Loader que some | o indicador de carregamento desaparece com fade de 400 ms desacelerado | `Crossfade` é muito usado [T] | já coberto; só padronizar a duração |
| 21 | Folha do player: entra devagar, sai rápido | a entrada usa a duração longa do sistema com desaceleração e a saída usa a média com aceleração; abrir a fila desacelera e fechar acelera | `Motion.kt` tem mola crítica e fade [T]; não vi a regra "saída mais curta que a entrada" aplicada em todo lugar | `screenTransition`, `subPageTransition` e a fila: sair em 60-70% do tempo de entrar |
| 22 | Troca entre capa e vídeo | a imagem da capa e uma vista de vídeo trocam de lugar com fades de curvas opostas (uma desacelera, a outra acelera) | sem capa animada | não aplicar |
| 23 | Controle de atenuação vocal | a altura do controle anima 750 ms com curva `(1; 0; 0,35; 1)`; o valor do slider vai ao novo ponto em 850 ms com `(0,75; 0; 0,25; 1)` | EQ tem sliders [T]; não conferi se as bandas animam ao trocar de preset | `PresetPanel` e `EqGraph`: ao escolher um preset, as bandas deslizam até o novo valor |
| 24 | Bolha da rolagem rápida | a bolha com a letra cresce de 0 a 1 em 100 ms | existe faixa A-Z [T]; não conferi se a bolha anima | `LibraryParts.kt` |
| 25 | Cabeçalho da playlist | o cabeçalho muda de altura e de recuo em 250 ms com a curva `(0,25; 0,1; 0,25; 1)` e há um ponto de ultrapassagem controlada | não conferi | cabeçalho da biblioteca ao rolar |
| 26 | Zoom e deslocamento de imagem | escala e posição X/Y animam 500 ms com aceleração | não há | baixa prioridade |
| 27 | Cartão da tela inicial que expira | o cartão cresce até 1,1 em 750 ms com a mesma curva suave (li só o começo da rotina) | `HomeScreen.kt` tem entrada e esqueleto [T] | só se houver cartões com prazo |

Regra que atravessa vários itens: **a saída é mais curta que a entrada** e as curvas se espelham (desacelera ao entrar, acelera ao sair). Isso combina com o princípio de consistência espacial da skill `tal-apple-design`.

## Funções que as animações pedem

1. **Fração única de arrasto:** um objeto que recebe a posição do dedo e devolve alpha, escala, translação e raio por curva. Base para os itens 1 e 2. Teste unitário das curvas nos extremos e em valores intermediários.
2. **Monitor de tempo de quadro:** conta quadros lentos seguidos e expõe "efeitos pesados permitidos". Base para o item 5; também protege o Haze e o fundo animado.
3. **Fonte de letras:** cliente do LRCLIB (API pública) com cache por faixa, leitura de LRC e do LRC com tempo por palavra, e fallback para texto simples. Base para os itens 6 a 11. Falta decidir a política de privacidade: o título e o artista passam a sair do aparelho para o LRCLIB, e os textos legais precisam dizer isso.
4. **Agendador pelo próximo evento:** em vez de recompor a cada quadro, calcula o próximo instante em que algo muda e agenda só esse. Reduz o gasto de bateria das letras.
5. **Cor e luminância da capa:** para escolher saturação e véu do fundo dentro da regra de 5% de acento.
6. **Fade-out ao fim do timer de sono:** o Apple Music não tem; o estudo anterior já apontava como melhoria.

## Ordem sugerida

1. Itens 1, 2 e 3 (movimento do player): mudam código que já existe e não precisam de dados novos.
2. Itens 4 e 5 juntos: o fundo não deve entrar sem o monitor de quadros.
3. Letras (itens 6 a 11) como uma entrega só, com a política de privacidade atualizada.
4. Itens 12 a 15 quando houver motivo concreto.

## Conferido no código (JADX 1.5.6) [J]

Bateu com o estudo anterior:
- **Mini player e player (item 1):** o alpha do player é `max(0, 1 + 0,5 ln f)` e o do mini player é `e^(-300 f)`, ambos calculados a cada passo do arrasto. Quando `f` chega a 1 o mini player fica invisível e a elevação das duas camadas troca; quando chega a 0 o player some.
- **Capa por estado (item 3):** pausada 0,85, tocando 1,0 e **durante a busca 0,95**. Escala em X e Y e a elevação da sombra animam juntas por 300 ms com desaceleração. Se a tela ainda não está visível, o valor é aplicado direto, sem animar.
- **Fundo (item 4):** existe uma view própria de fundo para o player. Ela usa três cópias da capa girando em 120, 90 e 70 s, saturação 2,5 e uma troca de capa de 1 s.
- **Rolagem de letras (item 8):** 750 ms.
- **Palavra longa (item 10):** o fator 1,14 aparece no cálculo de escala das letras.

Diferiu ou não foi confirmado:
- **Proteção de desempenho (item 5):** o que ela mede é o tempo de montar o fundo, não o tempo de quadro. A regra é mais de 3 vezes seguidas acima de 15 ms. O estudo anterior descrevia como "quadros lentos".
- **Movimento reduzido:** o fator de 2,1 vezes não foi reconferido; o código multiplica as durações por um fator vindo das configurações do sistema.
- **Escala de 0,98 nas linhas inativas (item 8):** não achei esse valor nas classes de letras. Segue como [N].
- **Curva `(0,4; 0,1; 0; 1)`:** existe uma curva com esses valores numa classe utilitária, mas não confirmei que é a da rolagem das letras.
- **Antecipações de 500 ms, 100 ms e a onda de atrasos (itens 6 e 8):** não conferidas; seguem como [N].

## Pendências

- Conferir no código as antecipações das letras, a onda de atrasos, os pontos de intervalo e a regra de alternância em duetos.
- Nada foi implementado, testado ou medido no T.A.R.K.A.S. As verificações acima são o critério para dizer que cada item está pronto.

## Estado da implementação no T.A.R.K.A.S.

| Itens | Estado |
|---|---|
| 1, 3, 28, 29, 33 (fração única, capa que viaja, escala por estado, mini player e barra de abas saindo rápido) | Código escrito, compila, `PlayerTravelTest` passa. **Não verificado em aparelho ou emulador.** |
| 18 (barras de equalizador na faixa tocando), 24 (bolha A-Z de 0 a 1 em 100 ms) | Código escrito e compila. As barras foram vistas animando no emulador; a bolha **não** foi vista. |
| 21 | Já atendido: as saídas em `Motion.kt` (150 ms) são mais curtas que as entradas (200 ms ou mais). |
| 2, 36 | Sem efeito hoje: o player não tem lista rolável dentro dele (a fila fica na Biblioteca). Voltam quando entrarem letras ou fila no player. |
| 23 | A curva do EQ já faz morph ao trocar de predefinição (`EqGraph.kt`); os controles das bandas não deslizam. Pendente. |
| 4, 5 (fundo animado da capa e monitor de quadros) | Código escrito, compila, `FrameGuardTest` passa. Visto no emulador (parado, sem bordas duras); **S23 (release local, tocando com capa): 588 quadros, 0 janky, p99 11 ms, incluindo a abertura. A abertura em si não foi filmada no S23; "reduzir animações" não foi testado.** Sem "reduzir animações" ou com o monitor desligado, o fundo fica parado. |
| Letras e itens que pedem dados novos (6–11, 16, 17) | Fora do escopo por enquanto. |

## Fluidez e taxa de atualização (S23, 2026-11-04)

- O app pede a maior taxa do painel (`RefreshRate.requestHighest`). O S23 estava preso em 60 Hz pela Economia de bateria; o modo 120 Hz **não foi verificado**.
- O fundo animado da capa não tem mais limite de 24 fps; o `FrameGuard` usa 1,5 quadro da taxa real do painel como limite.
- Troca de abas, 20 trocas em 60 Hz (gfxinfo): antes 3,24% de quadros travados e 38 comandos de desenho lentos; depois de trocar fade+escala por fade puro, 1,82% e 5. p99 ainda 53 ms: pico de composição da primeira entrada de página, pendente.
- Rolagem de lista: 0% travados. Abrir/fechar o player: menos de 1%.

## Abertura do player: a página sobe com a folha (S23, 2026-11-04)

- Problema visto na gravação: a abertura era uma janela retangular crescendo sobre uma página já montada no lugar final, então título e controles apareciam num quadro pequeno, sobrepostos à capa.
- Correção: a página em volta da capa (alça, título, barra, controles) sobe junto com a borda superior da folha, `translationY = (1 - p) * topo de origem`, mais o fade por `PlayerTravel.reveal`. A capa continua viajando pelo próprio canal. Gravação do S23 conferida quadro a quadro: o conteúdo entra de baixo com a folha e não há mais sobreposição. Fechar por voltar e por arrasto filmado no S23: a folha encolhe levando a página e a capa pousa no mini player. Abrir e mandar fechar aos 120-150 ms (3 vezes por voltar, 1 por arrasto) termina sempre na tela inicial com o mini player intacto, sem travar; **o meio da animação não foi filmado**.
- Fim do fechamento (S23): a folha era removida de uma vez quando a mola terminava, o que dava um corte seco para o mini player. Agora a folha dissolve (alpha com smoothstep de p = 0,35 até 0) enquanto a capa pousa, e o mini player começa a aparecer 80 ms antes (atraso de 60 ms em vez de 140 ms). Filmado quadro a quadro: a folha some em cerca de 8 quadros, com o mini player já visível por baixo.
- Duração (S23): a mola de abertura estava em rigidez 300 (assenta em ~0,26 s), rápida demais para acompanhar. Passou para rigidez 90, amortecimento 0,9 (assenta em ~0,5 s). Gravação a 30 fps: a abertura leva cerca de 14 quadros, com a folha, a capa e o conteúdo visíveis em cada etapa. É um reposicionamento grande, acima dos 300 ms da identidade, e a exceção está comentada em `Motion.ExpandStiffness`. O fechamento usa a mesma mola e não foi refilmado nessa velocidade.

## Encolher o player inteiro no mini player (2026-11-04)

- Capa, bloco título/artista, glifo de play/pause e botão de próxima viajam até os equivalentes do mini player (`PlayerOrigin.miniCover/miniTitle/miniPlay/miniNext`), pelo mesmo progresso que encolhe a folha; na abertura fazem o caminho inverso. Só quando o player abre do mini ou fecha nele (`PlayerOrigin.viaMini`); abrindo de uma linha ou com movimento reduzido eles aparecem com a página (`PlayerTravel.reveal`).
- O resto (anterior, favorito, menu, barra de progresso, volume, linha de baixo) some pelo `reveal` e sobe com a borda da folha.
- Os itens que viajam não podem ter ancestral que se mova: o retângulo de repouso é medido na janela. Por isso a translação da folha ficou em cada item que não viaja, e não na coluna.
- Dissolução final da folha em p < 0,18 (antes 0,35): os itens pousam a poucos pixels do mini antes de sumir, sem imagem dupla.
- Capa do mini player: raio 10 dp (`Shapes.s`, antes 6 dp), o mesmo ponto de partida do raio que cresce até 14 dp na capa grande.
- Verificado no S23 (release, 60 Hz): fechamento por Voltar, quadro a quadro. Pendente: arrastar para fechar, abertura filmada, 120 Hz, movimento reduzido, faixa sem capa.
