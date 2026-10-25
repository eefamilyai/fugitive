# Architecture & extension guide

Fugitive is built to grow. Every major concept (game modes, settings, abilities, presets, transports,
location sources, map styles, map overlays) is a registry or an interface. Adding a feature usually means
adding one class and registering it, without editing existing screens.

## Module graph

```
            app ──► feature/* ──► core/ui ──► core/designsystem
             │          │            │
             │          ├──► core/map ───────────────┐
             ▼          ▼                             ▼
     backend/*  ──► core/data ──► core/location   core/engine ──► core/model
```

* **core/model** and **core/engine** are plain Kotlin/JVM, with no Android dependency. All rules live here and are
  covered by fast unit tests (`./gradlew :core:engine:test`).
* **core/data** knows only the `GameBackend` interface. It never imports Firebase.
* Build logic is in `build-logic/` (convention plugins). A new feature module's build file is just
  `plugins { alias(libs.plugins.fugitive.android.feature) }`.

## How a game runs

1. The host's device creates a `GameState` with `GameFactory` (roles, zone plan, objectives, catch codes).
2. **Every device** shares its location (`GameSession.startLocationSharing`) and renders a `GameProjection`:
   the state as *this* player may see it (`VisibilityRules`), recomputed twice a second.
3. Players never modify the game directly. They submit `GameAction`s (catch, respond, use ability …).
4. The host runs the **Referee** once per second: a *pure function*
   `(state, members, locations, actions, now) → (new state, events, consumed actions)`.
   It validates actions, applies catches, enforces the zone and disconnects, runs mode hooks and checks for a win.
5. If the host disappears, the earliest-joined online player takes over as referee (`claimHost`) and the
   game continues seamlessly, because the referee is stateless.
6. The zone is a precomputed timeline (`ZonePlan`), so every device interpolates the same circle locally
   with no network traffic. Timestamps use the server clock (`ServerClock`).

## Add a game mode

```kotlin
class JuggernautMode : BaseMode() {
    override val id = "juggernaut"
    override val name = "Juggernaut"
    override val tagline = "One unstoppable hunter. Everyone else survives together."
    override val icon = "bolt"
    override val accent = 0xFFEF4444
    override val rules = listOf("…")

    // Optional: mode-specific settings appear automatically in the lobby editor.
    val lives = NumberSetting("jug_lives", "Runner lives", category = SettingCategory.MODE, default = 2, min = 1, max = 5)
    override fun settings() = listOf(lives)

    override fun onCatch(ctx: RuleContext, catcher: PlayerState?, target: PlayerState) { /* … */ }
    override fun checkResult(ctx: RuleContext) = CoreOutcomes.huntersVsRunnersResult(ctx)
}
```

Register it in any Koin module: `single { JuggernautMode() } bind GameMode::class`.
`dataModule` collects every `GameMode`, `Ability`, `GamePreset` and `LocationSource` with `getAll()`.
How to Play, mode cards, the settings form, the HUD labels and the bots all pick it up automatically.

Hooks available on `GameMode`: `assignRoles`, `setup`, `lateJoinRole`, `canCatch`, `onCatch`, `onPlayerOut`,
`onTick`, `handleAction` (for custom action types), `visibility` (per viewer/subject pair), `checkResult`,
`roleLabel`, `objectiveFor`, `defaults`, `hiddenSettings`, `validate`.

## Add a setting

Declare it (in `CoreSettings`, a mode, or an ability):

```kotlin
val NIGHT_MODE = ToggleSetting("night_mode", "Night mode", "Runners' pings are blurred.", SettingCategory.VISIBILITY, default = false)
```

Then read it anywhere with `settings[NIGHT_MODE]`. The editor renders it, with validation, units, sliders,
quick-pick chips and conditional visibility (`visibleWhen`). It syncs to every player and is stored
with the lobby. Nothing else is required.

## Add an ability

Subclass `Ability` (it gets enabled/uses/duration/cooldown settings for free). The default `activate`
adds a timed `Effect`; read it in rules with `ctx.effectActive(id) { … }`. Register with
`single { MyAbility() } bind Ability::class`. The HUD shows a button with cooldown ring for eligible roles.

## Add a transport (backend)

Implement `GameBackend` and `LobbyConnection` (see `backend/local` for a compact reference implementation)
and bind it: `single { MyBackend() } bind GameBackend::class`. Examples: a WebSocket server, Supabase, or LAN play.

## Add a map overlay or style

* Overlays: subclass `MapLayer` (own sources and style layers, `install`/`render`), then add it to the list
  passed to `GameMap`. See `ZoneLayer`, `PlayersLayer`, `TrailsLayer`, `PinsLayer`, `RingLayer`.
* Styles: `MapStyles.register(MapStyle(...))`. A style is either a URL (`StyleSource.Url`) or a recipe that`StyleComposer`builds on
  the device (`StyleSource.Composed`): `Imagery` (raster tiles + optional vector roads/labels restyled for photos), `Shaded`
  (any vector style + hillshade from Terrarium elevation tiles), `Raster`. Add a recipe type to compose new kinds of maps.
  Thumbnails live in `core/map/src/main/res/drawable-nodpi/map_preview_<id>.webp`.

## Add a location source

Implement `LocationSource` (a cold `Flow<LocationFix>`), for example an external Bluetooth GPS, and bind it.
`GameSession` resolves the user's chosen source by id.

## Data layout (Firebase)

```
lobbies/{CODE}/info        hostId, phase, round, locked, schema     (host writes)
               config      modeId, presetId, values{}               (host)
               roster      uid → role                               (host)
               members/{uid}  name, avatar, color, ready, online…   (each player writes own)
               locations/{uid}  latest fix                          (each player writes own)
               trails/{uid}/{push}  fix every ~5 s                  (each player, append-only)
               actions/{push}  GameAction                           (any member; host deletes)
               game        GameState                                (host / referee only)
               events/{push}  GameEvent                             (host)
               markers, chat                                        (members, append-only)
```

`FireJson` converts any kotlinx-serializable model to Firebase's map form, so adding a field to a model needs
no backend change. `LobbyInfo.schema` guards against joining a lobby made by a newer, incompatible version.
