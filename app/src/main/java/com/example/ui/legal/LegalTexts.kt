package com.example.ui.legal

/** A titled text shown in the reading screen: Terms, Privacy, licences, what's new. */
data class LegalDoc(val title: String, val subtitle: String, val sections: List<Pair<String, String>>)

/**
 * The legal texts and the "Sobre" content, written in Portuguese for Brazilian users.
 * Raise [TERMS_VERSION] whenever the Terms or the Privacy Policy change in substance: users who
 * accepted an older version see the acceptance step again on the next launch (LGPD, art. 9 §2).
 *
 * They are a solid base written for this app, not legal advice: have a lawyer review them before
 * publishing on the Play Store, which also needs the Privacy Policy at a public web address.
 */
object LegalTexts {
  const val TERMS_VERSION = 1
  const val UPDATED = "25 de outubro de 2026"

  const val CONTROLLER = "Roger Moreira"
  const val CONTACT_EMAIL = "roger.moreiraa8@gmail.com"
  const val CONTACT_INSTAGRAM = "@o.rogermoreira"
  const val INSTAGRAM_URL = "https://instagram.com/o.rogermoreira"
  const val RELEASES_URL = "https://github.com/TODDYNDOSVRAU/T.A.R.K.A.S/releases"

  /** The short version shown on the acceptance step. */
  val summary = listOf(
    "Seus ajustes, favoritos e playlists ficam só neste aparelho. O T.A.R.K.A.S. não tem conta nem servidor próprio.",
    "Quando você busca no Início, o termo da busca vai para o Audius, que entrega as músicas.",
    "A lista de fones do equalizador é baixada do projeto AutoEq, no GitHub.",
    "O assistente só usa IA online (Gemini) se uma chave for configurada no app.",
    "Você pode exportar ou apagar todos os seus dados em Ajustes > Privacidade, quando quiser."
  )

  val terms = LegalDoc(
    title = "Termos de Uso",
    subtitle = "Versão $TERMS_VERSION · atualizados em $UPDATED",
    sections = listOf(
      "1. Aceitação" to
        "Ao usar o T.A.R.K.A.S. você concorda com estes Termos e com a Política de Privacidade. Se não concordar, não use o app. " +
        "Você pode deixar de usar o app e apagar seus dados a qualquer momento em Ajustes > Privacidade.",
      "2. O que o app faz" to
        "O T.A.R.K.A.S. é um player de música com equalizador, curiosidades sobre faixas e um assistente por voz e texto. " +
        "Ele toca arquivos do seu aparelho e faixas públicas do Audius, e ajusta o som do que ele mesmo toca. " +
        "Com o Modo ADB ou root, que você ativa por conta própria, o equalizador também pode atuar no áudio de outros apps.",
      "3. Uso gratuito e \"como está\"" to
        "O app é oferecido gratuitamente, no estado em que se encontra, sem garantia de funcionamento contínuo ou livre de erros. " +
        "Serviços de terceiros, como o Audius e o AutoEq, podem mudar ou ficar fora do ar sem aviso.",
      "4. Conteúdo de terceiros" to
        "As músicas do Audius pertencem aos seus artistas e são entregues pelo próprio Audius, de acordo com os termos dele. " +
        "As curvas de fones vêm do projeto AutoEq (licença MIT). O T.A.R.K.A.S. não hospeda nem vende músicas.",
      "5. Uso adequado" to
        "Não use o app para infringir direitos autorais, contornar proteções de conteúdo ou violar leis. " +
        "Você é responsável pelos arquivos que adiciona ao app.",
      "6. Modo ADB e root" to
        "Conceder a permissão DUMP pelo ADB ou usar root altera o comportamento do seu aparelho. Faça isso só se entender o que está fazendo. " +
        "Não nos responsabilizamos por problemas causados por essas configurações.",
      "7. Volume e audição" to
        "Ganhos altos no equalizador podem aumentar muito o volume. Use com cuidado, principalmente com fones de ouvido.",
      "8. Limitação de responsabilidade" to
        "Na medida permitida pela lei, não respondemos por danos indiretos decorrentes do uso ou da impossibilidade de usar o app. " +
        "Nada nestes Termos afasta direitos garantidos pelo Código de Defesa do Consumidor.",
      "9. Mudanças" to
        "Estes Termos podem mudar. Quando a mudança for relevante, o app pedirá que você leia e aceite a nova versão.",
      "10. Lei e contato" to
        "Estes Termos seguem as leis do Brasil. Dúvidas: $CONTACT_EMAIL ou $CONTACT_INSTAGRAM no Instagram."
    )
  )

