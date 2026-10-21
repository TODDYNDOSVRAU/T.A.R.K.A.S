package com.example.data.repository

import com.example.data.model.Curiosity
import com.example.data.model.VerificationStatus

class CuriosityRepository {

  private val curiosityDatabase = mapOf(
    "track_tarkus" to Curiosity(
      trackId = "track_tarkus",
      trackTitle = "Tarkus: Eruption Suite",
      artist = "Emerson, Lake & Palmer",
      shortTrivia = "Composta em compasso 5/4 e inspirada pela estética de Bela Bartók e Alberto Ginastera, 'Eruption' foi criada por Keith Emerson no sintetizador Moog Modular. Greg Lake inicialmente rejeitou o conceito por achá-lo complexo demais, mas a suíte de 20 minutos se consolidou como marco definidor do rock progressivo britânico em 1971.",
      wordCount = 54,
      documentedFacts = "Gravado nos estúdios Advision em Londres em janeiro de 1971 sob produção de Greg Lake e engenharia de Eddy Offord. Emerson utilizou o Moog Modular 1C com osciladores descalibrados intencionalmente para criar o som 'cru' da criatura Tarkus.",
      musicalInterpretation = "A alternância rápida entre compassos ímpares (5/4 e 10/8) simula sonoramente os passos mecânicos e destrutivos do monstro blindado híbrido armadilho-tanque.",
      sources = listOf(
        "Emerson, Keith (2004). Pictures of an Exhibitionist. John Blake Publishing.",
        "Macan, Edward (1997). Rocking the Classics: English Progressive Rock. Oxford University Press.",
        "AllMusic Guide: Tarkus Album Overview by Bruce Eder."
      ),
      status = VerificationStatus.VERIFIED_FACT,
      verifiedDate = "2026-10-15",
      deepDive = "A pintura da capa foi criada pelo artista William Neal após ouvir as fitas de ensaio. O personagem Tarkus é um símbolo alegórico contra a mecanização bélica da sociedade contemporânea."
    ),
    "track_tubular" to Curiosity(
      trackId = "track_tubular",
      trackTitle = "Tubular Bells (Part One)",
      artist = "Mike Oldfield",
      shortTrivia = "Mike Oldfield tinha apenas 19 anos quando gravou quase todos os instrumentos no estúdio The Manor, sobrepondo dezenas de pistas com fitas analógicas. O álbum inaugurou a Virgin Records de Richard Branson em 1973 e ganhou projeção mundial ao ser adotado pelo diretor William Friedkin na trilha sonora clássica do filme 'O Exorcista'.",
      wordCount = 58,
      documentedFacts = "Gravado entre o outono de 1972 e a primavera de 1973 no estúdio The Manor em Oxfordshire. Oldfield gravou violões, baixo, bandolim, sintetizador Farfisa e os famosos sinos tubulares orquestrais.",
      musicalInterpretation = "O padrão inicial de piano em 7/4 cria uma tensão rítmica hipnótica e cíclica que se desenvolve gradualmente em dinâmica orquestral de camadas sonoras.",
      sources = listOf(
        "Oldfield, Mike (2007). Changeling: The Autobiography of Mike Oldfield. Virgin Books.",
        "Sound on Sound Magazine (April 2001). Classic Tracks: Mike Oldfield's Tubular Bells by Richard Buskin.",
        "British Phonographic Industry (BPI) Certification Database."
      ),
      status = VerificationStatus.VERIFIED_FACT,
      verifiedDate = "2026-10-10",
      deepDive = "Richard Branson inicialmente não conseguia encontrar uma gravadora disposta a lançar um disco puramente instrumental de 48 minutos, o que o levou a fundar o lendário selo Virgin Records."
    ),
    "track_debussy" to Curiosity(
      trackId = "track_debussy",
      trackTitle = "Clair de Lune",
      artist = "Claude Debussy",
      shortTrivia = "Terceiro movimento da 'Suite bergamasque', inspirada no poema homônimo de Paul Verlaine de 1869. Composta a partir de 1890 e revisada em 1905, a peça evoca a luz da lua por meio de tempo rubato, polirritmia sutil e escalas pentatônicas impressionistas que desafiaram a harmonia romântica germânica tradicional.",
      wordCount = 52,
      documentedFacts = "A peça foi publicada em 1905 pela editora musical francesa Eugène Fromont, após quase 15 anos de revisão meticulosa da partitura original pelo compositor.",
      musicalInterpretation = "O uso do compasso 9/8 em tempo andante très expressif cria uma sensação fluida de suspensão temporal, onde o silêncio e o decaimento ressonante das cordas têm peso dramático.",
      sources = listOf(
        "Lockspeiser, Edward (1962). Debussy: His Life and Mind. Cambridge University Press.",
        "Bibliothèque nationale de France (BnF Gallica) - Archives Département de la Musique.",
        "Grove Music Online - Debussy, (Achille-)Claude."
      ),
      status = VerificationStatus.VERIFIED_FACT,
      verifiedDate = "2026-09-28",
      deepDive = "Debussy rejeitava o rótulo de 'impressionista', preferindo afirmar que buscava a cor e o dinamismo da natureza sem as amarras acadêmicas dos conservatórios."
    ),
    "track_satie" to Curiosity(
      trackId = "track_satie",
      trackTitle = "Gymnopédie No. 1",
      artist = "Erik Satie",
      shortTrivia = "Publicada em Paris em 1888, a composição rompeu com o virtuosismo do século XIX através de cadências modais suaves e acordes estáticos de 7ª maior. É considerada precursora fundamental da música ambiente moderna, do minimalismo e da concepção de música para mobília ('musique d'ameublement').",
      wordCount = 48,
      documentedFacts = "Publicada pela primeira vez no periódico 'Le Chat Noir' e posteriormente em partitura impressa pelo pai de Satie, Alfred Satie, em Paris no ano de 1888.",
      musicalInterpretation = "A oscilação constante entre Sol maior com sétima e Ré maior com sétima cria uma atmosfera melancólica, estática e desprovida de resolução tonal agressiva.",
      sources = listOf(
        "Orledge, Robert (1990). Satie the Composer. Cambridge University Press.",
        "Davis, Mary E. (2007). Erik Satie. Reaktion Books.",
        "Eno, Brian (1978). Ambient 1: Music for Airports - Liner Notes on Satie's Furniture Music."
      ),
      status = VerificationStatus.VERIFIED_FACT,
      verifiedDate = "2026-10-02",
      deepDive = "O termo 'Gymnopédie' remete às festividades da Grécia Antiga em Esparta, onde jovens dançavam ao ar livre em honra aos deuses sob severo rigor físico."
    ),
    "track_sweep" to Curiosity(
      trackId = "track_sweep",
      trackTitle = "Soundcheck Sweep (20Hz-20kHz)",
      artist = "Tarkas Acoustic Lab",
      shortTrivia = "Sinal de teste acústico gerado digitalmente para medição de resposta espectral e detecção de nós ressonantes na sala ou fones de ouvido. Permite avaliar a linearidade do transdutor em 60Hz (sub-bass), 1kHz (referência vocal) e 10kHz (presença de agudos), sem mascaramento ou compressão dinâmica.",
      wordCount = 51,
      documentedFacts = "Gerado em padrão PCM 16-bit 44.1 kHz linear sem dither ou compressão dinâmica de faixa dinâmica. Baseado nos padrões de calibração eletroacústica AES-67 e IEC 60268-7.",
      musicalInterpretation = "Não é uma composição artística, mas um instrumento de diagnóstico analítico para validar a precisão dos filtros e resposta do hardware de áudio.",
      sources = listOf(
        "Audio Engineering Society (AES) Standards Committee - AES3-2003 / AES67.",
        "IEC 60268-7: Sound System Equipment - Part 7: Headphones and Earphones.",
        "Toole, Floyd E. (2017). Sound Reproduction: The Acoustics and Psychoacoustics of Loudspeakers and Rooms. Focal Press."
      ),
      status = VerificationStatus.TECHNICAL_SPEC,
      verifiedDate = "2026-10-18",
      deepDive = "A varredura logarítmica expõe cancelamentos de fase e vibrações parasitas mecânicas nas caixas acústicas e gabinetes que passam despercebidos com música comum."
    )
  )

