# T.A.R.K.A.S. Windows: pedido 2 (dono, 2026-11-18)

Referências: `docs/windows-ref/painel-referencia.mp4` (23 s, 30 fps) e os quadros `docs/windows-ref/quadros/q01..q12.png`; captura de Início estilo Spotify (protótipo puro, só para a estrutura: atalhos em grade 4x2, "Feito para", "Novos lançamentos", painel lateral direito com artista).

## 1. APIs gratuitas no Windows (win-biblioteca)
Todas as fontes grátis do Android (`biblioteca/funcoes/catalogo/*`: Audius, Jamendo, MusicBrainz) ligadas no Windows, com dados reais, busca, reprodução por stream e erros tratados. Chaves nunca vão no instalador.

## 2. Início (win-biblioteca)
Seguir a estrutura da captura, na identidade T.A.L. (cores de `MaterialTheme.colorScheme`, grade `Grid`): atalhos 4x2, carrosséis horizontais "Feito para você" / "Novos lançamentos" / álbuns e músicas das APIs, "Mostrar tudo", painel direito com a faixa atual e sobre o artista. Rolagem horizontal com roda + arrasto.

## 3. Painel e movimento (win-player)
Do vídeo: tocar na capa do mini player (barra inferior) abre um painel que desliza e ocupa a janela, com capa grande à esquerda, controles/volume embaixo dela e, à direita, abas Fila / Letras (letra com linha atual em destaque e as vizinhas apagadas, rolagem automática). Voltar por botão "Back" e Esc. Também vale trazer do vídeo: fundo desfocado da capa com cor dominante, grade de capas em mosaico 2x2 para playlists, barra lateral de ícones, botão "+" flutuante. Movimento: 150–300 ms com desaceleração, alvo 60–120 fps (nada de recomposição pesada durante a animação; usar `graphicsLayer`, medir quadros).

## 4. Nitidez e animações do mobile (win-base + todas)
- win-base: causa da imagem "480p": verificar escala de DPI (manifesto DPI-aware/`sun.java2d.uiScale`/Skiko), capas decodificadas em resolução baixa, `Image` sem `FilterQuality.High`, janela inicial pequena. Corrigir na raiz e medir em 1920x1080 e 4K com escala 100/125/150%.
- Todas: reaproveitar do mobile a rolagem de álbum (`CoverCarousel`, `PlayerTravel`, `PlayerExpand`), interações e curvas de movimento, cada sessão na sua área.

Regras: `docs/WINDOWS-SESSOES.md`. Sem commit, sem captura de tela inteira sem perguntar, build só por `gradle.sh`, nada pronto sem teste e exit code.

## Referência 2 (dono, mesma data): `docs/windows-ref/referencia-2.mp4`, quadros `quadros2/r01..r14.png`
Para Início, biblioteca, lista de playlist e letras no Windows:
- Início: saudação ("Bom dia, <nome>"), atalhos em grade 3 colunas, carrosséis "Ouvindo agora" e "Voltar a ouvir" com capas quadradas grandes e rolagem horizontal, mini player fixo embaixo (capa, título, coração, controles, ícones à direita).
- Barra lateral de ícones à esquerda (início, busca, biblioteca, configurações embaixo).
- Página de playlist: capa em mosaico 2x2, título, descrição, nº de faixas e duração, botão "Tocar tudo", "Adicionar à fila", aleatório; lista de faixas ao lado.
- Painel direito com abas Fila/Letras e botão Voltar; letra com linha atual em destaque e rolagem suave.
