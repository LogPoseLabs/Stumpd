package com.oreki.stumpd.di

import com.oreki.stumpd.utils.CompositeFeatureFlagProvider
import com.oreki.stumpd.utils.FeatureFlagProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FeatureFlagModule {

    @Binds
    @Singleton
    abstract fun bindFeatureFlagProvider(impl: CompositeFeatureFlagProvider): FeatureFlagProvider
}
