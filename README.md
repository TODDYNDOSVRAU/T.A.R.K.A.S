<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png" width="112" alt="Ícone do T.A.R.K.A.S.">
</p>

<h1 align="center">T.A.R.K.A.S.</h1>

<p align="center"><b>TOTTALY ACURRATE REAL-TIME KIND OF AUDIO SISTEM</b></p>

<p align="center">
  Uma central inteligente de áudio, música e assistência pessoal para Android.<br>
  Equalizador de verdade, suas músicas e os serviços de streaming numa busca só,<br>
  e a história por trás de cada música.
</p>

<p align="center">
  <a href="https://github.com/TODDYNDOSVRAU/T.A.R.K.A.S/releases/latest"><img alt="Última versão" src="https://img.shields.io/github/v/release/TODDYNDOSVRAU/T.A.R.K.A.S?label=vers%C3%A3o&color=0E7C86"></a>
  <img alt="Android 7.0 ou mais novo" src="https://img.shields.io/badge/Android-7.0%2B-7EE3EA">
  <img alt="Kotlin e Jetpack Compose" src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-0E7C86">
  <a href="LICENSE"><img alt="Licença: todos os direitos reservados" src="https://img.shields.io/badge/licen%C3%A7a-todos%20os%20direitos%20reservados-555"></a>
</p>

<p align="center">
  <img src="docs/screenshots/player.png" width="250" alt="Player">
  &nbsp;
  <img src="docs/screenshots/equalizador.png" width="250" alt="Equalizador">
  &nbsp;
  <img src="docs/screenshots/biblioteca.png" width="250" alt="Biblioteca">
</p>

## A ideia

O T.A.R.K.A.S. une três experiências num aplicativo só:

- **Controle de áudio:** um equalizador completo, perfis e, no futuro, sistemas multicanal 5.1 e 7.1, com ajustes que podem ser pedidos em linguagem natural.
- **Música:** as músicas do celular e os serviços de streaming numa busca só, tocando no mesmo player.
- **Inteligência artificial:** um assistente que entende o que está tocando e executa tarefas no próprio app.

O diferencial é **a história por trás de cada música**: enquanto a faixa toca, o app mostra uma curiosidade curta e verificada sobre a inspiração, os bastidores ou o impacto daquela música. É curta o bastante para não interromper e interessante o bastante para dar vontade de saber mais.

A visão completa do projeto está em [docs/VISAO.md](docs/VISAO.md).

## Baixar

