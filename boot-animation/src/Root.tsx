import { Composition } from "remotion";
import { Boot, BOOT_FRAMES, FPS } from "./Boot";

export const Root = () => (
  <Composition id="Boot" component={Boot} durationInFrames={BOOT_FRAMES} fps={FPS} width={1080} height={2400} />
);
