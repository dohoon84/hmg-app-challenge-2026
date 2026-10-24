package com.krazylabs.miri.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.krazylabs.miri.speech.AndroidSpeechFocus
import com.krazylabs.miri.speech.AndroidTtsEngine
import com.krazylabs.miri.speech.CabinAnnouncer
import com.krazylabs.miri.speech.ChimePlayer
import com.krazylabs.miri.speech.FocusedAnnouncer
import com.krazylabs.miri.speech.MainThreadScheduler
import com.krazylabs.miri.speech.RawClipPlayer
import com.krazylabs.miri.speech.SpeechEngine
import com.krazylabs.miri.speech.SpeechFocus
import com.krazylabs.miri.speech.SpeechScheduler
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SpeechModule {
    @Provides
    @Singleton
    fun speechEngine(@ApplicationContext context: Context): SpeechEngine = AndroidTtsEngine(context)

    @Provides
    @Singleton
    fun chimePlayer(@ApplicationContext context: Context): ChimePlayer = RawClipPlayer(context)

    @Provides
    @Singleton
    fun speechFocus(@ApplicationContext context: Context): SpeechFocus = AndroidSpeechFocus(context)

    @Provides
    @Singleton
    fun speechScheduler(): SpeechScheduler = MainThreadScheduler()

    @Provides
    @Singleton
    fun cabinAnnouncer(
        engine: SpeechEngine,
        chime: ChimePlayer,
        focus: SpeechFocus,
        scheduler: SpeechScheduler,
    ): CabinAnnouncer = FocusedAnnouncer(engine, chime, focus, scheduler)
}
