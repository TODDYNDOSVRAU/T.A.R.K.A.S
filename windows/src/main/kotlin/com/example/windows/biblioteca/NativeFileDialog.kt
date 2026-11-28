package com.example.windows.biblioteca

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.COM.COMUtils
import com.sun.jna.platform.win32.COM.Unknown
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import java.awt.FileDialog
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.awt.Window
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities

/*
 * The Windows explorer as the music picker: the system's own IFileOpenDialog (the same window Explorer and every native
 * app open), asked through COM from JNA. Folders and audio files are two separate dialogs, as the owner asked, each with
 * its own title and, for files, an audio filter built from what the scanner can read. The dialog runs on its own
 * single-threaded-apartment thread, so the window never freezes while it is open; the result comes back on the UI thread.
 */

/** What the picker is asked to choose. */
enum class PickMode { Folders, Files }

/** What a dialog came back with, before the app decides what to do with it. */
internal sealed interface PickOutcome {
  data class Picked(val paths: List<String>) : PickOutcome
  data object Cancelled : PickOutcome
  data class Failed(val hresult: Int) : PickOutcome
}

/** What the app does with an outcome. */
internal sealed interface PickEffect {
  data class Use(val files: List<File>) : PickEffect
  data object Nothing : PickEffect
  data class Error(val message: String) : PickEffect
}

/** A request to a backend: everything it needs, with no window handle type of its own. */
internal class PickRequest(
  val mode: PickMode,
  val title: String,
  /** Name and pattern pairs for the file-type list; empty for folders. */
  val filters: List<Pair<String, String>>,
  /** The window the dialog belongs to (the native handle), or `0`. */
  val ownerHandle: Long,
)

internal fun interface PickerBackend {
  /** Shows the dialog and waits for it; called off the UI thread. */
  fun pick(request: PickRequest): PickOutcome
}

/** `HRESULT_FROM_WIN32(ERROR_CANCELLED)`: what the dialog returns when the person closes it without choosing. */
internal const val HR_CANCELLED = 0x800704C7.toInt()
internal const val HR_FAIL = 0x80004005.toInt()

/** The code as Windows documents it, for the message the person reads. */
internal fun hresultText(hr: Int): String = "0x" + Integer.toHexString(hr).uppercase().padStart(8, '0')

/** `S_OK` is a pick, the cancel code is a quiet no-op, every other code is a failure of the dialog itself. */
internal fun outcomeOf(hr: Int, paths: () -> List<String>): PickOutcome = when {
  hr == 0 -> PickOutcome.Picked(paths())
  hr == HR_CANCELLED -> PickOutcome.Cancelled
  else -> PickOutcome.Failed(hr)
}

/**
 * What to do with [outcome]. A cancel does nothing and says nothing. A failed folder dialog is told to the person in the
 * T.A.L. voice (what happened, why, what to do). A failed file dialog falls back to the AWT dialog ([fallbackFiles]),
 * which is also native on Windows; it is never `JFileChooser`.
 */
internal fun interpret(mode: PickMode, outcome: PickOutcome, fallbackFiles: () -> List<File>): PickEffect = when (outcome) {
  is PickOutcome.Cancelled -> PickEffect.Nothing
  is PickOutcome.Picked -> outcome.paths.map(::File).let { if (it.isEmpty()) PickEffect.Nothing else PickEffect.Use(it) }
  is PickOutcome.Failed -> when (mode) {
    PickMode.Files -> fallbackFiles().let { if (it.isEmpty()) PickEffect.Nothing else PickEffect.Use(it) }
    PickMode.Folders -> PickEffect.Error(
      lt(
        "The folder window did not open (code ${hresultText(outcome.hresult)}). Windows refused to create it. Try again, or drag the folder onto this window.",
        "A janela de pastas não abriu (código ${hresultText(outcome.hresult)}). O Windows recusou criá-la. Tente de novo, ou arraste a pasta para esta janela."
      )
    )
  }
}

/**
 * The file-type list of the audio dialog: one entry for every audio extension the scanner reads, then "all files". Each is
 * a display name and a pattern in the form the system wants (`*.mp3;*.flac`).
 */
internal fun audioFilterSpecs(
  extensions: Collection<String>,
  audioLabel: String,
  allLabel: String,
): List<Pair<String, String>> {
  val patterns = extensions.map { "*." + it.lowercase() }
  val joined = patterns.joinToString(";")
  return listOf("$audioLabel ($joined)" to joined, "$allLabel (*.*)" to "*.*")
}

/** The picker the screens call. */
internal object NativePicker {
  /** Replaced by tests; `null` is the real thing for the platform. */
  @Volatile internal var backendOverride: PickerBackend? = null

  /** Set while a dialog is up, so a second click cannot stack another one. */
  @Volatile private var busy = false

  private val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

