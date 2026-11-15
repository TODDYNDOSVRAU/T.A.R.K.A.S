package com.example.ui.screens.audio

import com.example.data.audio.spatial.SpatialPreset
import java.util.Arrays
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The 3D "Spatial stage": a head seen from behind with headphones that, as the mode widens, turn into
 * two floating speakers and then into a 5.1 set. Port of the approved mock (exemplo-equalizador.html).
 * Pure Kotlin on purpose (no Compose types) so the geometry and the state targets are unit-tested;
 * SpatialStage.kt only draws what this file computes.
 */

/** The mock's coordinate space; the canvas scales it by its height. */
internal const val STAGE_W = 312f
internal const val STAGE_H = 192f

/** 2 speakers to 6 boxes takes this long, linearly. */
internal const val STAGE_MORPH_SECONDS = 0.39f

/** Distance parameter u (0 headphones on the head, 1 speakers on the 5.1 ring) for each state. */
internal fun stageTargetU(enabled: Boolean, preset: SpatialPreset): Float = when {
  !enabled -> 0f
  preset == SpatialPreset.SUBTLE -> 0.22f
  preset == SpatialPreset.STANDARD -> 0.5f
  else -> 1f
}

/** Morph parameter m: 1 only when the 5.1 boxes are shown. */
internal fun stageTargetM(enabled: Boolean, preset: SpatialPreset): Float = if (enabled && preset.virtual51) 1f else 0f

/** Lit parameter: the accent waves fade out when the effect is off. */
internal fun stageTargetOn(enabled: Boolean): Float = if (enabled) 1f else 0f

/** The animated values of the stage. Plain fields: the frame loop writes them, the draw phase reads them. */
internal class StageAnim(var u: Float = 0f, var m: Float = 0f, var onA: Float = 1f) {
  var t = 0f
  var drag = 0f
  var held = false

  /** Moves every value toward its target by [dt] seconds (already clamped by the caller). */
  fun step(dt: Float, tu: Float, tm: Float, to: Float) {
    t += dt
    u += (tu - u) * (1f - exp(-dt * 12f))
    if (abs(tu - u) < 0.002f) u = tu
    m += sign(tm - m) * min(abs(tm - m), dt / STAGE_MORPH_SECONDS)
    onA += sign(to - onA) * min(abs(to - onA), dt / 0.2f)
    if (!held) drag *= exp(-dt * 7f)
  }

  fun dragTo(value: Float) { drag = value.coerceIn(-1.3f, 1.3f) }
}

private fun ease(x: Float): Float = 1f - (1f - x.coerceIn(0f, 1f)).pow(3)
private fun lerp(a: Float, b: Float, x: Float): Float = a + (b - a) * x

/** One mesh with its pose and per-frame scratch (view space and screen coordinates of every vertex). */
internal class StageObject(
  val v: FloatArray,
  val fOff: IntArray,
  val fIdx: IntArray,
  val fRef: FloatArray,
  val fRing: BooleanArray,
  val k0: Float,
  val k1: Float,
  val bias: Float
) {
  var px = 0f
  var py = 0f
  var pz = 0f
  var s = 1f
  var a = 1f
  var yaw = 0f
  val vv = FloatArray(v.size)
  val sx = FloatArray(v.size / 3)
  val sy = FloatArray(v.size / 3)
  val faceCount: Int get() = fRing.size
}

internal class MeshBuilder {
  private val v = ArrayList<Float>()
  private val idx = ArrayList<Int>()
  private val off = arrayListOf(0)
  private val ref = ArrayList<Float>()
  private val ring = ArrayList<Boolean>()

  val vertexCount: Int get() = v.size / 3

  fun vert(x: Float, y: Float, z: Float): Int {
    v.add(x); v.add(y); v.add(z)
    return v.size / 3 - 1
  }

