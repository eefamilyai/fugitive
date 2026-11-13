import { useEffect } from "react";
import { Navigate, Route, Routes, useLocation, useNavigate } from "react-router-dom";
import { usePrefs } from "./prefs";
import { sessions, useManager, useSession } from "./session/manager";
import { Home } from "./screens/Home";
import { Onboarding } from "./screens/Onboarding";
import { LobbyScreen } from "./screens/Lobby";
import { GameSettingsScreen } from "./screens/GameSettings";
import { GameScreen } from "./screens/Game";
import { ResultsScreen } from "./screens/Results";
import { AppSettings } from "./screens/AppSettings";
import { HowToPlay } from "./screens/HowToPlay";
import { StatsScreen } from "./screens/Stats";

/** The active lobby's phase decides the screen, exactly like the Android app. */
function useSessionRouting() {
  const m = useManager();
  const session = m.kind === "active" ? m.session : null;
  const phase = useSession(session, (s) => {
    const l = s.lobby;
    if (!l) return "lobby";
    if (l.game?.result || l.info.phase === "RESULTS") return "results";
    if (l.info.phase === "PLAYING" && l.game) return "game";
    return "lobby";
  });
  const nav = useNavigate();
  const loc = useLocation();
  useEffect(() => {
    const path = loc.pathname;
    if (!session) {
      if (["/lobby", "/game", "/results", "/lobby/settings"].includes(path)) nav("/", { replace: true });
      return;
    }
    const target = phase === "game" ? "/game" : phase === "results" ? "/results" : "/lobby";
    const allowed = target === "/lobby" ? ["/lobby", "/lobby/settings", "/settings", "/how"] : target === "/game" ? ["/game", "/settings"] : [target];
    if (!allowed.includes(path)) nav(target, { replace: true });
  }, [session, phase, loc.pathname]);
  // Keep the screen awake during a game.
  useEffect(() => {
    if (phase !== "game") return;
    let lock: any = null;
    const req = () => (navigator as any).wakeLock?.request("screen").then((l: any) => { lock = l; }).catch(() => {});
    req();
    const vis = () => document.visibilityState === "visible" && req();
    document.addEventListener("visibilitychange", vis);
    return () => { document.removeEventListener("visibilitychange", vis); lock?.release?.(); };
  }, [phase]);
}

export function App() {
  const prefs = usePrefs();
  useSessionRouting();
  useEffect(() => { const s = sessions.current; return () => void s; }, []);
  if (!prefs.onboarded) return <Onboarding />;
  return (
    <Routes>
      <Route path="/" element={<Home />} />
      <Route path="/join/:code" element={<Home />} />
      <Route path="/lobby" element={<LobbyScreen />} />
      <Route path="/lobby/settings" element={<GameSettingsScreen />} />
      <Route path="/game" element={<GameScreen />} />
      <Route path="/results" element={<ResultsScreen />} />
      <Route path="/settings" element={<AppSettings />} />
      <Route path="/how" element={<HowToPlay />} />
      <Route path="/stats" element={<StatsScreen />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
