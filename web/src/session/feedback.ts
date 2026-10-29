// Vibration + sound cues. Vibration works on Android browsers; sounds cover iPhones.
import { getPrefs } from "../prefs";

const patterns = {
  tick: [18], notice: [60], heartbeat: [70, 110, 110], danger: [120, 80, 120, 80, 120],
  caught: [500, 150, 300], alert: [90, 70, 90, 70, 90], success: [40, 60, 120],
} as const;
export type Cue = keyof typeof patterns;

let audio: AudioContext | null = null;
function beep(freq: number, ms: number, when = 0, gain = 0.06) {
  try {
    audio ??= new AudioContext();
    const t = audio.currentTime + when;
    const o = audio.createOscillator(); const g = audio.createGain();
    o.frequency.value = freq; o.type = "sine";
    g.gain.setValueAtTime(gain, t); g.gain.exponentialRampToValueAtTime(0.0001, t + ms / 1000);
    o.connect(g).connect(audio.destination); o.start(t); o.stop(t + ms / 1000 + 0.02);
  } catch { /* audio unavailable */ }
}
const sounds: Record<Cue, () => void> = {
  tick: () => beep(1200, 40, 0, 0.03),
  notice: () => beep(880, 90),
  heartbeat: () => { beep(70, 120, 0, 0.25); beep(60, 140, 0.18, 0.2); },
  danger: () => { beep(440, 120); beep(440, 120, 0.2); beep(440, 120, 0.4); },
  caught: () => { beep(330, 300); beep(220, 400, 0.3); },
  alert: () => { beep(660, 90); beep(880, 90, 0.15); beep(660, 90, 0.3); },
  success: () => { beep(660, 100); beep(990, 200, 0.12); },
};

/** Unlocks audio on the first user gesture (browsers block autoplay). */
export function unlockAudio() { try { audio ??= new AudioContext(); audio.resume(); } catch { /* ignore */ } }

export function cue(c: Cue) {
  const p = getPrefs();
  if (p.haptics) navigator.vibrate?.(patterns[c] as unknown as number[]);
  if (p.sounds) sounds[c]();
}