  fun face(indices: IntArray, rx: Float, ry: Float, rz: Float, isRing: Boolean = false) {
    for (i in indices) idx.add(i)
    off.add(idx.size)
    ref.add(rx); ref.add(ry); ref.add(rz)
    ring.add(isRing)
  }

  fun build(k0: Float, k1: Float, bias: Float = 0f): StageObject = StageObject(
    v.toFloatArray(), off.toIntArray(), idx.toIntArray(), ref.toFloatArray(), ring.toBooleanArray(), k0, k1, bias
  )
}

/** Ellipsoid centred on [c] with radii [r]; [shape] may bend each unit-sphere point first. */
private fun ellipsoid(
  c: FloatArray, r: FloatArray, nu: Int, nv: Int, k0: Float, k1: Float, bias: Float = 0f,
  shape: ((FloatArray) -> Unit)? = null
): StageObject {
  val b = MeshBuilder()
  val p = FloatArray(3)
  for (i in 0..nu) {
    val u = PI.toFloat() * i / nu
    for (j in 0 until nv) {
      val t = 2f * PI.toFloat() * j / nv
      p[0] = sin(u) * cos(t); p[1] = cos(u); p[2] = sin(u) * sin(t)
      shape?.invoke(p)
      b.vert(c[0] + p[0] * r[0], c[1] + p[1] * r[1], c[2] + p[2] * r[2])
    }
  }
  for (i in 0 until nu) for (j in 0 until nv) {
    val a = i * nv + j
    val bb = i * nv + (j + 1) % nv
    b.face(intArrayOf(a, bb, bb + nv, a + nv), c[0], c[1], c[2])
  }
  return b.build(k0, k1, bias)
}

private fun addBox(b: MeshBuilder, w: Float, h: Float, d: Float, yaw: Float, rr: Float) {
  val cs = cos(yaw)
  val sn = sin(yaw)
  fun rot(x: Float, y: Float, z: Float): FloatArray = floatArrayOf(x * cs + z * sn, y, -x * sn + z * cs)
  for (x in floatArrayOf(-w / 2, w / 2)) for (y in floatArrayOf(-h / 2, h / 2)) for (z in floatArrayOf(-d / 2, d / 2)) {
    val q = rot(x, y, z); b.vert(q[0], q[1], q[2])
  }
  for (q in arrayOf(
    intArrayOf(0, 1, 3, 2), intArrayOf(4, 5, 7, 6), intArrayOf(0, 1, 5, 4),
    intArrayOf(2, 3, 7, 6), intArrayOf(0, 2, 6, 4), intArrayOf(1, 3, 7, 5)
  )) b.face(q, 0f, 0f, 0f)
  val ring = IntArray(14)
  for (j in 0 until 14) {
    val t = 2f * PI.toFloat() * j / 14
    val q = rot(cos(t) * rr, sin(t) * rr, d / 2 + 0.01f)
    ring[j] = b.vert(q[0], q[1], q[2])
  }
  b.face(ring, 0f, 0f, 0f, isRing = true)
}

private fun catmullRom(a: Float, b: Float, c: Float, d: Float, t: Float): Float =
  0.5f * ((2 * b) + (-a + c) * t + (2 * a - 5 * b + 4 * c - d) * t * t + (-a + 3 * b - 3 * c + d) * t * t * t)

/** Skull: wider behind, occipital bulge, narrower at the nape, chin and temples. */
private fun skullShape(p: FloatArray) {
  val y = p[1]
  if (y > 0f) {
    val k = max(0.02f, 1f - y * y).pow(-0.11f)
    p[0] *= k; p[2] *= k
  }
  if (y < 0f) {
    val t = -y
    p[0] *= 1f - 0.3f * t.pow(1.5f)
    p[2] *= if (p[2] > 0f) 1f - 0.5f * t.pow(1.4f) else 1f - 0.12f * t * t
  }
  if (p[2] > 0f) {
    val q = (y - 0.1f) / 0.45f
    p[2] *= 1f + 0.07f * exp(-q * q)
  } else p[0] *= 1f + 0.06f * p[2]
}

