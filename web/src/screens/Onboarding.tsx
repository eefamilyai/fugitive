import { useState } from "react";
import { Button, Logo, ProfileEditor } from "../ui/components";
import { getPrefs, setPrefs } from "../prefs";

const slides = [
  { t: "The city is your arena", b: "Fugitive turns your neighbourhood into a live game of manhunt. Hunters track. Runners hide. Everyone's phone keeps score.", i: null },
  { t: "Watch the zone", b: "A play area shrinks over time. Get caught outside it and you're out. Hunters see runners on the map — runners have to be clever.", i: "🌀" },
  { t: "Your game, your rules", b: "Classic, Infection, Extraction, Assassin — and dozens of settings: pings, abilities, catch rules, zone speed and more.", i: "🎛️" },
];

export function Onboarding() {
  const [step, setStep] = useState(0);
  const p = getPrefs();
  return (
    <div className="page"><div className="container" style={{ minHeight: "100vh" }}>
      <div className="row" style={{ justifyContent: "center", gap: 6 }}>
        {[0, 1, 2, 3].map((i) => <span key={i} style={{ width: i === step ? 22 : 8, height: 8, borderRadius: 4, background: i <= step ? "var(--signal)" : "var(--outline)" }} />)}
      </div>
      {step < slides.length ? (
        <>
          <div className="col center grow" style={{ justifyContent: "center", alignItems: "center", gap: 20, minHeight: "60vh" }}>
            {slides[step].i ? <div style={{ fontSize: 90 }}>{slides[step].i}</div> : <Logo size={170} />}
            <h1 style={{ fontSize: 32 }}>{slides[step].t}</h1>
            <p className="muted" style={{ fontSize: 17, maxWidth: 440 }}>{slides[step].b}</p>
          </div>
          <div className="row">
            <Button kind="ghost" onClick={() => setStep(slides.length)}>Skip</Button>
            <span className="grow" />
            <Button onClick={() => setStep(step + 1)}>{step === slides.length - 1 ? "Get started" : "Next"}</Button>
          </div>
        </>
      ) : (
        <div className="col" style={{ gap: 14 }}>
          <h1>Who's playing?</h1>
          <p className="muted" style={{ margin: 0 }}>Others will see this in the lobby and on the map.</p>
          <ProfileEditor initial={{ name: p.name, avatar: p.avatar, color: p.color }} confirm="Let's play"
            onConfirm={(v) => setPrefs({ ...v, onboarded: true })} />
          <p className="muted small">Fugitive only shares your location with players in your current game, only while you play.</p>
        </div>
      )}
    </div></div>
  );
}
