package app.fugitive.engine.rules

/** Built-in action types. Modes and plugins may define more (handled via GameMode.handleAction). */
object ActionTypes {
    const val CATCH_CLAIM = "catch_claim"
    const val CATCH_RESPONSE = "catch_response"
    const val CODE_CATCH = "code_catch"
    const val SELF_REPORT = "self_report"
    const val USE_ABILITY = "use_ability"
    const val LEAVE = "leave"
    const val HOST_END = "host_end"
}

/** Built-in event types (drive icons and alert behaviour in the UI). */
object EventTypes {
    const val GAME_STARTED = "game_started"
    const val RELEASED = "released"
    const val CATCH_CLAIMED = "catch_claimed"
    const val CATCH_DISPUTED = "catch_disputed"
    const val CAUGHT = "caught"
    const val INFECTED = "infected"
    const val ELIMINATED = "eliminated"
    const val ESCAPED = "escaped"
    const val LEFT = "left"
    const val JOINED = "joined"
    const val ZONE_WARNING = "zone_warning"
    const val ZONE_SHRINKING = "zone_shrinking"
    const val ZONE_FINAL = "zone_final"
    const val OUTSIDE_ZONE = "outside_zone"
    const val BACK_IN_ZONE = "back_in_zone"
    const val REVEALED = "revealed"
    const val DISCONNECTED = "disconnected"
    const val RECONNECTED = "reconnected"
    const val ABILITY = "ability"
    const val TIME_WARNING = "time_warning"
    const val OBJECTIVE = "objective"
    const val TARGET = "target"
    const val WRONG_CODE = "wrong_code"
    const val GAME_OVER = "game_over"
    const val INFO = "info"
}

/** Machine-readable reasons a game ended. */
object EndReasons {
    const val ALL_CAUGHT = "all_caught"
    const val TIME_UP = "time_up"
    const val LAST_STANDING = "last_standing"
    const val ESCAPED = "escaped"
    const val NO_HUNTERS = "no_hunters"
    const val HOST_ENDED = "host_ended"
    const val NO_PLAYERS = "no_players"
}

/** Machine-readable reasons a player left play. */
object OutReasons {
    const val CAUGHT = "caught"
    const val ZONE = "zone"
    const val DISCONNECT = "disconnect"
    const val LEFT = "left"
    const val ESCAPED = "escaped"
    const val INFECTED = "infected"
}

/** Keys used in PlayerState.data by built-in rules. */
object DataKeys {
    const val REVEAL_ZONE = "reveal_zone"
    const val REVEAL_DISCONNECT = "reveal_dc"
    const val DISCONNECTED = "disconnected"
    const val DWELL_HUNTER = "dwell_hunter"
    const val DWELL_SINCE = "dwell_since"
    const val CODE_FAILS = "code_fails"
    const val CODE_LOCKED_UNTIL = "code_locked_until"
    const val EXTRACTION_SINCE = "extract_since"
    const val EXTRACTION_ID = "extract_id"
}