/** Neck, trapezius and shoulders as one lathe through Catmull-Rom rings (y, centre z, radius x, radius z). */
private fun neckMesh(): StageObject {
  val rg = arrayOf(
    floatArrayOf(-.15f, .2f, .5f, .5f), floatArrayOf(-.4f, .16f, .55f, .52f), floatArrayOf(-.7f, .13f, .52f, .48f),
    floatArrayOf(-1f, .1f, .52f, .49f), floatArrayOf(-1.2f, .1f, .6f, .52f), floatArrayOf(-1.35f, .08f, .88f, .57f),
    floatArrayOf(-1.5f, .06f, 1.3f, .64f), floatArrayOf(-1.65f, .05f, 1.68f, .7f), floatArrayOf(-1.85f, .05f, 1.82f, .73f),
    floatArrayOf(-2.2f, .05f, 1.74f, .68f)
  )
  val rings = ArrayList<FloatArray>()
  for (i in 0 until rg.size - 1) for (s in 0 until 3) {
    val r = FloatArray(4) { k ->
      catmullRom(rg[max(0, i - 1)][k], rg[i][k], rg[i + 1][k], rg[min(rg.size - 1, i + 2)][k], s / 3f)
    }
    rings.add(r)
  }
  rings.add(rg[rg.size - 1])
  val nv = 40
  val b = MeshBuilder()
  for (r in rings) for (j in 0 until nv) {
    val t = 2f * PI.toFloat() * j / nv
    b.vert(r[2] * cos(t), r[0], r[1] + r[3] * sin(t))
  }
  for (i in 0 until rings.size - 1) for (j in 0 until nv) {
    val a = i * nv + j
    val bb = i * nv + (j + 1) % nv
    b.face(
      intArrayOf(a, bb, bb + nv, a + nv),
      0f, (rings[i][0] + rings[i + 1][0]) / 2f - 0.15f, (rings[i][1] + rings[i + 1][1]) / 2f
    )
  }
  return b.build(0.13f, 0.46f, 0.35f)
}

/** One of the 18 headband segments (a short curved bar). */
private fun bandSegment(s: Int, n: Int, a0: Float, a1: Float): StageObject {
  val b = MeshBuilder()
  var cx = 0f
  var cy = 0f
  for (q in intArrayOf(s, s + 1)) {
    val t = a0 + (a1 - a0) * q / n
    val c = cos(t)
    val sn = sin(t)
    val px = 0.92f * c
    val py = 1.12f * sn - 0.04f
    cx += px / 2; cy += py / 2
    for ((dr, dz) in arrayOf(-.035f to -.11f, .035f to -.11f, .035f to .11f, -.035f to .11f)) {
      b.vert(px + c * dr, py + sn * dr, dz)
    }
  }
  for (k in 0 until 4) b.face(intArrayOf(k, (k + 1) % 4, 4 + (k + 1) % 4, 4 + k), cx, cy, 0f)
  return b.build(0.5f, 0.44f)
}

/** Headphone cup = speaker disc whose cone faces the head (local +z). */
private fun cupMesh(): StageObject {
  val b = MeshBuilder()
  val n = 22
  val r = 0.36f
  val h = 0.26f
  val a = IntArray(n)
  val bb = IntArray(n)
  for (j in 0 until n) {
    val t = 2f * PI.toFloat() * j / n
    val p = cos(t) * r
    val q = sin(t) * r
    a[j] = b.vert(p, q, -h / 2)
    bb[j] = b.vert(p, q, h / 2)
  }
  for (j in 0 until n) {
    val nx = (j + 1) % n
    b.face(intArrayOf(a[j], bb[j], bb[nx], a[nx]), 0f, 0f, 0f)
  }
  b.face(a, 0f, 0f, 0f)
  b.face(bb, 0f, 0f, 0f)
  for (rr in floatArrayOf(0.26f, 0.1f)) {
    val ring = IntArray(16)
    for (j in 0 until 16) {
      val t = 2f * PI.toFloat() * j / 16
      ring[j] = b.vert(cos(t) * rr, sin(t) * rr, 0.14f)
    }
    b.face(ring, 0f, 0f, 0f, isRing = true)
  }
  return b.build(0.5f, 0.44f)
}

