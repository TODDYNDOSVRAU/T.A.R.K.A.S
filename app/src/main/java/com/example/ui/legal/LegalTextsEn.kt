package com.example.ui.legal

import com.example.ui.legal.LegalTexts.CONTACT_EMAIL
import com.example.ui.legal.LegalTexts.CONTACT_INSTAGRAM
import com.example.ui.legal.LegalTexts.CONTROLLER
import com.example.ui.legal.LegalTexts.TERMS_VERSION

/**
 * The English version of [LegalTexts]. It is a translation: the Portuguese text is the one that was
 * written for the law the app follows (Brazil, LGPD), and the Terms and the Privacy Policy say so.
 * Keep both in step: a change in one is a change in the other, section by section.
 */
internal object LegalTextsEn {
  private const val UPDATED = "October 29, 2026"
  private const val TRANSLATION = "English translation; the Portuguese version prevails"

  val summary = listOf(
    "Your settings, favorites and playlists stay on this device only. T.A.R.K.A.S. has no account and no server of its own.",
    "When you search on Home, the search term goes to Audius and Jamendo, which deliver the songs. The track's title and artist go to MusicBrainz when you open About the track.",
    "The equalizer's headphone curves come from the AutoEq project and are inside the app; new headphones are fetched from GitHub.",
    "The assistant only uses online AI (Gemini) if a key is configured in the app.",
    "You can export or erase all your data in Settings > Privacy, whenever you want."
  )

  val terms = LegalDoc(
    title = "Terms of Use",
    subtitle = "Version $TERMS_VERSION · updated on $UPDATED · $TRANSLATION",
    sections = listOf(
      "1. Acceptance" to
        "By using T.A.R.K.A.S. you agree to these Terms and to the Privacy Policy. If you do not agree, do not use the app. " +
        "You can stop using the app and erase your data at any time in Settings > Privacy.",
      "2. What the app does" to
        "T.A.R.K.A.S. is a music player with an equalizer, facts about tracks and an assistant by voice and text. " +
        "It plays files on your device and public tracks from Audius, and adjusts the sound of what it plays itself. " +
        "With ADB mode or root, which you enable on your own, the equalizer can also act on the audio of other apps.",
      "3. Free of charge and \"as is\"" to
        "The app is offered free of charge, as it is, with no guarantee that it will work continuously or without errors. " +
        "Third-party services, such as Audius, Jamendo, MusicBrainz and AutoEq, may change or go offline without notice.",
      "4. Third-party content" to
        "The songs on Audius belong to their artists and are delivered by Audius itself, under its terms. " +
        "The songs on Jamendo belong to their artists and are distributed by Jamendo under Creative Commons licenses, for listening; T.A.R.K.A.S. does not download or keep these songs. " +
        "Album, date and genre data come from MusicBrainz (public domain, CC0) and the covers from the Cover Art Archive. " +
        "The headphone curves come from the AutoEq project (MIT license). T.A.R.K.A.S. does not host or sell music.",
      "5. Proper use" to
        "Do not use the app to infringe copyright, get around content protection or break the law. " +
        "You are responsible for the files you add to the app.",
      "6. ADB mode and root" to
        "Granting the DUMP permission through ADB or using root changes how your device behaves. Do it only if you understand what you are doing. " +
        "We are not responsible for problems caused by these settings.",
      "7. Volume and hearing" to
        "High gains in the equalizer can raise the volume a lot. Use them with care, especially with headphones.",
      "8. Limitation of liability" to
        "To the extent the law allows, we are not liable for indirect damages arising from the use of the app or from being unable to use it. " +
        "Nothing in these Terms removes rights guaranteed by the Brazilian Consumer Protection Code.",
      "9. Changes" to
        "These Terms may change. When the change is relevant, the app will ask you to read and accept the new version.",
      "10. Law and contact" to
        "These Terms follow the laws of Brazil. Questions: $CONTACT_EMAIL or $CONTACT_INSTAGRAM on Instagram."
    )
  )

