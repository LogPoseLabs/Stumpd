package com.oreki.stumpd.ui.tournament

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.gson.Gson
import com.oreki.stumpd.FullScorecardActivity
import com.oreki.stumpd.TeamSetupActivity
import com.oreki.stumpd.domain.model.TeamSetupPreset
import com.oreki.stumpd.ui.nav.StumpdNavTransitions
import com.oreki.stumpd.ui.theme.StumpdTheme
import com.oreki.stumpd.viewmodel.TournamentViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * Tournaments, in their own activity with a small NavHost inside.
 *
 * The same shape as the statistics hub: one activity, several routes, so moving between the list,
 * a tournament and a squad doesn't cost a window transition each time. Everything here is reached
 * from a single card on the home screen, behind the `TOURNAMENTS` flag.
 */
@AndroidEntryPoint
class TournamentActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_TOURNAMENT_ID = "open_tournament_id"

        fun intent(context: Context): Intent = Intent(context, TournamentActivity::class.java)

        /**
         * Opens straight onto one tournament's detail screen, rather than the list.
         *
         * Used when a match completes or is exited: a fixture's natural "back" is the
         * tournament it settled, not the list every other entry point starts from — and this
         * has to be a real navigation rather than a bare `finish()`, because a fixture can also
         * be reached by *resuming* an in-progress match from History, which never puts this
         * activity on the back stack at all.
         */
        fun intent(context: Context, tournamentId: String): Intent =
            Intent(context, TournamentActivity::class.java)
                .putExtra(EXTRA_TOURNAMENT_ID, tournamentId)
                // Collapses any stale instance of this activity already on the stack (and
                // whatever was above it) rather than stacking a second one underneath.
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        val startTournamentId = intent.getStringExtra(EXTRA_TOURNAMENT_ID)
        setContent {
            StumpdTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    TournamentNavHost(onClose = { finish() }, startTournamentId = startTournamentId)
                }
            }
        }
    }
}

/**
 * The tournament view model for an id.
 *
 * Scoped to whichever nav entry asks for it, so the detail screen and a squad editor each get their
 * own — cheap, since everything they show is derived on load anyway.
 */
@Composable
internal fun tournamentViewModel(tournamentId: String): TournamentViewModel = hiltViewModel(
    creationCallback = { factory: TournamentViewModel.Factory -> factory.create(tournamentId) },
)

/** The routes. `detail` and `squad` carry their ids in the path, as the stats host does. */
sealed class TournamentRoute(val route: String) {
    data object List : TournamentRoute("list")
    data object Create : TournamentRoute("create")

    data object Detail : TournamentRoute("detail/{tournamentId}") {
        fun of(tournamentId: String) = "detail/$tournamentId"
    }

    data object Squad : TournamentRoute("squad/{tournamentId}/{teamId}") {
        fun of(tournamentId: String, teamId: String) = "squad/$tournamentId/$teamId"
    }
}

/** Back, or out of the flow entirely when there's nothing left to go back to. */
fun NavController.upOrClose(onClose: () -> Unit) {
    if (!popBackStack()) onClose()
}

@Composable
fun TournamentNavHost(onClose: () -> Unit, startTournamentId: String? = null) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = startTournamentId?.let { TournamentRoute.Detail.of(it) }
            ?: TournamentRoute.List.route,
        enterTransition = StumpdNavTransitions.enter,
        exitTransition = StumpdNavTransitions.exit,
        popEnterTransition = StumpdNavTransitions.popEnter,
        popExitTransition = StumpdNavTransitions.popExit,
    ) {
        composable(TournamentRoute.List.route) {
            TournamentListScreen(
                onBack = onClose,
                onCreate = { navController.navigate(TournamentRoute.Create.route) },
                onOpen = { id -> navController.navigate(TournamentRoute.Detail.of(id)) },
            )
        }
        composable(TournamentRoute.Create.route) {
            CreateTournamentScreen(
                onBack = { navController.upOrClose(onClose) },
                onCreated = { id ->
                    // Replace the create screen, so backing out of a new tournament lands on the
                    // list rather than on the form that made it.
                    navController.popBackStack()
                    navController.navigate(TournamentRoute.Detail.of(id))
                },
            )
        }
        composable(TournamentRoute.Detail.route) { entry ->
            val id = entry.arguments?.getString("tournamentId").orEmpty()
            val context = LocalContext.current
            TournamentDetailScreen(
                tournamentId = id,
                onBack = { navController.upOrClose(onClose) },
                onEditSquad = { teamId ->
                    navController.navigate(TournamentRoute.Squad.of(id, teamId))
                },
                onPlayFixture = { preset ->
                    // Team setup, opened on the fixture: the same screen as a quick match, with
                    // the teams settled. This activity stays on the stack, so finishing or
                    // backing out of the match returns to the tournament.
                    context.startActivity(
                        Intent(context, TeamSetupActivity::class.java)
                            .putExtra(TeamSetupPreset.INTENT_EXTRA, Gson().toJson(preset))
                    )
                },
                onOpenMatch = { matchId ->
                    context.startActivity(
                        Intent(context, FullScorecardActivity::class.java)
                            .putExtra("match_id", matchId)
                    )
                },
            )
        }
        composable(TournamentRoute.Squad.route) { entry ->
            TeamSquadScreen(
                tournamentId = entry.arguments?.getString("tournamentId").orEmpty(),
                teamId = entry.arguments?.getString("teamId").orEmpty(),
                onBack = { navController.upOrClose(onClose) },
            )
        }
    }
}