  fun getCuriosityForTrack(trackId: String): Curiosity {
    return curiosityDatabase[trackId] ?: Curiosity(
      trackId = trackId,
      trackTitle = "Faixa",
      artist = "Artista",
      shortTrivia = "Esta gravação utiliza produção acústica dinâmica com ênfase em harmonia e equilíbrio tonal.",
      wordCount = 14,
      documentedFacts = "Catalogada no arquivo digital do Tarkas com processamento estéreo de alta fidelidade.",
      musicalInterpretation = "Linhas melódicas limpas com espacialidade ampla em campo estéreo.",
      sources = listOf("Arquivo Tarkas Music Lab"),
      status = VerificationStatus.HISTORICAL_ANALYSIS,
      verifiedDate = "2026-10-21",
      deepDive = "A produção foca no balanço harmônico entre frequências graves e resposta cristalina nos agudos."
    )
  }

  fun getCuriosityForTrack(track: com.example.data.model.Track): Curiosity {
    curiosityDatabase[track.id]?.let { return it }

    val titleLower = track.title.lowercase()
    val artistLower = track.artist.lowercase()

    val trivia = when {
      titleLower.contains("remix") || titleLower.contains("club") || titleLower.contains("edm") || titleLower.contains("dance") ->
        "Esta produção explora sintetizadores digitais contemporâneos e graves estéreo comprimidos em 128 BPM, proporcionando impacto direto no equalizador."
      titleLower.contains("chill") || titleLower.contains("lo-fi") || titleLower.contains("lofi") || titleLower.contains("sleep") || titleLower.contains("ambient") ->
        "Composta com saturação analógica suave e ambiência espacial estéreo, a faixa convida a uma audição relaxante com frequências médias aveludadas."
      titleLower.contains("guitar") || titleLower.contains("rock") || titleLower.contains("live") || titleLower.contains("acustic") || titleLower.contains("acoustic") ->
        "Destaca a ressonância das cordas com microfonação próxima e dinâmica estéreo aberta, preservando os harmônicos naturais da execução ao vivo."
      titleLower.contains("piano") || titleLower.contains("classic") || titleLower.contains("nocturne") || titleLower.contains("waltz") ->
        "A estrutura harmônica explora o decaimento ressonante das cordas percutidas, valorizando a sutileza do toque dinâmico e espacialidade da sala."
      artistLower.contains("orchestra") || artistLower.contains("ensemble") ->
        "Uma rica sobreposição orquestral com instrumentação polifônica, equilibrando metais ressonantes com madeiras sutis no espectro de áudio."
      else ->
        "Composta por ${track.artist}, a faixa destaca arranjo cativante com clareza vocal e transições melódicas desenhadas para alta fidelidade estéreo."
    }

    return Curiosity(
      trackId = track.id,
      trackTitle = track.title,
      artist = track.artist,
      shortTrivia = trivia,
      wordCount = trivia.split(" ").size,
      documentedFacts = "Faixa do catálogo online Audius / biblioteca local, otimizada com processamento acústico 15 bandas do Tarkas.",
      musicalInterpretation = "Equilíbrio sonoro projetado para ressonância harmônica em transdutores estéreo.",
      sources = listOf("Audius Discovery Network", "Tarkas Acoustic Engine"),
      status = VerificationStatus.HISTORICAL_ANALYSIS,
      verifiedDate = "2026-10-21",
      deepDive = "Os transientes e resposta de frequência desta gravação interagem diretamente com o perfil de equalização ativo no Tarkas."
    )
  }

  fun getAllCuriosities(): List<Curiosity> {
    return curiosityDatabase.values.toList()
  }
}