  val privacy = LegalDoc(
    title = "Privacy Policy",
    subtitle = "Version $TERMS_VERSION · updated on $UPDATED · in line with the LGPD (Brazilian Law 13,709/2018) · $TRANSLATION",
    sections = listOf(
      "Who is responsible" to
        "The data controller is $CONTROLLER, developer of T.A.R.K.A.S. Contact for privacy matters and to exercise your rights: " +
        "$CONTACT_EMAIL, or $CONTACT_INSTAGRAM on Instagram.",
      "Data that stays on your device only" to
        "App settings (theme, appearance, equalizer and presets), favorites, playlists, the playback queue, how many times each song was played or skipped (for the Most played order) and the acceptance of these terms (version and date). " +
        "This data is not sent to any server of ours: T.A.R.K.A.S. has no server and no user account.",
      "Data sent to third parties, and what for" to
        "• Audius: the text you search on Home and the requests for the tracks you play, to list and stream the songs. Audius also receives the connection's IP address, like any website.\n" +
        "• Jamendo: the text you search on Home, the request for popular songs and the requests for the tracks you play, to list and stream the songs. Jamendo receives the connection's IP and the app's identification (not yours).\n" +
        "• MusicBrainz and Cover Art Archive: the track's title and artist, only when you open About the track in the player, to show album, date, genres and cover. They receive the connection's IP.\n" +
        "• GitHub (AutoEq project): the headphone curves come inside the app; up to once a week it checks the AutoEq list for new headphones and downloads the curve of a new headphone when you choose it. None of your data goes along other than the IP.\n" +
        "• Google Gemini: only if an AI key is configured in the app, the messages you write to the assistant that are not local commands.\n" +
        "• Speech recognition and reading aloud: done by Android's own services, under the rules of your device's manufacturer.",
      "Device permissions" to
        "• Notifications: to show the music controls.\n" +
        "• Music and audio: to list the songs on the device in the Library. The list stays on the device.\n" +
        "• Microphone: only for the live spectrum in the equalizer. Android requires this permission to read the sound the app plays; nothing is recorded or sent.\n" +
        "• Screen/audio capture (level meter, optional): measures the volume of other apps' sound while you are on the page; nothing is recorded.\n" +
        "• DUMP permission (ADB mode, optional): reads which apps are playing, to apply the equalizer to them.\n" +
        "All of them are requested at the moment of use, and the rest of the app works without them.",
      "Legal basis" to
        "Performance of the service you asked for by using the app (art. 7, V) to search and play songs and download headphone curves, and your consent (art. 7, I) " +
        "for the optional features, such as online AI, microphone and ADB mode, which you can withdraw at any time.",
      "For how long" to
        "The data stays on the device until you erase it, clear the app's data or uninstall it. We keep no copies.",
      "Your rights (art. 18)" to
        "You can: confirm and access your data; correct it; ask for anonymization, blocking or deletion; take it to another service (portability); " +
        "know who it is shared with; and withdraw consent. In the app itself: Settings > Privacy > Export my data (access and portability) " +
        "and Erase all data (deletion). For the rest, write to $CONTACT_EMAIL. You can also complain to the ANPD, the Brazilian data protection authority.",
      "Security" to
        "The data stays in the app's private storage, which other apps cannot read. The connections to Audius, GitHub and Gemini use HTTPS.",
      "Children" to
        "The app is not aimed at children under 12 and does not knowingly collect children's data.",
      "Changes to this policy" to
        "When this policy changes in a relevant way, the app will show the new version and ask for your acceptance before continuing."
    )
  )

