<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png" width="96" alt="T.A.R.K.A.S.">
</p>

<h1 align="center">T.A.R.K.A.S.</h1>

<p align="center"><b>TOTALLY ACCURATE REAL-TIME KIND OF AUDIO SYSTEM</b></p>

<p align="center">Player de música e central de áudio para Android.</p>

<p align="center">
  <a href="https://github.com/TODDYNDOSVRAU/T.A.R.K.A.S/releases/latest"><img alt="Versão" src="https://img.shields.io/github/v/release/TODDYNDOSVRAU/T.A.R.K.A.S?label=vers%C3%A3o&color=0E7C86"></a>
  <img alt="Android 7.0+" src="https://img.shields.io/badge/Android-7.0%2B-7EE3EA">
  <a href="LICENSE"><img alt="Licença" src="https://img.shields.io/badge/licen%C3%A7a-todos%20os%20direitos%20reservados-555"></a>
</p>

<p align="center">
  <img src="docs/screenshots/player.png" width="240" alt="Player">
  <img src="docs/screenshots/equalizador.png" width="240" alt="Equalizador">
  <img src="docs/screenshots/biblioteca.png" width="240" alt="Biblioteca">
</p>

## Recursos

**Reprodução**
- Músicas do aparelho, Audius, YouTube Music e Spotify em uma busca unificada
- Reprodução em segundo plano com controles na notificação, tela de bloqueio, fone e Bluetooth
- Gapless, fila, aleatório, repetir, favoritos e playlists
- Fila e posição restauradas ao reabrir
- Timer de dormir

**Equalizador**
- Gráfico de 15 bandas ou paramétrico (frequência, ganho e Q)
- Correção por modelo de fone ([AutoEq](https://github.com/jaakkopasanen/AutoEq))
- Graves, agudos, balanço, largura estéreo, limitador e pré-amp automático
- Aplicação em outros apps via ADB ou root

**Curiosidades**
- Fatos verificados sobre a faixa em reprodução, no player e na notificação

## Instalação

Baixe o APK da [última versão](https://github.com/TODDYNDOSVRAU/T.A.R.K.A.S/releases/latest) e instale no aparelho. Atualizações instalam sobre a versão anterior sem perda de dados.

**Equalizador em outros apps (ADB)**

```bash
adb shell pm grant com.aistudio.tarkas.app android.permission.DUMP
```

Depois, em Áudio > Ajustes avançados > Alcance, selecione **ADB**. Com root, selecione **Root**.

## Privacidade

Sem conta e sem servidor próprio. Dados ficam no aparelho. Buscas são enviadas aos serviços consultados (Audius, YouTube Music, Spotify). [Política completa](app/src/main/java/com/example/ui/legal/LegalTexts.kt) também disponível no app.

## Limitações

- O equalizador não se aplica ao áudio do YouTube Music e do Spotify.
- Spotify: login e reprodução ainda não validados.
- Modo ADB depende do fabricante e do app de origem.

## Roadmap

Perfis por dispositivo, multicanal 5.1/7.1, páginas de álbum e artista, letras sincronizadas e assistente com IA contextual. Detalhes em [docs/VISAO.md](docs/VISAO.md).

## Tecnologia

Kotlin · Jetpack Compose · Material 3 · AndroidX Media3 · Coil · OkHttp · DSP próprio (biquad, limitador com lookahead)

## Licença

Todos os direitos reservados. Uso pessoal dos APKs oficiais permitido; cópia, modificação, redistribuição e uso comercial proibidos sem autorização. Ver [LICENSE](LICENSE).

## Contato

Roger Moreira · roger.moreiraa8@gmail.com
