# T.A.R.K.A.S. 1.15 (16): varredura de UI/UX no aparelho

- **Data:** 2026-11-12
- **Aparelho:** Samsung S23 (SM-S911B), 1080x2340, 1 dp = 2,625 px, sistema em pt-BR
- **Pacote:** `com.aistudio.tarkas.app`
- **Método:** adb (`input`, `uiautomator dump`, `screencap`), medição de pixels com ffmpeg. Nenhum código foi alterado, nada foi compilado nem commitado.
- **Capturas:** nesta pasta (`NN-nome.png`; `Lxx` = tema claro, `Dxx` = paisagem no tema escuro; versões em meia resolução em `small/`).
- **Tema:** o tema é um ajuste do app (Ajustes > Tema), não segue `uimode night`. Valor original do dono: **Preto (AMOLED)**. "Escuro" abaixo significa esse tema; "claro" foi ligado temporariamente e desfeito.

## Resumo

| Severidade | Confirmados |
|---|---|
| Alta | 0 |
| Média | 15 |
| Baixa | 17 |
| **Total** | **32** |

Nenhum crash nem ANR: o buffer `crash` do logcat ficou vazio e não há `FATAL EXCEPTION` do pacote. Além disso, 11 suspeitas/observações (seção própria) e a lista do que não foi testado.

Os cinco mais relevantes:

1. **F30** Paisagem: mini player + barra de abas cobrem ~35% da altura; Biblioteca > Músicas abre sem nenhuma linha visível e o estado vazio de Favoritos fica escondido.
2. **F25** Sobre e Novidades: o botão de voltar é desenhado dentro da barra de status (inset errado).
3. **F1 + F20** Áudio: rótulos cortados ("Pré-am", "Ganh", "Equalizad"), valores de ganho com o último dígito cortado e cores de banda fora da paleta.
4. **F23 + F24** Assistente: a resposta nova fica fora da vista após enviar; "Como ativar" mostra instrução de desenvolvedor (`GEMINI_API_KEY` no `.env`) que o usuário não pode cumprir.
5. **F29** Paisagem: o player cheio não cobre a faixa do recorte da câmera (98 px) e deixa a tela de trás aparecer.

---

## Bugs confirmados

Formato: **id · tela · orientação/tema · severidade · área**. Passos, o que aconteceu, o esperado, captura.

### Áudio / Equalizador

**F1 · Áudio (console) · retrato, escuro e claro · média · equalizador**
- Passos: abrir a aba Áudio.
- Aconteceu: "Pré-amp" aparece "Pré-am", "Ganho" aparece "Ganh", o rótulo da chave "Equalizador" aparece "Equalizad"; no cartão da banda o rótulo "Freq" é cortado e o valor da frequência não aparece.
- Esperado: rótulos inteiros e valor da frequência legível.
- Capturas: `49`, `50`, `70`, `L03-audio.png`.

**F2 · Áudio (gráfico) · retrato, escuro · baixa · equalizador**
- Passos: pôr uma banda em +15 dB (entrada numérica).
- Aconteceu: o eixo do gráfico vai de -12 a +12 dB, mas o ganho aceita ±15; o pico em +15 é cortado no topo.
- Esperado: eixo e faixa de ganho iguais.
- Captura: `54`.

**F18 · Seletor de presets / AutoEq · retrato, escuro · média · equalizador**
- Passos: Áudio > botão de preset > buscar um texto sem resultado (ex.: "zzzz").
- Aconteceu: a folha encolhe para só o campo de busca e os chips; não há mensagem de "nenhum resultado".
- Esperado: estado vazio dizendo o que aconteceu e o que fazer.
- Capturas: `67-autoeq-empty.png`, `68`.

**F19 · Seletor AutoEq e Ajustes · retrato · baixa · equalizador/geral**
- Aconteceu: tipos de fone misturam idiomas ("Over-ear" ao lado de "Intra-auricular"); em Ajustes, "Liquid glass" em inglês numa interface em PT.
- Esperado: um idioma por interface.
- Capturas: `66-autoeq.png`, `82-ajustes.png`.