internal class StageCup(val obj: StageObject, val sd: Int) {
  var ang = 0f
}

internal class StageSpeaker(
  val obj: StageObject, val id: String, val dl: Float, val h: Float, val ang: Float, val sd: Int,
  val toX: Float, val toY: Float, val toZ: Float
) {
  var p = 0f
}

/** Items of one frame, filled by [StageScene.frame] and drawn back to front. */
internal class StageScene {
  val r = 2.6f
  val floorY = -2.2f
  private val camD = 14f

  val objs: Array<StageObject>
  val bandObjs: List<StageObject>
  val cups: List<StageCup>
  val speakers: List<StageSpeaker>

  init {
    val list = ArrayList<StageObject>()
    list.add(ellipsoid(floatArrayOf(0f, 0f, .04f), floatArrayOf(.8f, .96f, .93f), 34, 48, .13f, .5f, 0f, ::skullShape))
    for (sd in intArrayOf(-1, 1)) {
      list.add(ellipsoid(floatArrayOf(sd * .8f, -.06f, 0f), floatArrayOf(.09f, .25f, .15f), 10, 14, .13f, .5f) { p ->
        if (p[1] > 0f) p[2] *= 1f + .25f * p[1] else p[2] *= 1f - .35f * p[1] * p[1]
        if (p[2] > 0f) p[0] += sd * 1.1f * p[2]
      })
    }
    list.add(neckMesh())
    val band = ArrayList<StageObject>()
    val n = 18
    val a0 = 12f * PI.toFloat() / 180f
    val a1 = 168f * PI.toFloat() / 180f
    for (s in 0 until n) band.add(bandSegment(s, n, a0, a1))
    list.addAll(band)
    val cupList = ArrayList<StageCup>()
    for (sd in intArrayOf(-1, 1)) {
      val o = cupMesh()
      list.add(o)
      cupList.add(StageCup(o, sd))
    }
    val spk = ArrayList<StageSpeaker>()
    // id, angle from the listener's front, fed from the left (0) or right (1) cup, delay, w, h, d, floor y
    class Def(val id: String, val deg: Float, val from: Int, val dl: Float, val w: Float, val h: Float, val d: Float, val y: Float)
    for (dfn in listOf(
      Def("L", -30f, 0, 0f, .5f, .72f, .42f, 0f), Def("R", 30f, 1, 0f, .5f, .72f, .42f, 0f),
      Def("C", 0f, 0, .03f, .84f, .4f, .4f, 0f), Def("Ls", -110f, 0, .06f, .5f, .72f, .42f, 0f),
      Def("Rs", 110f, 1, .06f, .5f, .72f, .42f, 0f), Def("LFE", 62f, 1, .09f, .7f, .7f, .7f, floorY + .35f)
    )) {
      val t = dfn.deg * PI.toFloat() / 180f
      val b = MeshBuilder()
      addBox(b, dfn.w, dfn.h, dfn.d, -t, min(dfn.w, dfn.h) * .3f)
      val o = b.build(.2f, .5f)
      list.add(o)
      spk.add(StageSpeaker(o, dfn.id, dfn.dl, dfn.h, t, dfn.from, r * sin(t), dfn.y, -r * cos(t)))
    }
    objs = list.toTypedArray()
    bandObjs = band
    cups = cupList
    speakers = spk
  }

