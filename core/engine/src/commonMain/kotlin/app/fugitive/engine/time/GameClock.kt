package app.fugitive.engine.time

import app.fugitive.model.Fmt
import app.fugitive.model.GameState

/** Coarse stage of a game derived purely from its timestamps. */
enum class GameStage {
    /** Everyone sees a countdown; no one may move yet. */
    COUNTDOWN,
    /** Runners scatter; hunters wait. */
    HEADSTART,
    /** The hunt is on. */
    ACTIVE,
    /** Result decided. */
    ENDED;

    val huntersReleased: Boolean get() = this == ACTIVE
}

object GameClock {
    fun stage(game: GameState, now: Long): GameStage = when {
        game.result != null -> GameStage.ENDED
        now < game.startAt -> GameStage.COUNTDOWN
        now < game.headstartEndsAt -> GameStage.HEADSTART
        else -> GameStage.ACTIVE
    }

    /** Millis until the next stage boundary (or game end), or null if none. */
    fun untilNextBoundary(game: GameState, now: Long): Long? = when (stage(game, now)) {
        GameStage.COUNTDOWN -> game.startAt - now
        GameStage.HEADSTART -> game.headstartEndsAt - now
        GameStage.ACTIVE -> game.endsAt?.let { it - now }
        GameStage.ENDED -> null
    }?.coerceAtLeast(0)

    fun elapsedSinceStart(game: GameState, now: Long): Long = (now - game.startAt).coerceAtLeast(0)

    /** "12:05" / "1:02:09" style clock text. */
    fun format(millis: Long): String {
        val total = (millis.coerceAtLeast(0) + 999) / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "$h:${Fmt.pad2(m)}:${Fmt.pad2(s)}" else "$m:${Fmt.pad2(s)}"
    }
}
