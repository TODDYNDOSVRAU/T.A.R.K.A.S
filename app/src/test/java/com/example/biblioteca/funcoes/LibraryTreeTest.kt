package com.example

import com.example.data.model.Playlist
import com.example.data.model.Track
import com.example.data.playback.LibraryTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTreeTest {

  private fun t(id: String) = Track(id, "T$id", "A", "B", 1000L, "g", "e")

  private val library = listOf(t("a"), t("b"), t("c"))
  private val playlists = listOf(Playlist("p1", "Road", listOf("c", "gone", "a")))
  private val saved = mapOf("s" to t("s"))

  private fun children(parent: String, favorites: List<String> = emptyList()) =
    LibraryTree.children(parent, library, favorites, playlists, saved)

  @Test fun rootHasThreeFolders() {
    assertEquals(
      listOf(LibraryTree.Folder.FAVORITES, LibraryTree.Folder.PLAYLISTS, LibraryTree.Folder.LIBRARY),
      children(LibraryTree.ROOT)!!.map { it.folder }
    )
  }

  @Test fun libraryListsEveryTrack() {
    assertEquals(listOf("a", "b", "c"), children(LibraryTree.LIBRARY)!!.map { it.id })
  }

  @Test fun favouritesKeepTheirOrderAndFindSavedTracks() {
    assertEquals(listOf("c", "s", "a"), children(LibraryTree.FAVORITES, listOf("c", "s", "x", "a"))!!.map { it.id })
  }

  @Test fun playlistsAreFoldersAndTheirTracksSkipMissingOnes() {
    val folders = children(LibraryTree.PLAYLISTS)!!
    assertEquals(listOf("Road"), folders.map { it.name })
    assertEquals(LibraryTree.playlistId("p1"), folders[0].id)
    assertEquals(listOf("c", "a"), children(folders[0].id)!!.map { it.id })
  }

  @Test fun unknownParentIsNotAFolder() {
    assertNull(children("nope"))
    assertNull(children(LibraryTree.playlistId("missing")))
    assertFalse(LibraryTree.isFolder("a"))
    assertTrue(LibraryTree.isFolder(LibraryTree.playlistId("p1")))
  }

  @Test fun aTrackPlaysInsideTheFolderItWasPickedFrom() {
    val ctx = LibraryTree.contextFor(LibraryTree.playlistId("p1"), t("a"), library, emptyList(), playlists, saved)
    assertEquals(listOf("c", "a"), ctx.map { it.id })
  }

  @Test fun otherwiseItPlaysInTheLibrary() {
    assertEquals(library, LibraryTree.contextFor(null, t("b"), library, emptyList(), playlists, saved))
    assertEquals(library, LibraryTree.contextFor(LibraryTree.ROOT, t("b"), library, emptyList(), playlists, saved))
    // Picked from a folder that does not hold it: the library.
    assertEquals(library, LibraryTree.contextFor(LibraryTree.playlistId("p1"), t("b"), library, emptyList(), playlists, saved))
  }

  @Test fun voiceSearchFindsTitleThenArtistIgnoringCaseAndAccents() {
    val songs = listOf(
      Track("1", "Águas de Março", "Elis", "Elis & Tom", 1L, "g", "e"),
      Track("2", "Wave", "Tom Jobim", "Wave", 1L, "g", "e")
    )
    assertEquals("1", LibraryTree.search("aguas de marco", songs)?.id)
    assertEquals("2", LibraryTree.search("TOM", songs)?.id) // no title has it; the artist of track 2 does
    assertNull(LibraryTree.search("   ", songs))
    assertNull(LibraryTree.search("xyz", songs))
  }

  @Test fun aLongFolderIsCut() {
    val many = (1..LibraryTree.MAX_CHILDREN + 50).map { t("t$it") }
    assertEquals(LibraryTree.MAX_CHILDREN, LibraryTree.children(LibraryTree.LIBRARY, many, emptyList(), emptyList(), emptyMap())!!.size)
  }
}