  // ---- per-frame output -------------------------------------------------------------------------
  private val totalFaces = objs.sumOf { it.faceCount }
  private val cap = totalFaces + 256
  val itemType = IntArray(cap) // 0 face, 1 accent line, 2 gray line
  val itemObj = IntArray(cap)
  val itemFace = IntArray(cap)
  val itemTone = IntArray(cap) // palette index 0..255 of the fill
  val itemAlpha = FloatArray(cap)
  val itemLine = FloatArray(cap * 4)
  val itemWidth = FloatArray(cap)
  private val keys = LongArray(cap)
  var itemCount = 0
    private set

  val floorX = FloatArray(65)
  val floorY2 = FloatArray(65)
  var floorAlpha = 0f
    private set
  val labelX = FloatArray(6)
  val labelY = FloatArray(6)
  val labelAlpha = FloatArray(6)
  var lit = 1f
    private set

  // camera
  private var cyw = 1f
  private var syw = 0f
  private var cp = 1f
  private var sp = 0f
  private var kk = 56f
  private var cy0 = 84f
  private var halfW = STAGE_W / 2f
  private val tmp = FloatArray(6)

  private fun view(x: Float, y: Float, z: Float, out: FloatArray, o: Int) {
    val vx = x * cyw + z * syw
    val vz = -x * syw + z * cyw
    out[o] = vx
    out[o + 1] = y * cp - vz * sp
    out[o + 2] = y * sp + vz * cp
  }

  private fun projX(vx: Float, vz: Float): Float = halfW + vx * (kk * camD / (camD - vz))
  private fun projY(vy: Float, vz: Float): Float = cy0 - vy * (kk * camD / (camD - vz))

  private fun line(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, type: Int, al: Float, w: Float) {
    if (itemCount >= cap) return
    view(ax, ay, az, tmp, 0)
    view(bx, by, bz, tmp, 3)
    val i = itemCount++
    itemType[i] = type
    itemObj[i] = -1
    itemAlpha[i] = al
    itemWidth[i] = w
    itemLine[4 * i] = projX(tmp[0], tmp[2])
    itemLine[4 * i + 1] = projY(tmp[1], tmp[2])
    itemLine[4 * i + 2] = projX(tmp[3], tmp[5])
    itemLine[4 * i + 3] = projY(tmp[4], tmp[5])
    keys[i] = key(camD - (tmp[2] + tmp[5]) / 2f, i)
  }

  private fun key(depth: Float, i: Int): Long =
    (java.lang.Float.floatToRawIntBits(max(0f, depth)).toLong() shl 32) or i.toLong()

  private fun arc(cx: Float, cy: Float, cz: Float, beta: Float, rad: Float, span: Float, al: Float) {
    if (al < 0.02f) return
    var px = 0f
    var pz = 0f
    for (k in 0..8) {
      val t = beta - span + 2f * span * k / 8f
      val x = cx + rad * cos(t)
      val z = cz + rad * sin(t)
      if (k > 0) line(px, cy, pz, x, cy, z, 1, al, 1.25f)
      px = x; pz = z
    }
  }

