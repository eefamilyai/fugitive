# Fugitive — web client

The browser version of Fugitive. It uses **the same Firebase Realtime Database, the same data
format and the same security rules** as the Android app, so web and Android players share lobbies.

It also runs **the same game rules**: the Kotlin engine (`core/engine`) is compiled to JavaScript
and bundled in `src/engine/`. A web host referees a game exactly like an Android host would.

## Run on your server

You need the Firebase project you set up for Android (see `../docs/FIREBASE_SETUP.md`).

1. Firebase console → Project settings → **Your apps → Add app → Web** (`</>`). Copy the config.
2. Firebase console → Authentication → Settings → **Authorized domains** → add your server's domain.
3. Create `config.json` next to `docker-compose.yml`:

   ```json
   {
     "firebase": {
       "apiKey": "AIza…",
       "authDomain": "your-project.firebaseapp.com",
       "databaseURL": "https://your-project-default-rtdb.firebaseio.com",
       "projectId": "your-project",
       "appId": "1:…:web:…"
     },
     "emulatorHost": "",
     "privacyPolicyUrl": "https://your-site/privacy.html",
     "mapTilerKey": ""
   }
   ```
   These values are public by design (security comes from the database rules), so serving them is fine.
4. `docker compose up -d --build` → the site is on port **8080**.
5. **Put HTTPS in front.** Browsers only give GPS to `https://` pages. Easiest with Caddy:

   ```
   fugitive.example.com {
     reverse_proxy 127.0.0.1:8080
   }
   ```
   (Caddy gets the certificate automatically.) nginx/Traefik + Let's Encrypt work too.

Without Docker: `npm ci && npm run build`, copy `dist/` to any static host, add `config.json`,
and make unknown paths serve `index.html` (single-page app).

The join link in the lobby QR code (`https://your-site/join/ABC123`) opens the web app straight into the lobby.

## Develop

```bash
npm install
cp public/config.example.json public/config.json   # or set "emulatorHost": "127.0.0.1"
npm run dev                                         # http://localhost:5173
```

Local multiplayer without a Firebase account: run the emulators
(`cd ../firebase && npx firebase-tools emulators:start --only database,auth --project demo-fugitive`)
and set `"emulatorHost": "127.0.0.1"` in `public/config.json`.

After changing game rules in Kotlin, regenerate the JavaScript engine and commit it:

```bash
cd .. && ./gradlew :core:engine:syncWebEngine
```

## Web vs Android

| | Web | Android |
|---|---|---|
| Lobbies, all modes and settings, chat, pings, abilities, results | ✅ | ✅ |
| Same database & rules, can be host/referee | ✅ | ✅ |
| Map types (satellite, streets 3D, terrain, topo…) | ✅ (3D view also raises real terrain) | ✅ |
| Location while screen is off / app in background | ❌ browsers pause it — keep the page open | ✅ foreground service |
| Vibration | Android browsers only (iPhone gets sound cues) | ✅ |
| Practice vs bots | ❌ | ✅ |
| "Tap the map" manual location (desktop testing) | ✅ | joystick |

## Layout

```
src/engine/        Shared Kotlin rules compiled to JS (generated — don't edit)
src/engine.ts      Typed wrapper around it
src/backend/       Firebase transport (mirrors backend/firebase on Android)
src/session/       GameSession: location sharing, referee loop, host take-over, alerts
src/map/           MapLibre GL map, styles (same catalogue as Android), controls
src/screens/       Home, Lobby, Game settings, Game, Results, Settings, How to play
```