**F20 · Áudio, modo Gráfico · retrato, escuro · média · equalizador**
- Passos: Áudio > modo Gráfico > preset "Pop".
- Aconteceu: os valores de ganho sob as barras têm o último dígito cortado ("-1.0", "+1.5"); cada banda e cada ponto do gráfico usa uma cor diferente (roxo, verde, laranja, amarelo, azul), todas fora da paleta.
- Esperado: valores inteiros; só as cores da identidade (base + Ice/Deep Teal).
- Capturas: `72-pop.png` (e recorte), `75-bands-scrolled.png`.

**F21 · Áudio, seletor Gráfico/Paramétrico · retrato, claro e escuro · média · equalizador**
- Aconteceu: o segmento "Paramétrico" tem 34 dp de altura (mínimo 48 dp) e encosta no mini player; no tema claro o rótulo selecionado tem contraste ≈ 2,1:1 (`#5B979B` sobre ≈ `#C3D2D4`).
- Esperado: alvo ≥ 48 dp, sem sobreposição, contraste ≥ 4,5:1.
- Capturas: `L03-audio.png` (recorte), `70`, `72`.

**F22 · Áudio (console) · retrato, claro · baixa · equalizador**
- Aconteceu: os polegares dos sliders verticais são lavanda `#E6E0E9` (padrão do Material), fora da paleta.
- Esperado: cor da identidade.
- Captura: `L03-audio.png`.

**F31 · Áudio (console) · paisagem, claro e escuro · média · equalizador**
- Passos: girar o aparelho com a aba Áudio aberta.
- Aconteceu: não há layout de paisagem; sobram ~380 px de área útil, os sliders (520 px) nunca aparecem inteiros e o cartão da banda fica cortado. Dá para rolar, mas não para operar com conforto.
- Esperado: layout em duas colunas ou controles que caibam na altura.
- Capturas: `L09-audio-land.png`, `L10-audio-land2.png`.

### Player

**F3 · Player cheio · retrato, escuro · média · player**
- Passos: no player cheio, executar uma ação que mostra snackbar (ex.: "Adicionar à fila").
- Aconteceu: todo o layout do player sobe ~147 px enquanto a snackbar está visível e volta depois.
- Esperado: a snackbar flutua sobre o conteúdo sem deslocar o layout.
- Captura: `29`.

**F6 · Player cheio sem capa · retrato, escuro e claro · média · player**
- Aconteceu: o fundo é azulado (`#30363C` / `#40464B`), fora da paleta, e continua escuro com o tema claro ligado; a capa-padrão aparece como retângulos aninhados.
- Esperado: fundo derivado das cores-base e coerente com o tema.
- Capturas: `21`, `24`, `L06-player.png`, `92-state.png`.

**F8 · Mini player · retrato, escuro · baixa · player**
- Aconteceu: a miniatura da capa é cortada pela borda arredondada da pílula.
- Esperado: miniatura inteira dentro da pílula.
- Captura: `19` (recorte).

**F11 · Player cheio · acessibilidade · baixa · player**
- Aconteceu: Favorito, Aleatório, Repetir e Timer não expõem estado (ligado/desligado) na árvore de acessibilidade.
- Esperado: `stateDescription` ou papel de alternância com estado.
- Captura: dump de `12`.

**F14 · Detalhes da faixa · retrato, escuro · baixa · player**
- Aconteceu: o rótulo "Sobre a faixa" tem recuo diferente dos demais itens da folha.
- Capturas: `14`, `15`.

**F29 · Player cheio · paisagem, claro e escuro · média · player**
- Passos: abrir o player cheio e girar para paisagem.
- Aconteceu: uma faixa de 98 px à esquerda (inset do recorte da câmera) não é coberta pelo player e mostra a tela de trás (clara no tema claro, preta no escuro).
- Esperado: o fundo do player preenche a tela inteira; só o conteúdo respeita o inset.
- Capturas: `L07-player-land.png`, `D05-player-land.png`.

**F33 · Timer de dormir · paisagem, escuro · baixa · player**
- Aconteceu: o diálogo chega à borda inferior; "45 minutos" aparece pela metade, "60" e "90" ficam escondidos, o título rola para fora e nada indica que há rolagem.
- Esperado: diálogo com altura limitada, título fixo e indicação de rolagem.
- Captura: `D06-timer-land.png`.