  /**
   * Poses the scene for [a] and fills the item arrays; returns the number of items, already sorted
   * so that [sortedItem] walks them back to front. [widthUnits] is the canvas width in mock units.
   */
  fun frame(a: StageAnim, inten: Float, widthUnits: Float): Int {
    itemCount = 0
    halfW = widthUnits / 2f
    val u = a.u
    val e = ease(a.m)
    val mt = a.m * STAGE_MORPH_SECONDS
    lit = a.onA
    val yaw = 0.14f * sin(a.t * 0.5f) + a.drag
    val pit = lerp(0.14f, 0.4f, u) + 0.1f * e
    kk = lerp(56f, 42f, u) - 8f * e
    cy0 = lerp(84f, 74f, u) - 4f * e
    cyw = cos(yaw); syw = sin(yaw); cp = cos(pit); sp = sin(pit)

    // pose
    val bp = ease(u / 0.15f)
    // Indexed loops: this runs every frame, and an iterator per loop would allocate.
    for (bi in 0 until bandObjs.size) { val b = bandObjs[bi]; b.a = 1f - bp; b.px = 0f; b.py = 0.3f * bp; b.pz = 0f }
    val crr = lerp(0.95f, r, u)
    val caa = lerp(PI.toFloat() / 2f, PI.toFloat() / 4.5f, u.pow(1.5f))
    for (ci in 0 until cups.size) {
      val c = cups[ci]
      val p = ease(mt / 0.18f)
      val ang = c.sd * caa
      c.ang = ang
      c.obj.a = 1f - p
      c.obj.s = lerp(1f, 1.3f, u) * (1f - 0.3f * p)
      c.obj.yaw = -ang
      c.obj.px = crr * sin(ang); c.obj.py = -0.02f; c.obj.pz = -crr * cos(ang)
    }
    for (si in 0 until speakers.size) {
      val s = speakers[si]
      val p = ease((mt - s.dl) / 0.3f)
      val fr = cups[s.sd].obj
      val hop = if (s.id == "L" || s.id == "R") 0f else 0.35f
      s.p = p
      s.obj.a = min(1f, p * 4f)
      s.obj.s = lerp(0.5f, 1f, p)
      s.obj.px = lerp(fr.px, s.toX, p)
      s.obj.py = lerp(fr.py, s.toY, p) + hop * sin(PI.toFloat() * p)
      s.obj.pz = lerp(fr.pz, s.toZ, p)
    }

    // faces
    for (oi in objs.indices) {
      val o = objs[oi]
      if (o.a < 0.01f) continue
      addObject(oi, o)
    }
    // stands down to the floor
    for (si in 0 until speakers.size) {
      val s = speakers[si]
      if (s.id != "LFE" && s.p > 0.05f) {
        val o = s.obj
        line(o.px, o.py - s.h / 2f * o.s, o.pz, o.px, floorY, o.pz, 2, 0.6f * s.p * s.p, 1f)
      }
    }
    // waves: from the cups outward (stereo) or from the boxes to the head (5.1)
    val wu = min(1f, u / 0.15f)
    if (lit > 0.02f && e < 0.98f && wu > 0.02f) {
      for (ci in 0 until cups.size) for (i in 0 until 2) {
        val c = cups[ci]
        val ph = frac(a.t * 0.5f + i / 2f)
        arc(
          c.obj.px, c.obj.py, c.obj.pz, atan2(cos(c.ang), -sin(c.ang)), 0.3f + ph * max(0.3f, crr - 1.25f), 0.5f,
          (1f - ph) * min(1f, ph * 5f) * (0.35f + 0.65f * inten) * (1f - e) * lit * wu
        )
      }
    }
    if (lit > 0.02f && e > 0.02f) {
      for (n in 0 until speakers.size) for (i in 0 until 2) {
        val s = speakers[n]
        val ph = frac(a.t * 0.55f + i / 2f + n * 0.17f)
        arc(
          s.toX, s.toY, s.toZ, atan2(cos(s.ang), -sin(s.ang)), 0.4f + ph * (r - 1.6f), 0.42f,
          (1f - ph) * min(1f, ph * 5f) * (0.3f + 0.7f * inten) * s.p * lit
        )
      }
    }
    // floor ring
    val fl = max(e, 0.55f * ease((u - 0.5f) / 0.5f))
    floorAlpha = if (fl > 0.02f) 0.35f * fl else 0f
    if (floorAlpha > 0f) {
      for (k in 0..64) {
        val t = 2f * PI.toFloat() * k / 64
        view(r * sin(t), floorY, r * cos(t), tmp, 0)
        floorX[k] = projX(tmp[0], tmp[2])
        floorY2[k] = projY(tmp[1], tmp[2])
      }
    }
    // channel names
    for (n in 0 until speakers.size) {
      val s = speakers[n]
      if (s.p < 0.6f) { labelAlpha[n] = 0f; continue }
      labelAlpha[n] = (s.p - 0.6f) / 0.4f
      view(s.obj.px, s.obj.py + s.h / 2f + 0.12f, s.obj.pz, tmp, 0)
      labelX[n] = max(16f, min(widthUnits - 16f, projX(tmp[0], tmp[2])))
      labelY[n] = max(14f, projY(tmp[1], tmp[2]))
    }
    Arrays.sort(keys, 0, itemCount)
    return itemCount
  }

