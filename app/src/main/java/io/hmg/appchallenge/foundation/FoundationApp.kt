package io.hmg.appchallenge.foundation

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.hilt.android.HiltAndroidApp
import io.hmg.appchallenge.foundation.perf.LaunchTiming
import io.hmg.appchallenge.foundation.session.CabinSession
import javax.inject.Inject

@HiltAndroidApp
class FoundationApp : Application() {
    @Inject lateinit var cabinSession: CabinSession

    override fun onCreate() {
        LaunchTiming.markProcessStart()
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(cabinSession)
    }
}
