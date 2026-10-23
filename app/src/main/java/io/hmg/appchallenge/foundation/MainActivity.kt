package io.hmg.appchallenge.foundation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import io.hmg.appchallenge.foundation.ui.FoundationRoute
import io.hmg.appchallenge.foundation.ui.FoundationViewModel
import io.hmg.appchallenge.foundation.ui.theme.FoundationTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: FoundationViewModel by viewModels()

    private val energyPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onEnergyPermissionResult(granted)
    }

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
                FoundationRoute(
                    viewModel = viewModel,
                    onRequestEnergyPermission = {
                        if (BuildConfig.USES_AAOS_CAR) {
                            energyPermission.launch(AAOS_ENERGY_PERMISSION)
                        }
                    },
                )
            }
        }
    }

    private companion object {
        // Literal, so the fake flavor never loads android.car.Car.
        const val AAOS_ENERGY_PERMISSION = "android.car.permission.CAR_ENERGY"
    }
}
