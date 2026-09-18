package com.oreki.stumpd.ui.history

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.oreki.stumpd.data.repository.GroupRepository
import com.oreki.stumpd.data.repository.MatchRepository
import com.oreki.stumpd.data.repository.PlayerRepository
import com.oreki.stumpd.data.repository.TournamentRepository
import com.oreki.stumpd.utils.FeatureFlagProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface RepositoryEntryPoint {
    fun matchRepository(): MatchRepository
    fun playerRepository(): PlayerRepository
    fun tournamentRepository(): TournamentRepository
    fun groupRepository(): GroupRepository
    fun featureFlags(): FeatureFlagProvider
}

private fun getEntryPoint(context: Context): RepositoryEntryPoint =
    EntryPointAccessors.fromApplication(context, RepositoryEntryPoint::class.java)

@Composable
fun rememberMatchRepository(): MatchRepository {
    val ctx = LocalContext.current.applicationContext
    return remember { getEntryPoint(ctx).matchRepository() }
}

@Composable
fun rememberPlayerRepository(): PlayerRepository {
    val ctx = LocalContext.current.applicationContext
    return remember { getEntryPoint(ctx).playerRepository() }
}

@Composable
fun rememberGroupRepository(): GroupRepository {
    val ctx = LocalContext.current.applicationContext
    return remember { getEntryPoint(ctx).groupRepository() }
}

@Composable
fun rememberTournamentRepository(): TournamentRepository {
    val ctx = LocalContext.current.applicationContext
    return remember { getEntryPoint(ctx).tournamentRepository() }
}

/**
 * The composite flag provider — local override first, then Remote Config.
 *
 * Remembered rather than constructed, because the remote provider fetches on construction.
 */
@Composable
fun rememberFeatureFlags(): FeatureFlagProvider {
    val ctx = LocalContext.current.applicationContext
    return remember { getEntryPoint(ctx).featureFlags() }
}