  /** Index of the item to draw at position [pos] (0 = farthest). */
  fun sortedItem(pos: Int): Int = (keys[itemCount - 1 - pos] and 0xffffffffL).toInt()

  private fun frac(x: Float): Float = x - floor(x)

  private val lx = 0.42f
  private fun addObject(oi: Int, o: StageObject) {
    val oc = cos(o.yaw)
    val os = sin(o.yaw)
    val n = o.v.size / 3
    for (i in 0 until n) {
      val x = o.v[3 * i]; val y = o.v[3 * i + 1]; val z = o.v[3 * i + 2]
      val wx = o.px + (x * oc + z * os) * o.s
      val wy = o.py + y * o.s
      val wz = o.pz + (-x * os + z * oc) * o.s
      view(wx, wy, wz, o.vv, 3 * i)
      o.sx[i] = projX(o.vv[3 * i], o.vv[3 * i + 2])
      o.sy[i] = projY(o.vv[3 * i + 1], o.vv[3 * i + 2])
    }
    for (f in 0 until o.faceCount) {
      if (itemCount >= cap) return
      val s0 = o.fOff[f]
      val s1 = o.fOff[f + 1]
      val cnt = s1 - s0
      var nx = 0f; var ny = 0f; var nz = 0f; var cx = 0f; var cy = 0f; var cz = 0f
      for (k in 0 until cnt) {
        val ia = 3 * o.fIdx[s0 + k]
        val ib = 3 * o.fIdx[s0 + (k + 1) % cnt]
        val ax = o.vv[ia]; val ay = o.vv[ia + 1]; val az = o.vv[ia + 2]
        val bx = o.vv[ib]; val by = o.vv[ib + 1]; val bz = o.vv[ib + 2]
        nx += (ay - by) * (az + bz)
        ny += (az - bz) * (ax + bx)
        nz += (ax - bx) * (ay + by)
        cx += ax / cnt; cy += ay / cnt; cz += az / cnt
      }
      val len = sqrt(nx * nx + ny * ny + nz * nz)
      if (len < 1e-9f) continue
      nx /= len; ny /= len; nz /= len
      // reference point inside the body: the normal must point away from it
      val rx = o.fRef[3 * f]; val ry = o.fRef[3 * f + 1]; val rz = o.fRef[3 * f + 2]
      view(
        o.px + (rx * oc + rz * os) * o.s, o.py + ry * o.s, o.pz + (-rx * os + rz * oc) * o.s, tmp, 0
      )
      if (nx * (cx - tmp[0]) + ny * (cy - tmp[1]) + nz * (cz - tmp[2]) < 0f) { nx = -nx; ny = -ny; nz = -nz }
      if (nx * (0f - cx) + ny * (0f - cy) + nz * (camD - cz) <= 0f) continue
      val ring = o.fRing[f]
      val l = min(1f, max(0f, nx * -0.42f + ny * 0.62f + nz * 0.66f) + 0.24f * max(0f, nx * 0.72f + ny * 0.1f + nz * 0.69f))
      val tone = if (ring) 0.06f else o.k0 + o.k1 * l
      val i = itemCount++
      itemType[i] = 0
      itemObj[i] = oi
      itemFace[i] = f
      itemTone[i] = (tone.coerceIn(0f, 1f) * 255f).toInt()
      itemAlpha[i] = o.a
      val dz = camD - cz
      keys[i] = key(sqrt(cx * cx + cy * cy + dz * dz) - (if (ring) 0.08f else 0f) + o.bias, i)
    }
  }
}
