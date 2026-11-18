package com.example

import com.example.data.library.FolderCheck
import com.example.data.library.FolderPaths
import com.example.data.library.FolderRules
import com.example.data.library.FolderTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which folders of the phone are in the library, and the tree the user ticks them in. */
class FolderFilterTest {
  private val counts = mapOf(
    "Music/Rock" to 10,
    "Music/Rock/Live" to 3,
    "Music/Podcasts" to 5,
    "Download" to 2,
    "" to 1
  )

  @Test
  fun foldersWithoutRulesAreAllIn() {
    val rules = FolderRules.None
    assertTrue(rules.isIncluded("Music/Rock"))
    assertTrue(rules.isIncluded(""))
    assertFalse(rules.isFiltering)
  }

  @Test
  fun leavingAFolderOutLeavesItsBranchOutToo() {
    val rules = FolderRules.None.with("Music/Rock", false)
    assertFalse(rules.isIncluded("Music/Rock"))
    assertFalse(rules.isIncluded("Music/Rock/Live"))
    assertTrue(rules.isIncluded("Music/Podcasts"))
    assertTrue(rules.isFiltering)
  }

  @Test
  fun aFolderCanComeBackInsideABranchThatIsOut() {
    val rules = FolderRules.None.with("Music", false).with("Music/Rock", true)
    assertFalse(rules.isIncluded("Music/Podcasts"))
    assertTrue(rules.isIncluded("Music/Rock"))
    assertTrue(rules.isIncluded("Music/Rock/Live"))
  }

  @Test
  fun tickingAParentSetsTheWholeBranchAgain() {
    val rules = FolderRules.None.with("Music/Rock/Live", false).with("Music", true)
    assertTrue(rules.isIncluded("Music/Rock/Live"))
    assertFalse(rules.isFiltering)
    assertTrue(rules.encode().isEmpty())
  }

  @Test
  fun aRuleThatSaysWhatTheParentSaysIsNotKept() {
    val rules = FolderRules.None.with("Music", false).with("Music/Rock", false)
    assertEquals(setOf("0|Music"), rules.encode())
  }

  @Test
  fun theStorageRootDoesNotDecideForTheFoldersBelow() {
    val rules = FolderRules.None.with("", false)
    assertFalse(rules.isIncluded(""))
    assertTrue(rules.isIncluded("Music"))
  }

  @Test
  fun rulesSurviveBeingSavedAndRead() {
    val rules = FolderRules.None.with("Music", false).with("Music/Rock", true).with("Download", false)
    assertEquals(rules, FolderRules.decode(rules.encode()))
  }

  @Test
  fun damagedSavedRulesAreIgnored() {
    val rules = FolderRules.decode(setOf("garbage", "2|Music", "0Music", "0|Download"))
    assertEquals(setOf("0|Download"), rules.encode())
  }

  @Test
  fun treeHoldsTheFoldersWithMusicAndTheirParents() {
    val tree = FolderTree.build(counts)
    // the root row first, then the top folders in name order
    assertEquals(listOf("", "Download", "Music"), tree.map { it.path })
    val music = tree.last()
    assertEquals(0, music.own)
    assertEquals(18, music.total)
    assertEquals(listOf("Music/Podcasts", "Music/Rock"), music.children.map { it.path })
    assertEquals(13, music.children.last().total)
  }

  @Test
  fun checkStateFollowsWhatTheRulesKeep() {
    val tree = FolderTree.build(counts)
    val music = tree.last()
    assertEquals(FolderCheck.ON, music.state(FolderRules.None))
    val noPodcasts = FolderRules.None.with("Music/Podcasts", false)
    assertEquals(FolderCheck.PARTIAL, music.state(noPodcasts))
    assertEquals(13, music.included(noPodcasts))
    assertEquals(FolderCheck.OFF, music.state(FolderRules.None.with("Music", false)))
  }

  @Test
  fun rowsShowChildrenOnlyOfOpenFolders() {
    val tree = FolderTree.build(counts)
    assertEquals(listOf("", "Download", "Music"), FolderTree.rows(tree, emptySet()).map { it.node.path })
    val open = FolderTree.rows(tree, setOf("Music", "Music/Rock"))
    assertEquals(
      listOf("", "Download", "Music", "Music/Podcasts", "Music/Rock", "Music/Rock/Live"),
      open.map { it.node.path }
    )
    assertEquals(listOf(0, 0, 0, 1, 1, 2), open.map { it.depth })
  }

  @Test
  fun foldersAreReadFromMediaStoreAndFromFilePaths() {
    assertEquals("Music/Rock", FolderPaths.normalize("Music/Rock/"))
    assertEquals("", FolderPaths.normalize(null))
    assertEquals("Music/Rock", FolderPaths.ofFile("/storage/emulated/0/Music/Rock/a.mp3"))
    assertEquals("Music", FolderPaths.ofFile("/storage/ABCD-1234/Music/a.mp3"))
    assertEquals("Music", FolderPaths.ofFile("/sdcard/Music/a.mp3"))
    assertEquals("", FolderPaths.ofFile("/storage/emulated/0/a.mp3"))
  }
}
