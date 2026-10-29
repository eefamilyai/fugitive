// Device location for the web: Geolocation API (GPS on phones), or a manual position set by tapping
// the map (desktop testing / spectating). Pluggable like the Android LocationSource registry.
import type { LocationFix } from "../types";

export type LocationProblem = "permission" | "unavailable" | "insecure" | null;

export interface LocationSource {
  id: string;
  start(onFix: (fix: Omit<LocationFix, "time">) => void, onProblem: (p: LocationProblem) => void): () => void;
}

let battery: number | undefined;
(navigator as any).getBattery?.().then((b: any) => {
  const upd = () => { battery = Math.round(b.level * 100); };
  upd(); b.addEventListener("levelchange", upd);
}).catch(() => {});

export const gpsSource: LocationSource = {
  id: "gps",
  start(onFix, onProblem) {
    if (!window.isSecureContext) { onProblem("insecure"); return () => {}; }
    if (!("geolocation" in navigator)) { onProblem("unavailable"); return () => {}; }
    const id = navigator.geolocation.watchPosition(
      (p) => {
        onProblem(null);
        const c = p.coords;
        onFix({
          lat: c.latitude, lng: c.longitude, accuracy: c.accuracy ?? 50,
          bearing: c.heading != null && !Number.isNaN(c.heading) && (c.speed ?? 0) > 0.5 ? c.heading : undefined,
          speed: c.speed ?? undefined, altitude: c.altitude ?? undefined, battery, source: "web-gps",
        });
      },
      (e) => onProblem(e.code === e.PERMISSION_DENIED ? "permission" : "unavailable"),
      { enableHighAccuracy: true, maximumAge: 1000, timeout: 20000 },
    );
    return () => navigator.geolocation.clearWatch(id);
  },
};

/** Manual position: set by tapping the map; re-emitted every 2 s so the player stays "live". */
class ManualSource implements LocationSource {
  id = "manual";
  private pos: { lat: number; lng: number } | null = null;
  private emit: ((f: Omit<LocationFix, "time">) => void) | null = null;
  set(lat: number, lng: number) { this.pos = { lat, lng }; this.push(); }
  get position() { return this.pos; }
  private push() { if (this.pos && this.emit) this.emit({ ...this.pos, accuracy: 5, battery, source: "web-manual" }); }
  start(onFix: (f: Omit<LocationFix, "time">) => void, onProblem: (p: LocationProblem) => void) {
    this.emit = onFix;
    onProblem(null);
    if (!this.pos && "geolocation" in navigator && window.isSecureContext) {
      navigator.geolocation.getCurrentPosition((p) => { if (!this.pos) this.set(p.coords.latitude, p.coords.longitude); }, () => {}, { timeout: 8000 });
    }
    this.push();
    const t = window.setInterval(() => this.push(), 2000);
    return () => { window.clearInterval(t); this.emit = null; };
  }
}
export const manualSource = new ManualSource();
export const locationSources: Record<string, LocationSource> = { gps: gpsSource, manual: manualSource };
