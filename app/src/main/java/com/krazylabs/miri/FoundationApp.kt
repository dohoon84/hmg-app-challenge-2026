package com.krazylabs.miri

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.hilt.android.HiltAndroidApp
import com.krazylabs.miri.session.CabinSession
import javax.inject.Inject

@HiltAndroidApp
class FoundationApp : Application() {
    @Inject lateinit var cabinSession: CabinSession

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(cabinSession)
    }
}
