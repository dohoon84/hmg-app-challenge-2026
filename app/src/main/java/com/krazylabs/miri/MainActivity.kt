package com.krazylabs.miri

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.krazylabs.miri.ui.MiriRoute
import com.krazylabs.miri.ui.MiriViewModel
import com.krazylabs.miri.ui.theme.FoundationTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MiriViewModel by viewModels()

    /**
     * System Back at the root moves the task to the background. There is no on-screen
     * back button. A future screen above this one must set [rootBack].isEnabled to false
     * while it is showing, then true again when it leaves. This activity is the only screen.
     */
    private val rootBack = object : OnBackPressedCallback(enabled = true) {
        override fun handleOnBackPressed() {
            moveTaskToBack(true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, rootBack)
        setContent {
            FoundationTheme {
                MiriRoute(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onHostResume()
    }

    override fun onPause() {
        viewModel.onHostPause()
        super.onPause()
    }

    override fun onStop() {
        viewModel.onHostStop()
        super.onStop()
    }
}
