# Atualizações pelo GitHub

O T.A.R.K.A.S. procura por um release novo em `https://api.github.com/repos/TODDYNDOSVRAU/T.A.R.K.A.S/releases/latest`, baixa o APK, confere e instala.

## O que é automático e o que ainda pede um toque

| Etapa | Automática? |
|---|---|
| Verificar se há release novo (a cada 6 h, ao abrir o app, ou em Ajustes > Atualizações > Verificar agora) | Sim |
| Baixar o APK e conferir (checksum, pacote, versão, assinatura) | Sim, no Wi-Fi por padrão. Em dados móveis só se você tocar em "Baixar e instalar" |
| Instalar | **Depende do Android.** Sem toque só quando o próprio T.A.R.K.A.S. instalou a versão atual e a permissão "instalar apps desconhecidos" está ativa para ele |
| Primeira atualização de uma cópia instalada por adb ou pelo gerenciador de arquivos | **Pede uma confirmação do Android.** Depois dela, as seguintes dispensam o toque |
| Permissão "Permitir instalar atualizações" | **Um toque, uma vez** (Ajustes > Atualizações abre a tela do sistema) |
| Música tocando | O app **não instala** nesse momento. Fica pronta e instala na verificação seguinte ou quando você toca em "Instalar agora" |

A instalação reinicia o app. Por isso ela espera a música parar.

## Como publicar um release que o app reconhece

1. Aumente `versionCode` e `versionName` em `app/build.gradle.kts` para os valores finais do release (hoje `15` e `1.14`, que são os do `v1.14` já publicado, então o próximo release precisa de números maiores). O `versionCode` precisa ser maior que o do release anterior, ou o Android recusa.
2. Gere o APK de release assinado com a **mesma chave** das versões anteriores (ver abaixo): `:app:assembleRelease`.
3. No GitHub, crie o release:
   - **Tag:** `v<versionName>`, por exemplo `v1.15` para `versionName = "1.15"`. Só números separados por ponto, com `v` opcional. Uma tag como `nightly` é ignorada.
   - **Não** marque como rascunho nem como pré-lançamento: esses são ignorados.
   - **Asset:** um único arquivo `.apk`, de preferência `TARKAS-<versionName>.apk`. Com vários `.apk`, o app não adivinha: só aceita se exatamente um nome começar com `TARKAS`.
   - **Checksum:** o GitHub calcula o SHA-256 de cada asset e o app usa esse valor (`digest`). Alternativa, para releases antigos sem `digest`: publique também `TARKAS-<versionName>.apk.sha256` com o hash (`sha256sum` serve). Sem nenhum dos dois, o app só confere pacote, versão e assinatura.
4. Publique. Em até 6 horas (ou ao abrir o app) os aparelhos veem o release.

Exemplo com a CLI do GitHub (só quando você decidir publicar):

```bash
gh release create v1.15 app/build/outputs/apk/release/app-release.apk#TARKAS-1.15.apk --title "T.A.R.K.A.S. 1.15" --notes "..."
```

## Regra das duas versões

| Versão | Como chega ao aparelho | Chave | O que o app faz |
|---|---|---|---|
| **Release** (a versão final, pronta para o usuário) | APK do GitHub | `my-upload-key.jks` | Verifica, baixa, confere e instala, como descrito acima |
| **Desenvolvimento** | `installDebug` pelo adb | `debug.keystore` | **Não instala nada.** Só avisa em Ajustes que há versão nova e abre a página do release; não verifica sozinha nem agenda o job periódico |

Motivo: as chaves são diferentes. O Android recusaria o release por cima do build de desenvolvimento, ou exigiria apagar os dados, e o contrário também. A versão de desenvolvimento diz isso em Ajustes > Atualizações. O que decide é `BuildConfig.DEBUG`; os `signingConfigs` e o `installDebug` não foram alterados.

## A chave de assinatura

**Decisão do dono (2026-11-10): uma chave só para os releases do T.A.R.K.A.S., a chave de upload (`my-upload-key.jks`, SHA-256 `C6:47:F2:4B:…:F3:A8`). É a mesma que assinou o `v1.14` já publicado (conferido com `apksigner verify`), então quem instalou pelo GitHub recebe as atualizações sem reinstalar.**

O Android só instala uma atualização assinada pela mesma chave da versão instalada. O app confere isso antes de instalar e, se a chave for outra, descarta o arquivo e avisa, sem alterar nada.

- Builds `installDebug` usam `debug.keystore` (SHA-256 `8D:9F:2F:DA:…:2B:21`, fora do git), outra chave: só o aparelho de desenvolvimento tem esse tipo de build.
- Builds de release usam `my-upload-key.jks` (fora do git), com `STORE_PASSWORD` e `KEY_PASSWORD` do ambiente (`release-signing.local`).
- Quem instalou o app por `installDebug` e depois recebe um release assinado com a chave de upload vai ver a mensagem de assinatura diferente. Para esse aparelho, desinstale e instale o release uma vez (isso apaga os dados do app; use antes "Exportar meus dados"). Dali em diante as atualizações funcionam.
- **Guarde a chave de release com backup.** Sem ela, nenhum aparelho recebe atualização.

## Limites

- A API pública do GitHub aceita 60 consultas por hora por IP; o app faz poucas por dia. Se o limite estourar, mostra a mensagem e tenta depois.
- O repositório precisa ser público. Em um privado, a API exigiria um token no app, o que esta versão não faz.
- O app procura o release mais recente publicado, não uma tag específica.
