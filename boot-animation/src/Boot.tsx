import React from "react";
import { AbsoluteFill, Easing, interpolate, staticFile, useCurrentFrame } from "remotion";

export const FPS = 60;
/** 2.5 s. The last frames hold the finished mark, which the app then dissolves into its first screen. */
export const BOOT_FRAMES = 150;

/** Same value as @color/tarkas_ink, so the window, the video and the app meet without a seam. */
const INK = "#07090B";
const ICE = "#7EE3EA";
const WHITE = "#EAF7F8";

/** Starts and ends gently: a pen that accelerates through the stroke and lands softly. */
const DRAW = Easing.bezier(0.65, 0, 0.35, 1);
/** Fast out, long settle. */
const SETTLE = Easing.bezier(0.16, 1, 0.3, 1);

const clamp = { extrapolateLeft: "clamp", extrapolateRight: "clamp" } as const;
const ramp = (f: number, from: number, to: number, ease = DRAW) => interpolate(f, [from, to], [0, 1], { ...clamp, easing: ease });

/** The mark on its 132 x 80 grid (the same geometry as the launcher icon and the app's logo). */
const PARTS: { d: string; from: number; to: number; color: string }[] = [
  { d: "M0 40H29", from: 10, to: 34, color: ICE },
  { d: "M66 6L102 40L66 74L30 40Z", from: 24, to: 74, color: WHITE },
  { d: "M103 40H132", from: 58, to: 80, color: WHITE },
  { d: "M100 40L126 8", from: 64, to: 86, color: WHITE },
  { d: "M100 40L126 72", from: 68, to: 90, color: WHITE },
];

const MARK_W = 380;
const K = MARK_W / 132;

const fontFace = `@font-face { font-family: "Inter"; src: url("${staticFile("inter.ttf")}"); font-weight: 100 900; }`;

/**
 * The opening of T.A.R.K.A.S.:
 * 1. a thin line of light draws the mark, the ice bar first, as a pen would;
 * 2. the line gains its full weight, and a soft bloom answers it once;
 * 3. one glint crosses the finished mark while the name resolves under it, its spacing closing in;
 * 4. the camera has drifted in by a few percent the whole time, so nothing is ever quite still.
 */
export const Boot: React.FC = () => {
  const f = useCurrentFrame();

  // A slow dolly-in across the whole piece.
  const push = interpolate(f, [0, BOOT_FRAMES], [0.955, 1], { ...clamp, easing: Easing.out(Easing.cubic) });
  // Thin pen line while drawing, then the stroke swells to the logo's weight.
  const weight = interpolate(ramp(f, 80, 108, SETTLE), [0, 1], [3, 12]);
  const bloom = interpolate(f, [84, 106, 145], [0, 0.42, 0.12], clamp);
  const sweep = ramp(f, 100, 134, Easing.bezier(0.45, 0, 0.2, 1));
  const name = ramp(f, 98, 136, SETTLE);

  const strokes = (color: (c: string) => string, width: number) =>
    PARTS.map((p, i) => {
      const t = ramp(f, p.from, p.to);
      if (t <= 0) return null;
      // Once complete, the path is drawn without a dash, so the closed outline joins at its start corner.
      const done = t >= 1;
      return (
        <path
          key={i}
          d={p.d}
          pathLength={1}
          strokeDasharray={done ? undefined : "1 1"}
          strokeDashoffset={done ? undefined : 1 - t}
          fill="none"
          stroke={color(p.color)}
          strokeWidth={width}
          strokeLinejoin="miter"
          strokeMiterlimit={4}
          strokeLinecap="butt"
        />
      );
    });

  // The travelling head of each stroke: a small bright point while the line is still being drawn.
  const heads = PARTS.map((p, i) => {
    const t = ramp(f, p.from, p.to);
    if (t <= 0 || t >= 1) return null;
    const fade = Math.min(1, t * 6, (1 - t) * 6);
    return <circle key={i} r={weight * 0.9} fill="#FFFFFF" opacity={0.85 * fade} style={{ offsetPath: `path("${p.d}")`, offsetDistance: `${t * 100}%` } as React.CSSProperties} />;
  });

  return (
    <AbsoluteFill style={{ backgroundColor: INK }}>
      <style>{fontFace}</style>
      <AbsoluteFill style={{ alignItems: "center", justifyContent: "center", transform: `scale(${push})` }}>
        <div style={{ display: "flex", flexDirection: "column", alignItems: "center", marginTop: -40 }}>
          <svg width={MARK_W + 120} height={80 * K + 120} viewBox={`${-60 / K} ${-60 / K} ${132 + 120 / K} ${80 + 120 / K}`} style={{ overflow: "visible" }}>
            <defs>
              <filter id="bloom" x="-80%" y="-130%" width="260%" height="360%" colorInterpolationFilters="sRGB">
                <feGaussianBlur stdDeviation="13" />
              </filter>
              <linearGradient id="band" x1="0" y1="0" x2="1" y2="0.35">
                <stop offset="0" stopColor="#fff" stopOpacity="0" />
                <stop offset="0.5" stopColor="#fff" stopOpacity="1" />
                <stop offset="1" stopColor="#fff" stopOpacity="0" />
              </linearGradient>
              <mask id="glint" maskUnits="userSpaceOnUse" x="-20" y="-20" width="172" height="120">
                <rect x={-60 + 210 * sweep} y="-20" width="44" height="120" fill="url(#band)" />
              </mask>
            </defs>
            {/* Bloom: the same strokes, blurred, answering once when the line gains weight. */}
            <g filter="url(#bloom)" opacity={bloom}>
              {strokes(() => ICE, weight * 1.6)}
            </g>
            <g>{strokes((c) => c, weight)}</g>
            <g>{heads}</g>
            {sweep > 0 && sweep < 1 && (
              <g mask="url(#glint)" opacity={0.9}>
                {strokes(() => "#FFFFFF", weight)}
              </g>
            )}
          </svg>
          <div
            style={{
              marginTop: 70,
              fontFamily: "Inter",
              fontWeight: 600,
              fontSize: 44,
              color: WHITE,
              letterSpacing: `${interpolate(name, [0, 1], [0.62, 0.3])}em`,
              // Letter spacing adds a trailing gap after the last letter; this keeps the name centred.
              marginRight: `-${interpolate(name, [0, 1], [0.62, 0.3])}em`,
              opacity: name,
              filter: `blur(${interpolate(name, [0, 1], [10, 0])}px)`,
              transform: `translateY(${interpolate(name, [0, 1], [14, 0])}px)`,
            }}
          >
            T.A.R.K.A.S.
          </div>
        </div>
      </AbsoluteFill>
    </AbsoluteFill>
  );
};
