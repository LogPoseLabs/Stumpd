package com.oreki.stumpd.data.sync

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SyncWorkEntryPoint {
    fun completeSyncManager(): CompleteSyncManager
}
