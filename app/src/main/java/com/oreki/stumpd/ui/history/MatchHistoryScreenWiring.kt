package com.oreki.stumpd.ui.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.oreki.stumpd.data.local.db.StumpdDb
import com.oreki.stumpd.data.repository.GroupRepository
import com.oreki.stumpd.data.repository.MatchRepository
import com.oreki.stumpd.data.repository.PlayerRepository

@Composable
fun rememberMatchRepository(): MatchRepository {
    val ctx = LocalContext.current
    val db = remember { StumpdDb.get(ctx) }
    return remember { MatchRepository(db, ctx) }
}

@Composable
fun rememberPlayerRepository(): PlayerRepository {
    val ctx = LocalContext.current
    val db = remember { StumpdDb.get(ctx) }
    return remember { PlayerRepository(db) }
}

@Composable
fun rememberGroupRepository(): GroupRepository {
    val ctx = LocalContext.current
    val db = remember { StumpdDb.get(ctx) }
    return remember { GroupRepository(db) }
}
