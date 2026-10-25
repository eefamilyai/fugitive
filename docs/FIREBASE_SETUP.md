# Firebase setup (online multiplayer)

Online games use Firebase Realtime Database and Anonymous Authentication. The free **Spark** plan is plenty:
a 30-minute, 8-player game at 2-second updates transfers only a few MB.

## 1. Create the project (about 10 minutes)

1. Go to <https://console.firebase.google.com> → **Add project** → name it (e.g. `fugitive`). Analytics: off.
2. **Build → Authentication → Get started → Sign-in method → Anonymous → Enable.**
3. **Build → Realtime Database → Create database** → choose the location nearest your players → *Start in locked mode*.
4. Open the **Rules** tab, paste the contents of [`firebase/database.rules.json`](../firebase/database.rules.json), then **Publish**.
5. **Project settings (gear) → Your apps → Android**: package name `com.jaryl.fugitive`, then **Register app**.
   Download **google-services.json** and put it at `app/google-services.json`.
6. Rebuild. The home screen's "Online play isn't set up" notice disappears.

Optional, but recommended before publishing: add your release SHA-1/SHA-256 fingerprints under Project settings
(`keytool -list -v -keystore keystore/fugitive-upload.jks -alias upload`, plus the "App signing key" fingerprint shown in Play Console).

You can also deploy the rules from the command line:

```bash
cd firebase
npx firebase-tools login
npx firebase-tools use --add          # pick your project
npx firebase-tools deploy --only database
```

## 2. Clean up old lobbies (optional)

Lobbies delete themselves when the last player leaves. Abandoned ones (for example, everyone's phone died) remain, which
is harmless at this scale. If you upgrade to the Blaze plan you can add a scheduled Cloud Function that deletes
lobbies whose `info/createdAt` is older than 24 hours.

## 3. Local testing without a Firebase account

```bash
cd firebase
npx firebase-tools emulators:start --only database,auth --project demo-fugitive
```

Then add this line to `local.properties`: `fugitive.firebaseEmulatorHost=10.0.2.2` (the Android emulator's alias for your PC; use your
PC's LAN IP for a real phone on the same Wi-Fi). Debug builds connect to the emulators automatically with the
real security rules applied. Release builds never use the emulator.