### Biblioteca

**F4 · Biblioteca > Músicas · retrato, escuro e claro · média · biblioteca**
- Passos: tentar abrir o menu de uma faixa na lista (procurar "Mais opções", toque longo).
- Aconteceu: as linhas não têm botão de menu e o toque longo só toca a faixa. Os estados vazios de Fila e Playlists mandam usar "o menu de uma música", que só existe no player.
- Esperado: menu da faixa acessível na lista, ou texto que aponte para o lugar certo.
- Capturas: `05`, `20`, `42`, `43`, `L05-lib.png`, `93-fila.png`.

**F9 · Biblioteca > Músicas, faixa A-Z · retrato · média · biblioteca**
- Aconteceu: cada letra tem alvo de ~12x16 dp (mínimo 48 dp), a faixa não existe na árvore de acessibilidade e os divisores das linhas passam por dentro das letras.
- Esperado: alvo maior (ou arrasto contínuo), descrição acessível, divisores terminando antes da faixa.
- Capturas: `05`, `94-musicas.png`.

**F15 · Biblioteca > Fila, menu · retrato, escuro · baixa · biblioteca**
- Aconteceu: itens desabilitados do menu mantêm o ícone em Ice, como se estivessem ativos.
- Esperado: ícone desabilitado em cinza.
- Captura: `32`.

**F30 · Biblioteca · paisagem, claro e escuro · média · biblioteca**
- Passos: girar para paisagem na Biblioteca.
- Aconteceu: mini player + barra de abas cobrem ~35% da altura. Em Músicas nenhuma linha da lista aparece ao entrar (é preciso rolar o cabeçalho para fora); em Favoritos o texto do estado vazio fica atrás do mini player (só o coração aparece); a faixa A-Z fica espremida em "# A D H O P" ao lado da busca.
- Esperado: conteúdo visível ao entrar; barras compactas ou laterais em paisagem.
- Capturas: `L08-lib-land.png`, `D02-lib-land.png`, `D03-lib-land-scrolled.png`.

**F34 · Biblioteca > Músicas, ordem "Mais tocadas" · retrato, escuro · baixa · biblioteca**
- Passos: tocar no chip "Mais tocadas".
- Aconteceu: o chip selecionado continua cortado na borda direita ("Mais to") porque a fileira não rola até ele; a faixa A-Z continua na tela, embora a ordem não seja alfabética.
- Esperado: rolar a fileira até o chip selecionado; esconder a faixa A-Z em ordens não alfabéticas.
- Captura: `95-maistocadas.png`.

### Início

**F16 · Início · retrato, escuro · baixa · geral**
- Aconteceu: ao rolar, o conteúdo é cortado em linha reta sob o cabeçalho, sem esmaecimento nem superfície.
- Capturas: `02`, `44`.

### Assistente

**F23 · Assistente · retrato, escuro · média · assistente**
- Passos: enviar "ajuda" e depois "diagnostico".
- Aconteceu: a resposta mais nova fica fora da vista (só ~3 px acima dos chips); a lista não rola até a última mensagem. Trocar de aba e voltar corrige.
- Esperado: rolar até a última mensagem a cada envio/resposta.
- Capturas: `79-assist-ajuda.png`, `80-assist-diag.png` (compare com `L04-assist.png`).

**F24 · Assistente > "Como ativar" · retrato · média · assistente**
- Aconteceu: o texto diz "A IA online precisa de uma chave do Gemini (GEMINI_API_KEY) no arquivo .env, incluída ao compilar o app. Não há ajuste para isso dentro do app." É uma opção voltada ao usuário que ele não tem como cumprir; Ajustes também mostra "IA online (Gemini): Não configurada".
- Esperado: esconder a opção quando a chave não existe na build, ou oferecer um caminho que o usuário possa seguir.
- Captura: `81-como-ativar.png`.

### Ajustes e geral

**F5 · Diálogos (AlertDialog) · retrato · baixa · geral**
- Aconteceu: os diálogos ocupam a largura inteira da tela, sem margem lateral.
- Capturas: `11`, `16`, `51`.

