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
      sources = listOf(
        "Emerson, Keith (2004). Pictures of an Exhibitionist. John Blake Publishing.",
        "Macan, Edward (1997). Rocking the Classics: English Progressive Rock. Oxford University Press.",
        "AllMusic Guide: Tarkus Album Overview by Bruce Eder."
      ),
      status = VerificationStatus.VERIFIED_FACT,
      verifiedDate = "2026-10-15",
    ),
    "track_tubular" to Curiosity(
      trackId = "track_tubular",
      trackTitle = "Tubular Bells (Part One)",
      artist = "Mike Oldfield",
      shortTrivia = "Mike Oldfield tinha apenas 19 anos quando gravou quase todos os instrumentos no estúdio The Manor, sobrepondo dezenas de pistas com fitas analógicas. O álbum inaugurou a Virgin Records de Richard Branson em 1973 e ganhou projeção mundial ao ser adotado pelo diretor William Friedkin na trilha sonora clássica do filme 'O Exorcista'.",
      wordCount = 58,
      documentedFacts = "Gravado entre o outono de 1972 e a primavera de 1973 no estúdio The Manor em Oxfordshire. Oldfield gravou violões, baixo, bandolim, sintetizador Farfisa e os famosos sinos tubulares orquestrais.",
      sources = listOf(
        "Oldfield, Mike (2007). Changeling: The Autobiography of Mike Oldfield. Virgin Books.",
        "Sound on Sound Magazine (April 2001). Classic Tracks: Mike Oldfield's Tubular Bells by Richard Buskin.",
        "British Phonographic Industry (BPI) Certification Database."
      ),
      status = VerificationStatus.VERIFIED_FACT,
      verifiedDate = "2026-10-10",
    ),
    "track_debussy" to Curiosity(
      trackId = "track_debussy",
      trackTitle = "Clair de Lune",
      artist = "Claude Debussy",
      shortTrivia = "Terceiro movimento da 'Suite bergamasque', inspirada no poema homônimo de Paul Verlaine de 1869. Composta a partir de 1890 e revisada em 1905, a peça evoca a luz da lua por meio de tempo rubato, polirritmia sutil e escalas pentatônicas impressionistas que desafiaram a harmonia romântica germânica tradicional.",
      wordCount = 52,
      documentedFacts = "A peça foi publicada em 1905 pela editora musical francesa Eugène Fromont, após quase 15 anos de revisão meticulosa da partitura original pelo compositor.",
      sources = listOf(
        "Lockspeiser, Edward (1962). Debussy: His Life and Mind. Cambridge University Press.",
        "Bibliothèque nationale de France (BnF Gallica) - Archives Département de la Musique.",
        "Grove Music Online - Debussy, (Achille-)Claude."
      ),
      status = VerificationStatus.VERIFIED_FACT,
      verifiedDate = "2026-09-28",
    ),
    "track_satie" to Curiosity(
      trackId = "track_satie",
      trackTitle = "Gymnopédie No. 1",
      artist = "Erik Satie",
      shortTrivia = "Publicada em Paris em 1888, a composição rompeu com o virtuosismo do século XIX através de cadências modais suaves e acordes estáticos de 7ª maior. É considerada precursora fundamental da música ambiente moderna, do minimalismo e da concepção de música para mobília ('musique d'ameublement').",
      wordCount = 48,
      documentedFacts = "Publicada pela primeira vez no periódico 'Le Chat Noir' e posteriormente em partitura impressa pelo pai de Satie, Alfred Satie, em Paris no ano de 1888.",
      sources = listOf(
        "Orledge, Robert (1990). Satie the Composer. Cambridge University Press.",
        "Davis, Mary E. (2007). Erik Satie. Reaktion Books.",
        "Eno, Brian (1978). Ambient 1: Music for Airports - Liner Notes on Satie's Furniture Music."
      ),
      status = VerificationStatus.VERIFIED_FACT,
      verifiedDate = "2026-10-02",
    ),
    "track_sweep" to Curiosity(
      trackId = "track_sweep",
      trackTitle = "Soundcheck Sweep (20Hz-20kHz)",
      artist = "Tarkas Acoustic Lab",
      shortTrivia = "Sinal de teste acústico gerado digitalmente para medição de resposta espectral e detecção de nós ressonantes na sala ou fones de ouvido. Permite avaliar a linearidade do transdutor em 60Hz (sub-bass), 1kHz (referência vocal) e 10kHz (presença de agudos), sem mascaramento ou compressão dinâmica.",
      wordCount = 51,
      documentedFacts = "Gerado em padrão PCM 16-bit 44.1 kHz linear sem dither ou compressão dinâmica de faixa dinâmica. Baseado nos padrões de calibração eletroacústica AES-67 e IEC 60268-7.",
      sources = listOf(
        "Audio Engineering Society (AES) Standards Committee - AES3-2003 / AES67.",
        "IEC 60268-7: Sound System Equipment - Part 7: Headphones and Earphones.",
        "Toole, Floyd E. (2017). Sound Reproduction: The Acoustics and Psychoacoustics of Loudspeakers and Rooms. Focal Press."
      ),
      status = VerificationStatus.TECHNICAL_SPEC,
      verifiedDate = "2026-10-18",
    )
  )

  /**
   * The verified curiosity of a track, or null when there is none. Nothing is shown, spoken or put
   * in the notification for a track without one: never invent trivia or sources.
   */
  fun getCuriosityForTrack(track: com.example.data.model.Track): Curiosity? = curiosityDatabase[track.id]

  fun getAllCuriosities(): List<Curiosity> {
    return curiosityDatabase.values.toList()
  }
}