  val privacy = LegalDoc(
    title = "Política de Privacidade",
    subtitle = "Versão $TERMS_VERSION · atualizada em $UPDATED · de acordo com a LGPD (Lei 13.709/2018)",
    sections = listOf(
      "Quem é o responsável" to
        "O controlador dos dados é $CONTROLLER, desenvolvedor do T.A.R.K.A.S. Contato para assuntos de privacidade e para exercer seus direitos: " +
        "$CONTACT_EMAIL, ou $CONTACT_INSTAGRAM no Instagram.",
      "Dados que ficam só no seu aparelho" to
        "Ajustes do app (tema, aparência, equalizador e predefinições), favoritos, playlists, a fila de reprodução e a aceitação destes termos (versão e data). " +
        "Esses dados não são enviados para nenhum servidor nosso: o T.A.R.K.A.S. não tem servidor nem conta de usuário.",
      "Dados enviados a terceiros, e para quê" to
        "• Audius: o texto que você busca no Início e os pedidos das faixas que você toca, para listar e transmitir as músicas. O Audius recebe também o endereço IP da conexão, como qualquer site.\n" +
        "• GitHub (projeto AutoEq): pedidos para baixar a lista de fones e a curva do fone escolhido. Nenhum dado seu vai junto além do IP.\n" +
        "• Google Gemini: só se uma chave de IA estiver configurada no app, as mensagens que você escreve ao assistente que não são comandos locais.\n" +
        "• Reconhecimento de voz e leitura em voz alta: feitos pelos serviços do próprio Android, segundo as regras do fabricante do seu aparelho.",
      "Permissões do aparelho" to
        "• Notificações: para mostrar os controles da música.\n" +
        "• Microfone: só para o espectro ao vivo no equalizador. O Android exige essa permissão para ler o som que o app toca; nada é gravado ou enviado.\n" +
        "• Captura de tela/áudio (medidor de nível, opcional): mede o volume do som dos outros apps enquanto você estiver na página; nada é gravado.\n" +
        "• Permissão DUMP (Modo ADB, opcional): lê quais apps estão tocando, para aplicar o equalizador neles.\n" +
        "Todas são pedidas na hora do uso, e o resto do app funciona sem elas.",
      "Base legal" to
        "Execução do serviço que você pediu ao usar o app (art. 7º, V) para buscar e tocar músicas e baixar curvas de fones, e o seu consentimento (art. 7º, I) " +
        "para os recursos opcionais, como IA online, microfone e Modo ADB, que você pode revogar a qualquer momento.",
      "Por quanto tempo" to
        "Os dados ficam no aparelho até você apagá-los, limpar os dados do app ou desinstalá-lo. Não guardamos cópias.",
      "Seus direitos (art. 18)" to
        "Você pode: confirmar e acessar seus dados; corrigi-los; pedir anonimização, bloqueio ou eliminação; levá-los a outro serviço (portabilidade); " +
        "saber com quem são compartilhados; e revogar consentimentos. No próprio app: Ajustes > Privacidade > Exportar meus dados (acesso e portabilidade) " +
        "e Apagar todos os dados (eliminação). Para o resto, escreva para $CONTACT_EMAIL. Você também pode reclamar à ANPD.",
      "Segurança" to
        "Os dados ficam no armazenamento privado do app, que outros apps não leem. As conexões com Audius, GitHub e Gemini usam HTTPS.",
      "Crianças" to
        "O app não é voltado a menores de 12 anos e não coleta dados de crianças de forma consciente.",
      "Mudanças nesta política" to
        "Quando esta política mudar de forma relevante, o app mostrará a nova versão e pedirá seu aceite antes de continuar."
    )
  )

  /** What changed in each version, newest first. */
  val changelog = LegalDoc(
    title = "Novidades",
    subtitle = "O que mudou em cada versão",
    sections = listOf(
      "1.8" to
        "• Liquid glass de verdade na barra de abas, no mini player e nos avisos (Android 13 ou mais novo): o vidro curva o que passa por trás.\n" +
        "• Troca de abas mais rápida, indicador que estica como uma gota e barra que encolhe ao rolar.\n" +
        "• Cartões e listas entram em sequência; capas aparecem com fade; espera do \"Em alta\" com prévia dos cartões.\n" +
        "• Player: arraste para baixo para fechar, gesto de voltar com prévia, deslize a capa ou o mini player para pular, coração ao curtir.\n" +
        "• Configuração inicial com Termos de Uso e Política de Privacidade.\n" +
        "• Ajustes reorganizados, cor de destaque, reduzir transparência, armazenamento, exportar e apagar dados, e a página Sobre.",
      "1.7" to
        "• Início com \"Em alta no Audius\" e faixas de demonstração separadas.\n" +
        "• Faixa que falha avisa e pula para a próxima; avisos claros sem internet.\n" +
        "• Capa real na notificação; permissões pedidas só na hora de usar.",
      "1.6" to "• Equalizador como um console único, com bandas paramétricas próprias.",
      "1.5" to "• Equalizador paramétrico com cartões e knobs.",
      "1.4" to "• Equalizador redesenhado e predefinições de fones (AutoEq).",
      "1.3" to "• O player cresce a partir do mini player ou da música tocada.",
      "1.2" to "• O app segue as configurações de animação e desfoque do sistema."
    )
  )

  /** Open-source software inside the app, with its licence. */
  val licenses = LegalDoc(
    title = "Licenças de código aberto",
    subtitle = "O T.A.R.K.A.S. usa estes projetos. Obrigado aos autores.",
    sections = listOf(
      "Jetpack Compose, AndroidX, Material 3" to "Google · Apache License 2.0",
      "AndroidX Media3 (ExoPlayer)" to "Google · Apache License 2.0",
      "Kotlin e kotlinx.coroutines" to "JetBrains · Apache License 2.0",
      "Coil" to "Coil Contributors · Apache License 2.0",
      "Haze" to "Chris Banes · Apache License 2.0",
      "Backdrop (AndroidLiquidGlass)" to "Kyant · Apache License 2.0",
      "OkHttp" to "Square · Apache License 2.0",
      "AutoEq" to "Jaakko Pasanen · MIT License · curvas de correção de fones",
      "Apache License 2.0" to "Licenciado sob a Apache License, Versão 2.0. Você pode obter uma cópia em https://www.apache.org/licenses/LICENSE-2.0. " +
        "O software é distribuído \"como está\", sem garantias ou condições de qualquer tipo."
    )
  )

  val credits = listOf(
    "Músicas em streaming" to "Audius, rede aberta de música dos próprios artistas",
    "Correção de fones" to "Projeto AutoEq e quem mediu cada fone (oratory1990, crinacle, Rtings e outros)",
    "Desenvolvimento" to "$CONTROLLER · $CONTACT_INSTAGRAM"
  )
}