**F10 · Player cheio aberto · acessibilidade · baixa · geral**
- Aconteceu: a tela de trás continua na árvore de acessibilidade com nós clicáveis; um leitor de tela navega por conteúdo coberto.
- Capturas: dumps de `12` e `L07`.

**F12 · Textos · baixa · geral**
- Aconteceu: o mesmo recurso tem dois nomes: "Timer de sono" e "Timer de dormir"; "Áudio espacial" e "Tarkas Spatial". O nome do produto aparece como "Tarkas" ("sessão interna do Tarkas"), sem os pontos de T.A.R.K.A.S.
- Capturas: `59`, `60`, `80`.

**F13 · Folhas inferiores · acessibilidade · baixa · geral**
- Aconteceu: as folhas expõem os textos-padrão "Fechar planilha" e "Alça de arrastar".
- Esperado: descrições próprias ("Fechar", "Arrastar para fechar").

**F25 · Ajustes > Sobre e Sobre > Novidades · retrato · média · geral**
- Aconteceu: o botão de voltar ("< Ajustes" / "< Sobre") ocupa y = 0–126 px, dentro da barra de status, sobre relógio e ícones do sistema.
- Esperado: cabeçalho abaixo do inset da barra de status.
- Capturas: `88-sobre.png`, `89-novidades.png`.

**F26 · Ajustes · retrato · baixa · geral**
- Passos: rolar Ajustes até o fim, abrir Sobre, voltar.
- Aconteceu: Ajustes volta ao topo.
- Esperado: manter a posição de rolagem.

**F27 · Formatação de números · baixa · geral**
- Aconteceu: Ajustes usa vírgula ("48,0 kHz", "1,8 MB"); Áudio usa ponto ("48.0 kHz", "0.0 dB", "-0.5 dB"). O seletor diz "8850 fones medidos" e o changelog "8.849 perfis".
- Esperado: um formato por idioma e um número só.
- Capturas: `66`, `83`, `89`.

**F28 · Ajustes > "Apagar todos os dados" · retrato, escuro · baixa · geral**
- Aconteceu: o texto é vermelho `#B54D4D`, fora da paleta.
- Captura: `87`.

**F32 · Início com "Barras flutuantes = Liquid glass" · paisagem, claro · média · geral**
- Aconteceu: mini player e barra de abas sobre capas escuras ficam ilegíveis (texto preto e ícones cinza sobre imagem escura borrada).
- Esperado: véu claro suficiente sob o vidro para manter contraste AA.
- Captura: `L11-home-land.png`.

---

## Suspeitas e observações (não confirmadas como bug)

- **S1 (player):** deslizar o carrossel de capas com a música pausada inicia a reprodução (`23`, `24`). Pode ser intencional.
- **S2 (biblioteca):** em "Mais tocadas" só uma faixa mostrou contagem ("1 reprodução"), embora outras tenham sido tocadas por alguns segundos durante a varredura. O limiar que conta uma reprodução não é visível na interface (`95`).
- **S3 (biblioteca):** na ordem por artista, "Artista desconhecido" entra na letra A; notas de voz do WhatsApp aparecem como músicas.
- **S4 (player):** a transição de abrir o player dura ~0,55 s e a de fechar ~0,6 s (medido por quadros de gravação), acima dos 150–300 ms da identidade (`22a`, `22b`).
- **S5 (equalizador):** a entrada numérica limita 99 a +15,0 sem avisar.
- **S6 (equalizador):** Gráfico e Paramétrico guardam curvas separadas; com o preset "Pop", trocar para Paramétrico mostra uma banda plana enquanto o rótulo continua "Pop •" (`73`).
- **S7 (equalizador):** depois de aplicar um perfil AutoEq o console não diz qual fone está ativo (só "A correção do fone está ligada…") e o botão de preset continua "Flat" (`70`).
- **S8 (equalizador):** brilho verde (`#072512`) no topo da página avançada em uma única captura (`60`); não se repetiu.
- **S9 (geral):** rótulo do app no diálogo de permissão do sistema lido como "T.A.R.K.A.S" sem o ponto final (`04`); não foi reconferido.
- **S10 (identidade):** o acento ocupa bem mais que ~5% em várias telas: botões de largura inteira, preenchimento dos sliders, cartão de banda em teal, balões do usuário no chat, linhas de status em Ice.
- **S11 (textos e detalhes):** "Sem EQ · tocar volta"; "Comandos Disponíveis:" em maiúsculas de título; "Faixa tocando" com a música pausada; Ajustes diz "toque em Ajustes avançados", que fica dentro do menu ⋮; a linha Permissões não lista a permissão de mídia; campo de busca de presets com 46 dp; chip "Fones (AutoEq)" cortado na borda; filtro do seletor volta para "Todas" a cada abertura; dois títulos grandes empilhados ("Áudio" + "Ajustes avançados"); "Curiosidade da faixa" desabilitado sem explicação; arrastar na fila não levanta o item; teclado continua aberto após "Limpar busca"; o botão de fila fecha o player e pula para Biblioteca > Fila; a Biblioteca não tem atalho para Ajustes.