Baixe o `TARKAS-x.y.apk` da **[última versão](https://github.com/TODDYNDOSVRAU/T.A.R.K.A.S/releases/latest)** e abra o arquivo no celular. O Android vai pedir para permitir a instalação de apps fora da loja.

Cada versão instala por cima da anterior, sem perder favoritos, playlists e ajustes. O app ainda não está na Play Store.

## O que ele faz

**Player**
- Toca as músicas do celular, com capa, artista e álbum (Biblioteca > Músicas).
- Busca no Audius, no YouTube Music e no Spotify direto do Início. Cada resultado mostra de onde vem e toca no próprio player do app.
- Continua tocando com a tela apagada e com o app fechado. Os controles ficam na notificação, na tela de bloqueio, no fone e no Bluetooth.
- Sem pausa entre as faixas (gapless).
- Fila, aleatório e repetir.
- Lembra a música, a posição e a fila quando você reabre o app.
- Timer de dormir de 5 a 90 minutos ou no fim da faixa, com o volume baixando aos poucos.
- Favoritos e playlists.

**Equalizador**
- Gráfico de 15 bandas ou paramétrico, com frequência, ganho e Q de cada banda.
- Correção para o seu fone, com milhares de modelos do projeto [AutoEq](https://github.com/jaakkopasanen/AutoEq).
- Graves e agudos, balanço, largura estéreo e limitador.
- Pré-amp automático, que baixa o volume o quanto a curva reforça para o som não distorcer.
- Predefinições prontas e as suas próprias.
- Equaliza também os outros apps (Spotify, YouTube, jogos) no modo ADB ou root, como o Poweramp Equalizer.

**Extras**
- Curiosidades sobre as músicas, faladas ou mostradas no player.
- Liquid glass nas barras e no mini player (Android 13 ou mais novo). Respeita "reduzir movimento" e "reduzir transparência" do sistema.
- Tema escuro, AMOLED ou claro, e cor de destaque.

## Equalizar os outros apps (modo ADB)

O Android só deixa um app aplicar efeitos ao som de outros apps com uma permissão que precisa ser liberada uma vez por um computador:

1. Ative a depuração USB no celular (Opções do desenvolvedor).
2. Ligue o celular no computador e rode:

```bash
adb shell pm grant com.aistudio.tarkas.app android.permission.DUMP
```

3. No app, vá em Áudio > Ajustes avançados > Alcance e escolha **ADB**.

Com root, escolha **Root** e o app libera a permissão sozinho.

## Privacidade

O T.A.R.K.A.S. não tem servidor nem conta. Ajustes, favoritos, playlists e equalizador ficam só no seu aparelho.

O que sai do aparelho:
- o que você busca vai para o Audius, o YouTube Music e o Spotify;
- a lista de fones é baixada do AutoEq, no GitHub.

Se você entrar no YouTube Music ou no Spotify, o login é feito na página do próprio serviço. O app não vê a sua senha.

A política completa fica em Ajustes > Sobre > Política de Privacidade.

## Limitações conhecidas

- O equalizador do app não atua no som do YouTube Music e do Spotify.
- A tela de login do Spotify pode abrir em branco, e a reprodução do Spotify ainda não foi testada com conta.
- No modo ADB, alguns apps e alguns fabricantes não informam a sessão de áudio, e o equalizador não chega neles.

## Visão e próximos passos

O que a [visão do projeto](docs/VISAO.md) prevê e onde cada parte está hoje:

| Área | Hoje | Próximos passos |
|---|---|---|
| **Equalizador** | 15 bandas ou paramétrico, correção por fone, graves e agudos, limitador, pré-amp automático | Perfil que troca sozinho conforme o fone ou a caixa conectada |
| **Perfis** | Predefinições prontas e do usuário | Perfis por equipamento e por conteúdo (filmes, músicas, jogos) |
| **Multicanal** | Estéreo, com balanço e largura | Configuração e volume por canal em sistemas 5.1 e 7.1, onde o aparelho e o equipamento permitirem |
| **Outros apps** | Equalizador aplicado a outros apps nos modos ADB e root | Mais compatibilidade entre fabricantes |
| **Streaming** | Audius, YouTube Music e Spotify na busca do Início, com selo de origem | Álbuns, artistas e playlists na busca; Apple Music, se houver um meio permitido |
| **Biblioteca** | Músicas do celular, favoritos, playlists, fila salva, gapless | Páginas de álbum e artista, letras sincronizadas |
| **História por trás da música** | Curiosidades verificadas no player e na notificação, faladas ou mostradas, para um catálogo pequeno de faixas | Muito mais faixas, com fonte citada e "me conta mais" |
| **Assistente** | Comandos de voz e texto para o player e o equalizador | IA contextual: "deixe os graves mais fortes sem abafar os vocais", "encontre músicas parecidas com essa" |
| **Tarefas** | Timer de dormir no player | Timers, lembretes e programações de música pelo assistente |

Tudo o que depende de outros serviços ou do sistema (APIs de streaming, permissões do Android, recursos do aparelho e dos equipamentos) só entra quando há um meio confiável e permitido.

## Feito com

Kotlin, Jetpack Compose, Material 3, AndroidX Media3 (ExoPlayer e MediaSession), Coil, OkHttp, [Haze](https://github.com/chrisbanes/haze) e [Backdrop](https://github.com/Kyant0/AndroidLiquidGlass). O processamento do equalizador (filtros biquad, limitador com antecipação e pré-amp automático) é próprio.

As licenças das bibliotecas usadas estão no app, em Ajustes > Sobre > Licenças de código aberto.

## Licença

**Todos os direitos reservados.** O código está aqui para ser lido, não reaproveitado.

Você pode ler o código e instalar os APKs oficiais para uso pessoal. Sem autorização por escrito, é proibido copiar, modificar, fazer fork para desenvolver ou publicar, compilar e distribuir, usar comercialmente ou usar o nome e a marca. Os detalhes estão em [LICENSE](LICENSE).

Pull requests não são aceitos sem acordo prévio. Problemas e sugestões são bem-vindos nas [issues](https://github.com/TODDYNDOSVRAU/T.A.R.K.A.S/issues).

## Contato

Roger Moreira · roger.moreiraa8@gmail.com · Instagram [@o.rogermoreira](https://instagram.com/o.rogermoreira)
