package com.iloapps.nomaddashboard.core.data.companion

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class CompanionModule {
    @Binds abstract fun bindCompanionRepository(impl: DefaultCompanionRepository): CompanionRepository
}