  /** What the dialog for [mode] is asked to show: its title and, for files, the audio filter, all in the language of the system. */
  internal fun request(mode: PickMode): PickRequest = PickRequest(
    mode = mode,
    title = when (mode) {
      PickMode.Folders -> lt("Choose music folders", "Escolher pastas de músicas")
      PickMode.Files -> lt("Choose audio files", "Escolher arquivos de áudio")
    },
    filters = if (mode == PickMode.Files) {
      audioFilterSpecs(TagReader.EXTENSIONS, lt("Audio", "Áudio"), lt("All files", "Todos os arquivos"))
    } else emptyList(),
    ownerHandle = ownerHandle(),
  )

  /**
   * Opens the dialog for [mode]. [onFiles] gets the chosen folders or files on the UI thread; [onError] gets a message to
   * show. Returns at once: the dialog lives on its own thread.
   */
  fun pick(mode: PickMode, onFiles: (List<File>) -> Unit, onError: (String) -> Unit) {
    SwingUtilities.invokeLater {
      if (busy) return@invokeLater
      val backend = backendOverride ?: if (isWindows) WindowsFileDialog else {
        legacy(mode, onFiles)
        return@invokeLater
      }
      busy = true
      val req = request(mode)
      Thread({
        val outcome = try {
          backend.pick(req)
        } catch (t: Throwable) {
          PickOutcome.Failed(HR_FAIL)
        }
        SwingUtilities.invokeLater {
          busy = false
          when (val effect = interpret(mode, outcome, ::awtFiles)) {
            is PickEffect.Use -> onFiles(effect.files)
            is PickEffect.Error -> onError(effect.message)
            PickEffect.Nothing -> Unit
          }
        }
      }, "native-file-dialog").apply { isDaemon = true }.start()
    }
  }

  /** Outside Windows the chooser is what the app always used. */
  private fun legacy(mode: PickMode, onFiles: (List<File>) -> Unit) {
    val chooser = JFileChooser().apply {
      fileSelectionMode = if (mode == PickMode.Folders) JFileChooser.DIRECTORIES_ONLY else JFileChooser.FILES_AND_DIRECTORIES
      isMultiSelectionEnabled = true
    }
    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
      onFiles(chooser.selectedFiles.toList().ifEmpty { listOfNotNull(chooser.selectedFile) })
    }
  }

  /** The AWT file dialog (native on Windows): what a file pick falls back to when the COM dialog cannot be made. */
  private fun awtFiles(): List<File> {
    val dialog = FileDialog(null as Frame?, lt("Choose audio files", "Escolher arquivos de áudio"), FileDialog.LOAD)
    dialog.isMultipleMode = true
    dialog.isVisible = true
    return dialog.files.toList()
  }

  /** The native handle of the window the person is in, or `0`; read on the UI thread. */
  private fun ownerHandle(): Long = runCatching {
    val w: Window? = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
      ?: Window.getWindows().firstOrNull { it.isShowing }
    if (w == null) 0L else Pointer.nativeValue(Native.getComponentPointer(w))
  }.getOrDefault(0L)
}

/** `IFileOpenDialog` through COM. Every method here is a call by vtable slot, so the slots are named once. */
internal object WindowsFileDialog : PickerBackend {
  private const val CLSCTX_INPROC_SERVER = 1
  private const val COINIT_APARTMENTTHREADED = 2
  private const val SIGDN_FILESYSPATH = 0x80058000.toInt()

  const val FOS_PICKFOLDERS = 0x20
  const val FOS_FORCEFILESYSTEM = 0x40
  const val FOS_ALLOWMULTISELECT = 0x200
  const val FOS_PATHMUSTEXIST = 0x800
  const val FOS_FILEMUSTEXIST = 0x1000

  /** The options each mode asks for, on top of the dialog's defaults. */
  internal fun optionsFor(mode: PickMode): Int = when (mode) {
    PickMode.Folders -> FOS_PICKFOLDERS or FOS_FORCEFILESYSTEM or FOS_ALLOWMULTISELECT or FOS_PATHMUSTEXIST
    PickMode.Files -> FOS_ALLOWMULTISELECT or FOS_FILEMUSTEXIST or FOS_FORCEFILESYSTEM
  }

  private val CLSID_FileOpenDialog = Guid.CLSID("{DC1C5A9C-E88A-4dde-A5A1-60F82A20AEF7}")
  private val IID_IFileOpenDialog = Guid.GUID("{d57c7288-d4ad-4768-be02-9d969532d960}")

  /** A COM object called by slot number. */
  private class Com(p: Pointer) : Unknown(p) {
    fun call(slot: Int, vararg args: Any?): Int = _invokeNativeInt(slot, arrayOf<Any?>(pointer, *args))
  }

