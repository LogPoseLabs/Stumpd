package com.oreki.stumpd

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.oreki.stumpd.domain.model.MatchHistory

/**
 * The match summary is now the "Summary" tab of the full scorecard — it was a strict subset of
 * that screen, so keeping both meant maintaining two views of the same match.
 *
 * This activity stays only as a redirect, so a task restored from an older build (or any link
 * still pointing here) lands on the tab rather than on a blank screen.
 */
class MatchDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(this, FullScorecardActivity::class.java).apply {
                putExtra("match_id", intent.getStringExtra("match_id") ?: "")
                putExtra("initial_tab", "Summary")
            }
        )
        finish()
    }
}

/**
 * Legal balls bowled in [innings], counted from the delivery log. Wides and no-balls are not
 * legal deliveries, so they are excluded.
 */
internal fun legalBallsInInnings(match: MatchHistory, innings: Int): Int =
    match.allDeliveries.count { delivery ->
        delivery.inning == innings &&
            !delivery.outcome.startsWith("Wd", ignoreCase = true) &&
            !delivery.outcome.startsWith("Nb", ignoreCase = true)
    }

/**
 * Run rate over the overs actually faced.
 *
 * This used to divide by the match's *configured* overs, so a side bowled out in 3 of 5 overs
 * had its run rate divided by 5 and looked far slower than it was. Falls back to the configured
 * overs only when there is no delivery log, which is the case for older saved matches.
 */
internal fun inningsRunRate(match: MatchHistory, innings: Int, runs: Int): Double {
    if (runs <= 0) return 0.0
    val legalBalls = legalBallsInInnings(match, innings)
    if (legalBalls > 0) return runs * 6.0 / legalBalls
    val configuredOvers = match.matchSettings?.totalOvers?.toDouble() ?: 0.0
    return if (configuredOvers > 0) runs / configuredOvers else 0.0
}
