package com.oreki.stumpd.ui.stats

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import android.os.Build
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.oreki.stumpd.CaptainStatsScreen
import com.oreki.stumpd.ui.nav.StumpdNavTransitions
import com.oreki.stumpd.HeadToHeadScreen
import com.oreki.stumpd.RankingsScreen
import com.oreki.stumpd.RecordsScreen
import com.oreki.stumpd.StatsHubScreen
import com.oreki.stumpd.ui.theme.StumpdMotion

sealed class StatsRoute(val route: String) {
    data object Hub : StatsRoute("hub")
    data object Rankings : StatsRoute("rankings")
    data object HeadToHead : StatsRoute("head_to_head")
    data object CaptainStats : StatsRoute("captain_stats")
    data object Records : StatsRoute("records")
}

fun NavController.popBackStackOrFinish(context: Context) {
    if (!popBackStack()) {
        (context as? ComponentActivity)?.finish()
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun StatsNavHost(
    onCloseStatsFlow: () -> Unit,
    widthSizeClass: WindowWidthSizeClass,
) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = StatsRoute.Hub.route,
        // The five stats screens are the only place in the app that navigates without changing
        // activity, so without this they appeared instantly while every other navigation moved.
        // Same direction, distance and duration as the window animations in themes.xml, so
        // "hub → rankings" feels like "home → history" even though one is a window and the other
        // is a composable swap.
        enterTransition = StumpdNavTransitions.enter,
        exitTransition = StumpdNavTransitions.exit,
        popEnterTransition = StumpdNavTransitions.popEnter,
        popExitTransition = StumpdNavTransitions.popExit,
    ) {
        composable(StatsRoute.Hub.route) {
            StatsHubScreen(
                navController = navController,
                onBack = onCloseStatsFlow,
                widthSizeClass = widthSizeClass,
            )
        }
        composable(StatsRoute.Rankings.route) {
            RankingsScreen(navController = navController)
        }
        composable(StatsRoute.HeadToHead.route) {
            HeadToHeadScreen(navController = navController)
        }
        composable(StatsRoute.CaptainStats.route) {
            CaptainStatsScreen(navController = navController)
        }
        composable(StatsRoute.Records.route) {
            RecordsScreen(navController = navController)
        }
    }
}
