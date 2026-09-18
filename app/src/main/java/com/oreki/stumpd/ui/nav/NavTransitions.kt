package com.oreki.stumpd.ui.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import com.oreki.stumpd.ui.theme.StumpdMotion

/**
 * How a Compose NavHost moves, matching the window animations in `themes.xml`.
 *
 * Without these a route change appears instantly while every other navigation in the app slides,
 * so "list → detail" feels unlike "home → history" even though one is a composable swap and the
 * other a window. Shared rather than copied, so the stats and tournament hosts cannot drift apart.
 */
object StumpdNavTransitions {

    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInHorizontally(
            initialOffsetX = { (it * 0.07f).toInt() },
            animationSpec = tween(StumpdMotion.STANDARD_MS, easing = StumpdMotion.enter),
        ) + fadeIn(tween(StumpdMotion.QUICK_MS + 60))
    }

    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutHorizontally(
            targetOffsetX = { -(it * 0.03f).toInt() },
            animationSpec = tween(StumpdMotion.STANDARD_MS, easing = StumpdMotion.standard),
        ) + fadeOut(tween(StumpdMotion.STANDARD_MS), targetAlpha = 0.7f)
    }

    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInHorizontally(
            initialOffsetX = { -(it * 0.03f).toInt() },
            animationSpec = tween(StumpdMotion.STANDARD_MS, easing = StumpdMotion.enter),
        ) + fadeIn(tween(StumpdMotion.STANDARD_MS), initialAlpha = 0.7f)
    }

    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutHorizontally(
            targetOffsetX = { (it * 0.07f).toInt() },
            animationSpec = tween(StumpdMotion.STANDARD_MS, easing = StumpdMotion.standard),
        ) + fadeOut(tween(StumpdMotion.QUICK_MS + 80))
    }
}
