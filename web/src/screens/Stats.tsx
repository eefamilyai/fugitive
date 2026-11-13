import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Avatar, Button, Card, Notice, SectionHeader } from "../ui/components";
import { EMPTY_TOTALS, rate, watchLeaderboard, watchMyStats, type LeaderEntry, type Totals } from "../backend/stats";
import { currentAccount, signInWithGoogle, signOutAccount, watchAccount } from "../backend/firebase";
import { clock, distance, speed } from "../util/format";

export function StatsScreen() {
  const nav = useNavigate();
  // Start unknown (not "signed out") so a returning Google user never sees the sign-in card flash.
  const [acct, setAcct] = useState<ReturnType<typeof currentAccount> | undefined>(undefined);
  const known = acct !== undefined;
  const [mine, setMine] = useState<Totals>(EMPTY_TOTALS);
  const [board, setBoard] = useState<LeaderEntry[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => watchAccount((a) => setAcct(a ?? null)), []);
  useEffect(() => watchLeaderboard(20, setBoard), []);
  useEffect(() => (acct?.google ? watchMyStats(acct.uid, setMine) : () => {}), [acct?.uid, acct?.google]);

  async function signIn() {
    setBusy(true); setError(null);
    try { await signInWithGoogle(); }
    catch (e: any) { setError(e?.message ?? "Sign-in failed"); }
    finally { setBusy(false); }
  }

  return (
    <div className="page"><div className="container">
      <div className="topbar">
        <Button kind="ghost" small onClick={() => nav("/")}>← Back</Button>
        <div className="grow"><h2>Stats & leaderboard</h2></div>
      </div>

      {error && <Notice title={error} icon="⚠️" color="var(--danger)" />}

      {!known ? (
        <p className="muted center">Loading your account…</p>
      ) : !acct?.google ? (
        <Card>
          <div className="card-title"><span className="icon">🏅</span><div><h2>Save your progress</h2>
            <div className="muted">Sign in with Google to keep wins, catches and stats across games and devices. Guests play without saving.</div></div></div>
          <Button block disabled={busy} loading={busy} onClick={signIn}>Sign in with Google</Button>
        </Card>
      ) : (
        <Card>
          <div className="row">
            {acct.photo ? <img src={acct.photo} alt="" width={40} height={40} style={{ borderRadius: 20 }} /> : <Avatar emoji="🙂" color={0} size={40} />}
            <div className="grow"><strong>{acct.name ?? "Signed in"}</strong><div className="muted small">Stats are saved to this account</div></div>
            <Button kind="ghost" small onClick={() => signOutAccount()}>Sign out</Button>
          </div>
        </Card>
      )}

      {acct?.google && (
        <>
          <SectionHeader title="Your stats" />
          <div className="row" style={{ flexWrap: "wrap", gap: 8 }}>
            <Stat label="Games" value={mine.games} />
            <Stat label="Wins" value={mine.wins} />
            <Stat label="Win rate" value={`${Math.round(rate(mine) * 100)}%`} />
            <Stat label="Catches" value={mine.catches} />
            <Stat label="Time survived" value={clock(mine.survivedMs)} />
            <Stat label="Distance" value={distance(mine.distanceMeters)} />
            <Stat label="Top speed" value={speed(mine.topSpeedMps)} />
          </div>
        </>
      )}

      <SectionHeader title="Leaderboard" />
      <p className="muted small">Ranked by wins, then win rate, then catches.</p>
      {board.length === 0 && <p className="muted">No ranked games yet. Win one and you'll appear here.</p>}
      {board.map((r, i) => (
        <div key={r.uid} className="list-row" style={{ background: r.uid === acct?.uid ? "color-mix(in srgb, var(--signal) 12%, var(--n2))" : undefined }}>
          <span className="grotesk" style={{ width: 26, fontWeight: 700 }}>{i + 1}</span>
          <Avatar emoji={r.avatar} color={0} />
          <div className="grow"><strong>{r.name}</strong><div className="muted small">{r.games} games · {Math.round(rate(r) * 100)}% win rate</div></div>
          <div style={{ textAlign: "right" }}><div className="grotesk" style={{ fontWeight: 700 }}>{r.wins} wins</div><div className="tiny muted">{r.catches} catches</div></div>
        </div>
      ))}
    </div></div>
  );
}

function Stat({ label, value }: { label: string; value: string | number }) {
  return (
    <Card style={{ minWidth: 140, flex: "1 1 140px" }}>
      <div className="label-caps">{label}</div>
      <h3 style={{ margin: "4px 0 0" }}>{value}</h3>
    </Card>
  );
}
