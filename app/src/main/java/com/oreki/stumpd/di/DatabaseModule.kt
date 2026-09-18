package com.oreki.stumpd.di

import android.content.Context
import com.oreki.stumpd.data.local.db.StumpdDb
import com.oreki.stumpd.data.repository.GroupRepository
import com.oreki.stumpd.data.repository.MatchCorrectionRepository
import com.oreki.stumpd.data.repository.MatchRepository
import com.oreki.stumpd.data.repository.PlayerRepository
import com.oreki.stumpd.data.repository.TournamentRepository
import com.oreki.stumpd.data.sync.CompleteSyncManager
import com.oreki.stumpd.data.sync.SyncScheduler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): StumpdDb =
        StumpdDb.get(context)

    @Provides
    @Singleton
    fun provideMatchRepository(db: StumpdDb, @ApplicationContext context: Context): MatchRepository =
        MatchRepository(db, context, Dispatchers.IO)

    @Provides
    @Singleton
    fun providePlayerRepository(db: StumpdDb, matchRepository: MatchRepository): PlayerRepository =
        PlayerRepository(db, matchRepository)

    @Provides
    @Singleton
    fun provideTournamentRepository(
        db: StumpdDb,
        matchRepository: MatchRepository,
    ): TournamentRepository = TournamentRepository(db, matchRepository, Dispatchers.IO)

    @Provides
    @Singleton
    fun provideGroupRepository(db: StumpdDb): GroupRepository =
        GroupRepository(db)

    @Provides
    @Singleton
    fun provideMatchCorrectionRepository(
        db: StumpdDb,
        matchRepository: MatchRepository,
        groupRepository: GroupRepository,
    ): MatchCorrectionRepository = MatchCorrectionRepository(
        db = db,
        matchRepository = matchRepository,
        groupRepository = groupRepository,
        ioDispatcher = Dispatchers.IO,
    )

    @Provides
    @Singleton
    fun provideCompleteSyncManager(
        @ApplicationContext context: Context,
        db: StumpdDb,
        matchRepository: MatchRepository,
        playerRepository: PlayerRepository,
        groupRepository: GroupRepository,
        syncScheduler: SyncScheduler,
    ): CompleteSyncManager = CompleteSyncManager(
        context = context,
        db = db,
        matchRepository = matchRepository,
        playerRepository = playerRepository,
        groupRepository = groupRepository,
        syncScheduler = syncScheduler,
    )
}