Nota: o fundo `#000000` (em vez de `#080808`) vem do tema "Preto (AMOLED)" escolhido pelo dono; é escolha de projeto, não bug.

## Funcionou como esperado

Fluxo de permissão de mídia; salto pela faixa A-Z; timer de dormir; fila (adicionar, remover, deslizar, desfazer, arrastar, confirmação em "Limpar"); busca; chaves de alternância; mensagem ao negar captura do medidor ("Permissão de captura negada. O medidor continua desligado."); crossfade (chave e duração); AutoEq (buscar, aplicar, remover); comparar sem EQ; verificação de atualização (só visualização: "Atualizado"); tema claro usa Deep Teal (nenhum Ice sobre fundo claro encontrado); rotação mantém estado (posição do player, rolagem da biblioteca, folha aberta); comando desconhecido no Assistente tem resposta clara; Sobre traz "Developed by Totally Accurate Labs."; ordem "Mais tocadas" mostra a contagem na linha.

## Não testado, e por quê

- **Onboarding:** não apareceu; exigiria limpar os dados do app (proibido).
- **Interface em inglês:** exigiria mudar o idioma do sistema ou do app.
- **Entrada por voz no Assistente:** exigiria conceder permissão de microfone.
- **IA online (Gemini):** não configurada nesta build.
- **Controles na notificação:** a permissão de notificação não foi concedida.
- **Instalar/baixar atualização, exportar dados, "Limpar cache", "Apagar todos os dados":** fora do escopo por segurança.
- **Modos de EQ por ADB/Root e efeito do áudio espacial:** não ligados.
- **Fones com fio/Bluetooth:** nenhuma saída externa conectada.
- **"Adicionar banda" e arrastar pontos no gráfico (modo paramétrico):** não exercitados.
- **Tema claro:** só as telas principais (Início, Biblioteca, Áudio, Assistente, Ajustes, player), não todas as folhas e diálogos.
- **Tablet / tela grande:** sem aparelho.
- **Logcat:** o buffer principal rodou durante a sessão; a ausência de crash se apoia no buffer `crash` (vazio) e no fato de o app nunca ter fechado sozinho.

## Efeitos colaterais da varredura no aparelho

- Permissão de mídia concedida quando o app pediu; a de notificação foi recusada.
- Algumas faixas foram tocadas por poucos segundos (uma delas por acidente no fim); a contagem de reproduções pode ter subido em 1 para "2026 (Composição: Cícero Pereira)". A faixa atual do player ficou em AUD-20240511-WA0137, pausada em 0:16.
- O histórico do Assistente ficou com três mensagens de teste ("ajuda", "xyzzy", "diagnostico").
- Verificação de atualização executada uma vez (carimbo de hora atualizado); nada baixado.
- Restaurado e conferido: tema Preto (AMOLED), ordem "Título", fila vazia, crossfade desligado, correção AutoEq removida, preset Flat em modo Paramétrico, modo noturno do sistema em `auto`, `accelerometer_rotation` 0, `user_rotation` 0, volume de mídia 5/15, reprodução pausada.
