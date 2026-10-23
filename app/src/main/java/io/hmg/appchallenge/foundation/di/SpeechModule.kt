package io.hmg.appchallenge.foundation.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.hmg.appchallenge.foundation.speech.AndroidSpeechFocus
import io.hmg.appchallenge.foundation.speech.AndroidTtsEngine
import io.hmg.appchallenge.foundation.speech.CabinAnnouncer
import io.hmg.appchallenge.foundation.speech.ChimePlayer
import io.hmg.appchallenge.foundation.speech.FocusedAnnouncer
import io.hmg.appchallenge.foundation.speech.MainThreadScheduler
import io.hmg.appchallenge.foundation.speech.RawClipPlayer
import io.hmg.appchallenge.foundation.speech.SpeechEngine
import io.hmg.appchallenge.foundation.speech.SpeechFocus
import io.hmg.appchallenge.foundation.speech.SpeechScheduler
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
