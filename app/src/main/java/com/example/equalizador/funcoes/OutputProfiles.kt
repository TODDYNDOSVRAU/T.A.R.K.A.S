package com.example.data.audio

import com.example.data.model.DeviceCorrection
import com.example.data.model.EqPresets
import com.example.data.model.EqSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/** The spatial stage as one output wants it. The preset is kept by its key, so the file survives renames. */
data class SpatialProfile(val enabled: Boolean = false, val presetKey: String = "", val intensity: Float = 1f)

/**
 * Everything that makes one output sound the way the user set it: the curve with its tone, balance,
 * limiter and fone correction, the spatial stage, and which preset the curve came from. The on/off
 * switch of the equalizer is not part of it: it is the user's, and stays as it is across outputs.
 */
data class OutputProfile(val eq: EqSettings, val spatial: SpatialProfile, val presetId: String? = null) {
  /** The same profile with the on/off switch of [current]. */
  fun keepingSwitchOf(current: EqSettings) = copy(eq = eq.copy(enabled = current.enabled))
}

/** What the profile manager needs from the player, so the rules run without a device. */
interface OutputProfileHost {
  /** The output the music goes to now. */
  val output: StateFlow<OutputDevice>
  /** Emits whenever the curve, the spatial stage or the preset may have changed. */
  val changes: Flow<Unit>
  fun current(): OutputProfile
  fun apply(profile: OutputProfile)
}

/** Where the profiles are kept. */
interface OutputProfileStore {
  fun profilesJson(): String?
  fun saveProfilesJson(json: String)
  /** Before profiles existed, only the fone correction was bound to an output. */
  fun legacyBindingsJson(): String?
  fun auto(): Boolean
  fun saveAuto(on: Boolean)
}

/**
 * One profile per output, which comes back by itself when the output does. Pure rules ([decide]) and
 * a small manager ([OutputProfileManager]) that follows the output and records what the user changes.
 */
object OutputProfiles {

  /** The profiles, and the fone correction carried over from before outputs had profiles. */
  data class Book(
    val profiles: Map<String, OutputProfile> = emptyMap(),
    val carry: DeviceCorrection? = null,
    /** Outputs where the user picked a correction or cleared it: no suggestion is made for them. */
    val chosen: Set<String> = emptySet()
  )

  /**
   * @param profile what to play on the output
   * @param book the book after the decision: a first-seen output is kept as it was found, and the
   * carried-over correction is spent on the first fone it fits
   */
  data class Decision(val profile: OutputProfile, val book: Book)

  /**
   * - a known output gets its own profile;
   * - a new output starts from what plays now (curve and spatial), with the fone correction decided
   *   by [OutputRouting.decide]: none on the loudspeaker, the carried-over one on a fone of that name;
   *   it is kept, so later changes elsewhere do not move it.
   */
  fun decide(output: OutputDevice, book: Book, current: OutputProfile): Decision {
    book.profiles[output.key]?.let { return Decision(it.keepingSwitchOf(current.eq), book) }
    val legacy = OutputRouting.decide(output, book.carry?.let { mapOf(OutputRouting.CARRY_OVER to it) }.orEmpty())
    val carryLeft = if (OutputRouting.CARRY_OVER in legacy.bindings) book.carry else null
    val profile = current.copy(eq = current.eq.copy(device = legacy.correction))
    val spent = book.carry != null && carryLeft == null
    return Decision(profile, book.copy(profiles = book.profiles + (output.key to profile), carry = carryLeft, chosen = if (spent) book.chosen + output.key else book.chosen))
  }

  /** The book from the bindings of the previous version: each bound output keeps its correction on what plays now. */
  fun migrate(legacy: Map<String, DeviceCorrection?>, current: OutputProfile): Book = Book(
    profiles = legacy.filterKeys { it != OutputRouting.CARRY_OVER }
      .mapValues { (_, c) -> current.copy(eq = current.eq.copy(device = c)) },
    carry = legacy[OutputRouting.CARRY_OVER],
    chosen = legacy.keys.filter { it != OutputRouting.CARRY_OVER }.toSet()
  )

  fun toJson(book: Book): String = JSONObject().apply {
    put("v", 1)
    book.carry?.let { put("carry", EqPresets.deviceToJson(it)) }
    put("profiles", JSONObject().apply { book.profiles.forEach { (k, p) -> put(k, profileToJson(p)) } })
    put("chosen", org.json.JSONArray(book.chosen.toList()))
  }.toString()

  fun profileToJson(p: OutputProfile): JSONObject = JSONObject()
    .put("eq", EqPresets.settingsToJson(p.eq))
    .put("spatial", JSONObject().put("on", p.spatial.enabled).put("preset", p.spatial.presetKey).put("intensity", p.spatial.intensity.toDouble()))
    .apply { p.presetId?.let { put("preset", it) } }

