package com.example

import android.content.Intent
import android.net.Uri
import com.example.data.model.Track
import com.example.ui.components.TrackShare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** P2: what sharing a track sends, by kind of track. */
@RunWith(RobolectricTestRunner::class)
class TrackShareTest {
  private fun track(path: String?, local: Boolean = true, demo: Boolean = false, artist: String = "Ana") =
    Track("id", "Song", artist, "Album", 1000, "g", "e", isLocalFile = local, filePath = path, isDemo = demo)

  @Test fun aFileOnTheDeviceGoesAsAudioWithAReadGrant() {
    val i = TrackShare.intent(track("content://media/external/audio/media/7"))!!
    assertEquals(Intent.ACTION_SEND, i.action)
    assertEquals("audio/*", i.type)
    assertEquals(Uri.parse("content://media/external/audio/media/7"), i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
    assertTrue(i.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    assertEquals(Uri.parse("content://media/external/audio/media/7"), i.clipData!!.getItemAt(0).uri)
  }

  @Test fun aStreamGoesAsTextWithItsLink() {
    val i = TrackShare.intent(track("https://example.org/a.mp3", local = false))!!
    assertEquals("text/plain", i.type)
    assertEquals("Song — Ana\nhttps://example.org/a.mp3", i.getStringExtra(Intent.EXTRA_TEXT))
  }

  @Test fun aTrackWithoutAddressGoesAsTitleAndArtist() {
    val i = TrackShare.intent(track(null, local = false))!!
    assertEquals("Song — Ana", i.getStringExtra(Intent.EXTRA_TEXT))
  }

  @Test fun noArtistMeansTitleAlone() {
    assertEquals("Song", TrackShare.text(track(null, artist = "")))
  }

  @Test fun theDemoSongsAreNotOffered() {
    val demo = track("/data/x.wav", demo = true)
    assertFalse(TrackShare.canShare(demo))
    assertNull(TrackShare.intent(demo))
  }
}
