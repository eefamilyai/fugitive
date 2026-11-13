import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { Avatar, Button, Card, CodeInput, Dialog, Logo, Notice, ProfileEditor, Sheet } from "../ui/components";
import { sessions, useManager } from "../session/manager";
import { setPrefs, usePrefs } from "../prefs";
import { normalizeCode } from "../util/format";

export function Home() {
  const prefs = usePrefs();
  const m = useManager();
  const nav = useNavigate();
  const params = useParams();
  const [code, setCode] = useState(normalizeCode(params.code ?? new URLSearchParams(location.search).get("joinCode") ?? ""));
  const [error, setError] = useState<string | null>(null);
  const [profile, setProfile] = useState(false);
  const busy = m.kind === "connecting";
  const online = sessions.online();
  const notice = m.kind === "idle" ? m.notice : null;

  useEffect(() => { if (params.code && code.length === 6 && online && m.kind === "idle") act(() => sessions.join(code)); }, []);

  async function act(f: () => Promise<string | null>) { setError(null); const e = await f(); if (e) setError(e); }

  return (
    <div className="page"><div className="container">
      <div className="topbar">
        <Logo />
        <div className="grow">
          <div className="grotesk" style={{ fontWeight: 700, fontSize: 24, letterSpacing: 3 }}>FUGITIVE</div>
          <div className="muted small">Real-world manhunt</div>
        </div>
        <button className="icon-btn" onClick={() => setProfile(true)} aria-label="Edit profile"><Avatar emoji={prefs.avatar} color={prefs.color} size={36} /></button>
        <button className="icon-btn" onClick={() => nav("/settings")} aria-label="Settings">⚙️</button>
      </div>
      <h2>Hi {prefs.name || "there"} 👋</h2>

      {(error || notice) && <Notice title={error ?? notice!} icon="⚠️" color="var(--danger)"
        action={<div><Button kind="ghost" small onClick={() => { setError(null); sessions.consumeNotice(); }}>Dismiss</Button></div>} />}

      {!online && <Notice title="Online play isn't set up on this server" icon="☁️"
        body="Add the Firebase web config to config.json (see web/README.md)." />}

      {prefs.lastLobby && online && (
        <Card tint="var(--signal)">
          <div className="row">
            <span style={{ fontSize: 22 }}>🔄</span>
            <div className="grow"><strong>Game in progress</strong><div className="muted small">Lobby {prefs.lastLobby}</div></div>
            <Button kind="ghost" small disabled={busy} onClick={() => setPrefs({ lastLobby: null })}>Forget</Button>
            <Button kind="ghost" small disabled={busy} onClick={() => act(sessions.rejoin)}>Rejoin</Button>
          </div>
        </Card>
      )}

      <Card>
        <div className="card-title"><span className="icon">🎮</span><div><h2>Host a game</h2><div className="muted">Create a lobby and share the code</div></div></div>
        <Button block disabled={!online || busy} loading={busy && m.message.startsWith("Creating")} onClick={() => act(sessions.create)}>＋ Create lobby</Button>
      </Card>

      <Card>
        <div className="card-title"><span className="icon">🔑</span><div><h2>Join a game</h2><div className="muted">Enter the 6-character code</div></div></div>
        <CodeInput value={code} onChange={setCode} onSubmit={() => code.length === 6 && act(() => sessions.join(code))} />
        <div style={{ height: 12 }} />
        <Button block color="var(--runner)" onColor="var(--n1)" disabled={code.length !== 6 || !online || busy}
          loading={busy && m.message.startsWith("Joining")} onClick={() => act(() => sessions.join(code))}>Join</Button>
      </Card>

      <Card tint="var(--zone)">
        <div className="row"><span style={{ fontSize: 22 }}>📱</span>
          <div className="grow"><strong>Plays with the Android app</strong>
            <div className="muted small">Web and Android players join the same lobbies. Practice mode with bots is in the Android app.</div></div></div>
      </Card>

      <Button kind="secondary" block onClick={() => nav("/stats")}>🏅 Stats & leaderboard</Button>
      <div style={{ height: 8 }} />
      <Button kind="secondary" block onClick={() => nav("/how")}>❔ How to play</Button>
    </div>

    {busy && <Dialog title={m.message} icon={<Logo size={56} />} actions={null}><p className="muted center">Hang tight…</p></Dialog>}
    {profile && <Sheet title="Your profile" onClose={() => setProfile(false)}>
      <ProfileEditor initial={{ name: prefs.name, avatar: prefs.avatar, color: prefs.color }} confirm="Save"
        onConfirm={(v) => { setPrefs(v); sessions.current?.updateProfile(v); setProfile(false); }} />
    </Sheet>}
    </div>
  );
}