  val changelog = LegalDoc(
    title = "What's new",
    subtitle = "What changed in each version",
    sections = listOf(
      "1.17" to
        "• New: spatial audio with a measured HRTF (KU100), virtual room and Cinema 5.1 stage; 5.1 tracks play virtualized on headphones.\n" +
        "• With spatial audio on, the preamp takes off 3.5 dB automatically to avoid clipping.\n" +
        "• Limiter with its ceiling at 0 dBFS; the old setting is converted once, on first launch.\n" +
        "• Waveform progress bar on by default.\n" +
        "• Library: filter by folder.\n" +
        "• The update notice and permission requests appear when the app opens.\n" +
        "• Motion: lists, tabs, panels and the mini player use the same spring, with a slight overshoot.\n" +
        "• Texts, numbers and dates follow the system language (Portuguese and English).\n" +
        "• Small interface adjustments.",
      "1.16" to
        "• Landscape: mini player and tabs on a single line, more room in the library and in the player, and the camera area covered.\n" +
        "• Library: track menu in Songs, a larger and accessible A-Z strip, and the chosen order always in view.\n" +
        "• Equalizer: labels no longer cut off, landscape layout, larger selector and a notice when the profile search finds nothing.\n" +
        "• Assistant: the conversation scrolls to the new reply, and the help explains what to do in the app.\n" +
        "• About and What's new no longer sit under the status bar, and Settings returns to the same position.\n" +
        "• Reading and contrast adjustments in several places.",
      "1.15" to
        "• New: updates through GitHub. The app tells you when a new version is out and, in the final version, downloads, checks and installs it.\n" +
        "• New: offline AutoEq corrections, with 8,849 headphone profiles in the app, and each headphone keeps its own correction.\n" +
        "• New: crossfade between tracks (1 to 12 s, off by default) and volume normalization between tracks.\n" +
        "• New: queue with drag to reorder, swipe to remove with undo, and what plays next; move and remove also from the track menu.\n" +
        "• New: search in Songs and Favorites, \"Most played\" order and A-Z strip in the library.\n" +
        "• Equalizer: exact value through a numeric field, undo when restoring, deleting a band or changing preset, and names written out in full.\n" +
        "• Player in landscape split in half, and the player opens growing from the mini player.\n" +
        "• App texts in Portuguese and English.\n" +
        "• Fix: playback no longer stalls at the end of a track with spatial audio.",
      "1.14" to
        "• New: Jamendo songs in the search and in the Popular on Jamendo row on Home, played in full by the app's player, with equalizer and spatial audio.\n" +
        "• New: About the track, in the player's ⋯ menu: album, release and genres from MusicBrainz, with the cover from the Cover Art Archive.\n" +
        "• YouTube Music and Spotify left the app. The sessions kept on the device were erased.\n" +
        "• Terms of Use and Privacy Policy updated for the new services.",
      "1.13" to
        "• Spotify and YouTube Music: the next track no longer goes silent or pauses on its own.\n" +
        "• The Spotify login, which opened a black screen, works again; Settings shows when the account is connected.\n" +
        "• List animation, in Settings: Slide, Fade, Zoom, Side, Flip or Bounce when scrolling the songs.\n" +
        "• Fix in the audio when going from one track to another with a different format.",
      "1.12" to
        "• The Audio screen is shorter, with tabs: Curve, Presets and Spatial.\n" +
        "• Spatial audio works on any output, not only headphones, and no longer pauses when Android's spatial audio is on.\n" +
        "• With ADB mode or root, spatial audio also reaches other apps (Spotify, YouTube), on devices that accept the effect.",
      "1.11" to
        "• Look rebuilt from scratch: typography, spacing and lists in the style of Apple's apps, with less glass and more clarity.\n" +
        "• New opening animation.\n" +
        "• Spatial audio for headphones, with adjustable intensity, in the Audio tab.\n" +
        "• Smoother and more precise animations; the play button turns into pause.\n" +
        "• The back button follows the path you took, and the app returns to the same screen after rotating or being closed by Android.\n" +
        "• Faders, knobs and the progress bar work with TalkBack.\n" +
        "• Fixes: music stopped between tracks on some devices; YouTube and Spotify froze if the page closed on its own; the fact did not pause; notices disappeared too fast.",
      "1.10" to
        "• Music keeps playing with the screen off and with the app closed, with controls in the notification, on the lock screen, on the headset and over Bluetooth.\n" +
        "• Library > Songs: every song on the phone, with cover, artist and album.\n" +
        "• No pause between tracks (gapless).\n" +
        "• The app remembers the song, the position and the queue when it is reopened.\n" +
        "• Sleep timer in the player's ⋯ menu: from 5 to 90 minutes or at the end of the track.\n" +
        "• Equalizer without clicks, cleaner limiter and automatic preamp.\n" +
        "• Fixes: skipping during a fact skipped two songs; the EQ in other apps went flat after Android closed the app.",
      "1.9" to
        "• The Home search also brings YouTube Music and Spotify, with a badge, and plays through the T.A.R.K.A.S. player, even with the screen off.\n" +
        "• Sign in to the accounts in Settings > Streaming (YouTube Premium without ads; Spotify on the free plan, with ads).\n" +
        "• New name in the launcher: T.A.R.K.A.S.\n" +
        "• Limitation: the app's equalizer does not yet act on the sound of YouTube and Spotify.",
      "1.8" to
        "• Real liquid glass on the tab bar, the mini player and the notices (Android 13 or newer): the glass bends what passes behind it.\n" +
        "• Faster tab switching, an indicator that stretches like a drop and a bar that shrinks when scrolling.\n" +
        "• Cards and lists come in one after another; covers fade in; the wait for \"Trending\" shows a preview of the cards.\n" +
        "• Player: drag down to close, back gesture with preview, swipe the cover or the mini player to skip, a heart when you like.\n" +
        "• Initial setup with Terms of Use and Privacy Policy.\n" +
        "• Settings reorganized, accent color, reduce transparency, storage, export and erase data, and the About page.",
      "1.7" to
        "• Home with \"Trending on Audius\" and the demo tracks kept apart.\n" +
        "• A track that fails says so and skips to the next; clear notices without internet.\n" +
        "• Real cover in the notification; permissions requested only at the moment of use.",
      "1.6" to "• Equalizer as a single console, with its own parametric bands.",
      "1.5" to "• Parametric equalizer with cards and knobs.",
      "1.4" to "• Redesigned equalizer and headphone presets (AutoEq).",
      "1.3" to "• The player grows from the mini player or from the song that was tapped.",
      "1.2" to "• The app follows the system's animation and blur settings."
    )
  )

