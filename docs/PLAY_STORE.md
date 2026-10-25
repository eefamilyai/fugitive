# Publishing to Google Play

Everything that can be prepared ahead of time is already in this repo. The remaining steps need your Google account.

## What's ready

| Item | Location |
|---|---|
| Signed release bundle (AAB) | `app/build/outputs/bundle/release/app-release.aab` (`./gradlew :app:bundleRelease`) |
| Upload key | `keystore/fugitive-upload.jks` + `keystore.properties`. **Back both up; if you lose them, you can't publish updates without a key reset.** Both are git-ignored. |
| App icon 512×512 | `docs/store/icon-512.png` |
| Feature graphic 1024×500 | `docs/store/feature-graphic-1024x500.png` |
| Phone screenshots | `docs/store/screenshots/` (1080×2400) |
| Privacy policy | <https://fugitive-manhunt.dshai.reggilion.com/privacy.html> (also linked in-app under Settings → About) |
| Listing text | below |

## Steps

1. **Developer account**: <https://play.google.com/console> (one-time US$25 fee plus identity verification).
2. **Set up Firebase first** ([FIREBASE_SETUP.md](FIREBASE_SETUP.md)), so the published build has online play, then
   rebuild: `./gradlew :app:bundleRelease`.
3. **Create app** → name "Fugitive: Real-World Manhunt", type *Game*, *Free*.
4. **Testing requirement for new personal accounts:** Google requires a **closed test with at least 12 testers for 14 days**
   before you can apply for production. Create *Testing → Closed testing*, upload the AAB, add your friends' Gmail
   addresses, and have them opt in and play. This works well for a party game.
5. **App content** declarations:
   - *Privacy policy*: the URL above.
   - *Ads*: No ads.
   - *App access*: All functionality available without special access (practice mode works offline).
   - *Content rating* questionnaire: Game → no violence, gambling or user-generated content beyond chat between players
     who know each other. Choose "Yes, users can interact" and "shares location with other users".
   - *Target audience*: 13+ (or 16+), not designed for children.
   - *Data safety*:
     - Location → **Precise location**: collected, shared with other users of the app (players in your game), *not* optional for online play, purpose **App functionality**, processed ephemerally / deleted with the lobby.
     - Personal info → **Name** (nickname): collected, shared with other players, App functionality.
     - Messages → **Other in-app messages** (chat): collected, shared with other players.
     - App info & performance → none. Device IDs → none (Firebase anonymous UID is a user ID, declare under *User IDs*).
     - Data is encrypted in transit: **Yes**. Users can request deletion: **Yes** (email in the policy).
   - *Foreground service permissions*: declare **Location** FGS. Use case: "Shares the player's location with other
     players in an active real-world game while the screen is off; started by the user starting a game and stopped
     when the game ends." Google asks for a short **video link**: screen-record starting a game, locking the phone, and the notification.
   - The app does **not** use `ACCESS_BACKGROUND_LOCATION`, so no background-location review is needed.
6. **Store listing**: paste the text below and upload the graphics.
7. Roll out the closed test. After 14 days, apply for **Production**.

## Listing text

**App name** (30): `Fugitive: Real-World Manhunt`

**Short description** (80):
`Hide, run & hunt for real. Live map, shrinking zone, Infection mode & more.`

**Full description**:
```
Turn your neighbourhood into a game board.

Fugitive is real-world manhunt for your phone. One person hosts, friends join with a code, and the chase begins: hunters track runners on a live map while the play zone shrinks around everyone.

🏃 HOW IT WORKS
• Create a lobby and share the 6-letter code or QR
• Pick the hunters, or let the app choose
• Runners get a head start, then the hunt is on
• Tag someone in real life, tap Catch, and they confirm on their phone
• Get caught outside the zone and you're out

🎮 GAME MODES
• Classic Manhunt: survive until the clock runs out
• Infection: every catch turns a survivor into a zombie
• Extraction: race to the evac point before time's up
• Assassin: hunt your secret target while someone hunts you

⚙️ MAKE IT YOURS
Over 60 rules to tweak: live tracking or periodic pings, hidden or visible hunters, catch range, secret catch codes, head start, game length, zone size, shrink speed, battle-royale stages, grace periods, and abilities like Radar Scan, Cloak and Sonar. Or pick a preset: Hide & Seek, Battle Royale, The Fugitive, Zombie Outbreak and more.

🗺️ BUILT FOR THE STREET
• Smooth live map with trails and heading arrows
• Heartbeat vibration when a hunter is close
• Works with the screen off
• Results with a replay of everyone's routes, awards and stats
• Practice offline against bots

🔒 PRIVATE BY DESIGN
Your location is shared only with players in your current game, only while you're playing. No ads, no tracking.

Play safe: watch for traffic and agree on boundaries before you start.
```

**Category**: Game → Action (or Casual). **Tags**: Multiplayer, Outdoor, Party.