  // IFileDialog / IFileOpenDialog slots (after IUnknown 0..2 and IModalWindow::Show at 3).
  private const val SHOW = 3
  private const val SET_FILE_TYPES = 4
  private const val SET_FILE_TYPE_INDEX = 5
  private const val SET_OPTIONS = 9
  private const val GET_OPTIONS = 10
  private const val SET_TITLE = 17
  private const val GET_RESULTS = 27
  // IShellItemArray and IShellItem.
  private const val ARRAY_GET_COUNT = 7
  private const val ARRAY_GET_ITEM_AT = 8
  private const val ITEM_GET_DISPLAY_NAME = 5

  override fun pick(request: PickRequest): PickOutcome {
    val ole = Ole32.INSTANCE
    val init = ole.CoInitializeEx(null, COINIT_APARTMENTTHREADED)
    // S_OK and S_FALSE both mean this thread is now in the apartment; a failure (a different mode already set) is not ours to undo.
    val initialised = COMUtils.SUCCEEDED(init)
    try {
      val created = PointerByReference()
      val hr = ole.CoCreateInstance(CLSID_FileOpenDialog, null, CLSCTX_INPROC_SERVER, IID_IFileOpenDialog, created).toInt()
      if (hr != 0) return PickOutcome.Failed(hr)
      val dialog = Com(created.value)
      try {
        return run(dialog, request)
      } finally {
        dialog.Release()
      }
    } finally {
      if (initialised) ole.CoUninitialize()
    }
  }

  private fun run(dialog: Com, request: PickRequest): PickOutcome {
    val current = IntByReference()
    var hr = dialog.call(GET_OPTIONS, current)
    if (hr != 0) return PickOutcome.Failed(hr)
    hr = dialog.call(SET_OPTIONS, current.value or optionsFor(request.mode))
    if (hr != 0) return PickOutcome.Failed(hr)
    dialog.call(SET_TITLE, WString(request.title))

    // The filter strings and the table that points at them must stay alive until Show returns.
    val keep = ArrayList<Memory>()
    if (request.filters.isNotEmpty()) {
      val table = Memory((request.filters.size * 2 * Native.POINTER_SIZE).toLong())
      request.filters.forEachIndexed { i, (name, spec) ->
        val n = wide(name).also(keep::add)
        val s = wide(spec).also(keep::add)
        table.setPointer((i * 2L) * Native.POINTER_SIZE, n)
        table.setPointer((i * 2L + 1) * Native.POINTER_SIZE, s)
      }
      keep.add(table)
      hr = dialog.call(SET_FILE_TYPES, request.filters.size, table)
      if (hr != 0) return PickOutcome.Failed(hr)
      dialog.call(SET_FILE_TYPE_INDEX, 1)
    }

    val owner: Pointer? = if (request.ownerHandle != 0L) Pointer(request.ownerHandle) else null
    hr = dialog.call(SHOW, owner)
    // The native memory of the filters is read by Show; the JIT must not free it before this line.
    java.lang.ref.Reference.reachabilityFence(keep)
    if (hr != 0) return outcomeOf(hr) { emptyList() }

    val results = PointerByReference()
    hr = dialog.call(GET_RESULTS, results)
    if (hr != 0) return PickOutcome.Failed(hr)
    val array = Com(results.value)
    try {
      return PickOutcome.Picked(pathsOf(array))
    } finally {
      array.Release()
    }
  }

  private fun pathsOf(array: Com): List<String> {
    val count = IntByReference()
    if (array.call(ARRAY_GET_COUNT, count) != 0) return emptyList()
    val out = ArrayList<String>(count.value)
    for (i in 0 until count.value) {
      val itemRef = PointerByReference()
      if (array.call(ARRAY_GET_ITEM_AT, i, itemRef) != 0) continue
      val item = Com(itemRef.value)
      try {
        val name = PointerByReference()
        if (item.call(ITEM_GET_DISPLAY_NAME, SIGDN_FILESYSPATH, name) == 0) {
          val p = name.value
          if (p != null) {
            out.add(p.getWideString(0))
            Ole32.INSTANCE.CoTaskMemFree(p)
          }
        }
      } finally {
        item.Release()
      }
    }
    return out
  }

  private fun wide(s: String): Memory = Memory((s.length + 1) * 2L).also { it.setWideString(0, s) }
}

/** Opens the Windows folder picker and adds each chosen folder to the scanned ones. */
fun chooseFolder(lib: BibliotecaState, onError: (String) -> Unit = {}) {
  NativePicker.pick(PickMode.Folders, { dirs -> dirs.forEach(lib::addRoot) }, onError)
}

/** Opens the Windows picker for audio files; [onPicked] gets what was chosen, the same as a drop on the window. */
fun chooseFiles(onPicked: (List<File>) -> Unit, onError: (String) -> Unit = {}) {
  NativePicker.pick(PickMode.Files, onPicked, onError)
}