  val licenses = LegalDoc(
    title = "Open-source licenses",
    subtitle = "T.A.R.K.A.S. uses these projects. Thanks to the authors.",
    sections = OpenSourceNotices.librarySections + listOf(
      "AutoEq" to "Jaakko Pasanen · MIT License · headphone correction curves",
      "SADIE II (measured HRTF, Neumann KU100)" to "Copyright 2018, University of York · Apache License 2.0 · head responses used in the 5.1 virtualization",
      "Inter" to "The Inter Project Authors · SIL Open Font License 1.1",
      "Space Grotesk" to "The Space Grotesk Project Authors · SIL Open Font License 1.1",
      "JetBrains Mono" to "The JetBrains Mono Project Authors · SIL Open Font License 1.1",
      "SIL Open Font License 1.1" to "The fonts can be used, studied, modified and redistributed freely, including embedded in software, " +
        "as long as they are not sold by themselves. Full text at https://openfontlicense.org.",
      "MIT License (AutoEq)" to OpenSourceNotices.AUTOEQ_MIT,
      "Apache License 2.0" to "Licensed under the Apache License, Version 2.0. You may obtain a copy at https://www.apache.org/licenses/LICENSE-2.0. " +
        "The software is distributed \"as is\", without warranties or conditions of any kind."
    )
  )

  val credits = listOf(
    "Streaming music" to "Audius, an open music network run by the artists themselves, and Jamendo, independent music under Creative Commons",
    "Song data" to "MusicBrainz (CC0) and Cover Art Archive",
    "Headphone correction" to "The AutoEq project and whoever measured each headphone (oratory1990, crinacle, Rtings and others)",
    "Development" to "$CONTROLLER · $CONTACT_INSTAGRAM"
  )
}
