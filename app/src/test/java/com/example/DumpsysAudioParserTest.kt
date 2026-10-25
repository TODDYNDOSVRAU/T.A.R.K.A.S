package com.example

import com.example.data.systemaudio.DumpsysAudioParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DumpsysAudioParserTest {

  private val sample = """
    players:
    (not logged)  AudioPlaybackConfiguration piid:295 deviceIds:[] type:android.media.SoundPool u/pid:1000/717 state:idle attr:AudioAttributes: usage=USAGE_ASSISTANCE_SONIFICATION content=CONTENT_TYPE_SONIFICATION flags=0x800 tags= bundle=null sessionId:0 mutedState:none  FormatInfo{isSpatialized=false, channelMask=0x0, sampleRate=0}
      AudioPlaybackConfiguration piid:1431 deviceIds:[2] type:android.media.MediaPlayer u/pid:10216/15849 state:started attr:AudioAttributes: usage=USAGE_MEDIA content=CONTENT_TYPE_MUSIC flags=0x800 tags= bundle=null sessionId:1785 mutedState:none  FormatInfo{isSpatialized=false, channelMask=0x3, sampleRate=44100}
      AudioPlaybackConfiguration piid:1500 deviceIds:[2] type:android.media.AudioTrack u/pid:10300/222 state:paused attr:AudioAttributes: usage=USAGE_GAME content=CONTENT_TYPE_UNKNOWN flags=0x0
    ducked players:
  """.trimIndent()

  @Test
  fun `reads every player line`() {
    assertEquals(3, DumpsysAudioParser.parsePlayers(sample).size)
  }

  @Test
  fun `reads uid, pid, type, state and usage`() {
    val media = DumpsysAudioParser.parsePlayers(sample)[1]
    assertEquals(10216, media.uid)
    assertEquals(15849, media.pid)
    assertEquals("android.media.MediaPlayer", media.playerType)
    assertEquals("started", media.state)
    assertEquals("USAGE_MEDIA", media.usage)
  }

  @Test
  fun `only started players count as playing`() {
    val players = DumpsysAudioParser.parsePlayers(sample)
    assertEquals(listOf(false, true, false), players.map { it.isStarted })
  }

  @Test
  fun `a line without attributes still parses, with no usage`() {
    val players = DumpsysAudioParser.parsePlayers(
      "AudioPlaybackConfiguration piid:9 deviceIds:[] type:android.media.AudioTrack u/pid:10001/5 state:started"
    )
    assertEquals(1, players.size)
    assertNull(players[0].usage)
    assertTrue(players[0].isStarted)
  }

  @Test
  fun `text without players gives an empty list`() {
    assertTrue(DumpsysAudioParser.parsePlayers("nothing to see here\nplayers:\n").isEmpty())
    assertFalse(DumpsysAudioParser.parsePlayers("").any())
  }

  @Test
  fun `the audio session of each player is read, 0 when the system gives none`() {
    // Lines as an Android 16 emulator prints them
    val players = DumpsysAudioParser.parsePlayers(
      """
      AudioPlaybackConfiguration piid:2247 deviceIds:[2] type:android.media.AudioTrack u/pid:10150/2334 state:started attr:AudioAttributes: usage=USAGE_MEDIA content=CONTENT_TYPE_SPEECH flags=0xA00 tags= bundle=null sessionId:3065 mutedState:none  FormatInfo{isSpatialized=false, channelMask=0x1, sampleRate=24000}
      AudioPlaybackConfiguration piid:295 deviceIds:[] type:android.media.SoundPool u/pid:1000/717 state:idle attr:AudioAttributes: usage=USAGE_ASSISTANCE_SONIFICATION content=CONTENT_TYPE_SONIFICATION flags=0x800 tags= bundle=null sessionId:0 mutedState:none
      AudioPlaybackConfiguration piid:12 type:android.media.MediaPlayer u/pid:10200/99 state:started attr:AudioAttributes: usage=USAGE_MEDIA
      """.trimIndent()
    )
    assertEquals(listOf(3065, 0, 0), players.map { it.sessionId })
    assertEquals("USAGE_MEDIA", players[0].usage)
    assertTrue(players[0].isStarted)
  }
}