  fun fromJson(text: String?): Book? {
    if (text.isNullOrBlank()) return null
    return runCatching {
      val o = JSONObject(text)
      val profiles = o.getJSONObject("profiles")
      Book(
        profiles = profiles.keys().asSequence().associateWith { k -> profileFromJson(profiles.getJSONObject(k)) },
        carry = EqPresets.deviceFromJson(o.optJSONObject("carry")),
        chosen = o.optJSONArray("chosen")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }.orEmpty()
      )
    }.getOrNull()
  }

  fun profileFromJson(o: JSONObject): OutputProfile {
    val s = o.optJSONObject("spatial")
    return OutputProfile(
      eq = EqPresets.settingsFromJson(o.getJSONObject("eq")),
      spatial = SpatialProfile(
        enabled = s?.optBoolean("on", false) ?: false,
        presetKey = s?.optString("preset").orEmpty(),
        intensity = (s?.optDouble("intensity", 1.0) ?: 1.0).toFloat().coerceIn(0f, 1f)
      ),
      presetId = if (o.has("preset")) o.optString("preset") else null
    )
  }
}

/**
 * Follows the output and the user: when the output changes, plays that output's profile; when the
 * user changes the sound, keeps it as that output's profile. Lives in the audio layer, so the screen
 * and the view model only show the result.
 *
 * @param onApplied called after a profile was put on an output ([correctionChanged]: the fone
 * correction is not the one that played before; [announce]: false at launch, when it is quiet)
 */
class OutputProfileManager(
  private val scope: CoroutineScope,
  private val host: OutputProfileHost,
  private val store: OutputProfileStore,
  private val saveDelayMs: Long = 400,
  private val onApplied: (output: OutputDevice, correctionChanged: Boolean, announce: Boolean) -> Unit = { _, _, _ -> }
) {
  private var book = OutputProfiles.Book()
  private var started = false
  private var saveJob: Job? = null

  private val _auto = MutableStateFlow(store.auto())
  /** "Trocar a correção com o fone", now the whole profile: each output gets what was set on it. */
  val auto: StateFlow<Boolean> = _auto.asStateFlow()

  /** Whether the user (or a first look) already gave this output a profile. */
  fun isKnown(key: String) = key in book.profiles

  /** Whether the user picked or cleared a correction on this output. */
  fun hasChosen(key: String) = key in book.chosen

  /** The correction this output plays with, if any. */
  fun correctionOf(key: String): DeviceCorrection? = book.profiles[key]?.eq?.device

  /** Call once the saved sound is loaded: the first decision needs it. */
  fun start() {
    if (started) return
    started = true
    val now = host.current()
    book = OutputProfiles.fromJson(store.profilesJson())
      ?: OutputRouting.fromJson(store.legacyBindingsJson())?.let { OutputProfiles.migrate(it, now) }
      ?: OutputProfiles.Book(carry = now.eq.device)
    save()
    scope.launch {
      var first = true
      host.output.collect { out ->
        applyOutput(out, announce = !first)
        first = false
      }
    }
    scope.launch { host.changes.collect { record() } }
  }

  fun setAuto(on: Boolean) {
    _auto.value = on
    store.saveAuto(on)
    if (on) applyOutput(host.output.value, announce = false)
  }

  /** What is playing now belongs to the output playing now. */
  fun record() {
    if (!started) return
    val key = host.output.value.key
    val now = host.current()
    val before = book.profiles[key]
    if (before != null && sameSound(before, now)) return
    val picked = before != null && before.eq.device != now.eq.device
    book = book.copy(profiles = book.profiles + (key to now), chosen = if (picked) book.chosen + key else book.chosen)
    saveSoon()
  }

  private fun applyOutput(out: OutputDevice, announce: Boolean) {
    if (!_auto.value) return
    val now = host.current()
    val decision = OutputProfiles.decide(out, book, now)
    val changedBook = decision.book != book
    book = decision.book
    val target = decision.profile
    val correctionChanged = target.eq.device != now.eq.device
    if (!sameSound(target, now)) host.apply(target)
    if (changedBook) saveSoon()
    onApplied(out, correctionChanged, announce)
  }

  /** Equal in what is heard and which preset it came from, whatever the on/off switch says. */
  private fun sameSound(a: OutputProfile, b: OutputProfile) =
    OutputProfiles.profileToJson(a.keepingSwitchOf(b.eq)).toString() == OutputProfiles.profileToJson(b).toString()

  private fun saveSoon() {
    saveJob?.cancel()
    saveJob = scope.launch {
      delay(saveDelayMs)
      save()
    }
  }

  /** Exports all saved output profiles as JSON for backup or transfer between devices. */
  fun exportJson(): String = OutputProfiles.toJson(book)

  /**
   * Imports output profiles from a JSON backup, merging them into the current book.
   * Returns the number of imported profiles, or -1 if the JSON is invalid.
   */
  fun importJson(json: String): Int {
    val imported = OutputProfiles.fromJson(json) ?: return -1
    if (imported.profiles.isEmpty()) return 0
    book = book.copy(
      profiles = book.profiles + imported.profiles,
      chosen = book.chosen + imported.chosen
    )
    save()
    if (_auto.value) applyOutput(host.output.value, announce = false)
    return imported.profiles.size
  }

  private fun save() {
    store.saveProfilesJson(OutputProfiles.toJson(book))
  }
}
