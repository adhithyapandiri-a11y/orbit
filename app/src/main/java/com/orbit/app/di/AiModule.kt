package com.orbit.app.di

import com.orbit.core.ai.OfflineDeterministicTaskExtractor
import com.orbit.core.ai.provider.AiProvider
import com.orbit.core.ai.provider.OfflineDeterministicAiProvider
import com.orbit.core.domain.model.TaskExtractor
import com.orbit.core.domain.usecase.ExtractTaskWithReviewUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AiModule {

    @Provides
    @Singleton
    fun provideTaskExtractor(): TaskExtractor = OfflineDeterministicTaskExtractor()

    @Provides
    @Singleton
    fun provideAiProvider(taskExtractor: TaskExtractor): AiProvider =
        OfflineDeterministicAiProvider(taskExtractor = taskExtractor)

    @Provides
    fun provideExtractTaskWithReviewUseCase(
        taskExtractor: TaskExtractor
    ): ExtractTaskWithReviewUseCase = ExtractTaskWithReviewUseCase(taskExtractor)
}
