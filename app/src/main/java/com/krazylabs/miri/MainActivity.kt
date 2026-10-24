package com.krazylabs.miri

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.krazylabs.miri.ui.FoundationRoute
import com.krazylabs.miri.ui.FoundationViewModel
import com.krazylabs.miri.ui.MiriRoute
import com.krazylabs.miri.ui.MiriViewModel
import com.krazylabs.miri.ui.design.quietClick
import com.krazylabs.miri.ui.theme.FoundationTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MiriViewModel by viewModels()
    private val foundationViewModel: FoundationViewModel by viewModels()

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
            var showFoundation by rememberSaveable { mutableStateOf(false) }
            FoundationTheme {
                if (showFoundation && BuildConfig.DEBUG) {
                    Box(Modifier.fillMaxSize()) {
                        FoundationRoute(
                            viewModel = foundationViewModel,
                            onRequestEnergyPermission = {},
                        )
                        Text(
                            "미리로",
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(24.dp)
                                .quietClick { showFoundation = false }
                                .padding(12.dp),
                        )
                    }
                } else {
                    MiriRoute(
                        viewModel = viewModel,
                        onOpenFoundation = { showFoundation = true },
                    )
                }
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
