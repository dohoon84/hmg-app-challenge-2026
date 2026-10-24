package io.hmg.appchallenge.foundation.probe

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.hmg.appchallenge.foundation.ui.theme.FoundationTheme

/**
 * pleosProbe launcher for the signal dump. Not in the release source set.
 * System Back moves the task back so the process can keep NaviHelper listeners.
 */
class SignalProbeActivity : ComponentActivity() {
    private val probe by lazy { SignalProbe(applicationContext) }

    private val permissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        probe.onPermissionResult(result)
    }

    private val rootBack = object : OnBackPressedCallback(enabled = true) {
        override fun handleOnBackPressed() {
            moveTaskToBack(true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, rootBack)
        probe.attachHost(this)
        probe.setFrontFromIntent(intent)
        probe.start()
        probe.setCadenceFromIntent(intent)
        if (savedInstanceState == null) probe.requestRouteFromIntent(intent)
        setContent {
            FoundationTheme {
                ProbeScreen(
                    probe = probe,
                    onHold = probe::setHold,
                    onJeju = probe::requestJeju,
                    onGas = probe::requestGas,
                    onSimulation = probe::trySimulation,
                    onPoll = probe::pollNavi,
                    onVehicle = probe::readVehicle,
                    onVoice = {
                        startActivity(Intent(this, VoiceProbeActivity::class.java))
                    },
                )
            }
        }
        permissions.launch(SignalProbe.PERMISSIONS)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        probe.setFrontFromIntent(intent)
        probe.setCadenceFromIntent(intent)
        probe.requestRouteFromIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        probe.onActivityStart()
    }

    override fun onResume() {
        super.onResume()
        probe.onHostResume()
    }

    override fun onPause() {
        probe.onHostPause()
        super.onPause()
    }

    override fun onStop() {
        probe.onActivityStop()
        super.onStop()
    }

    override fun onDestroy() {
        probe.release()
        super.onDestroy()
    }
}

@Composable
private fun ProbeScreen(
    probe: SignalProbe,
    onHold: (Boolean) -> Unit,
    onJeju: () -> Unit,
    onGas: () -> Unit,
    onSimulation: () -> Unit,
    onPoll: () -> Unit,
    onVehicle: () -> Unit,
    onVoice: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("신호 프로브", style = MaterialTheme.typography.headlineMedium)
        Text("검증용입니다. 릴리스에 포함되지 않습니다. 제어 API는 호출하지 않습니다.")
        SelectionContainer {
            Text(
                text = probe.jsonDirectory,
                fontFamily = FontFamily.Monospace,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Switch(checked = probe.holdInBackground, onCheckedChange = onHold)
            Text(
                if (probe.holdInBackground) {
                    "백그라운드에서도 Navi 리스너 유지. 콜백은 bg=true"
                } else {
                    "백그라운드에서는 Navi 리스너를 해제"
                },
            )
        }
        ProbeSwitch(
            checked = probe.frontTest,
            onChecked = probe::updateFrontTest,
            label = if (probe.frontTest) {
                "R→P 후 1.5초 뒤 moveTaskToFront"
            } else {
                "R→P 화면 복귀 꺼짐"
            },
        )
        ProbeSwitch(
            checked = probe.reorderToFront,
            onChecked = probe::updateReorderToFront,
            label = if (probe.reorderToFront) {
                "이어서 REORDER_TO_FRONT"
            } else {
                "REORDER_TO_FRONT 꺼짐"
            },
        )
        ProbeSwitch(
            checked = probe.cadencePoll,
            onChecked = probe::updateCadencePoll,
            label = if (probe.cadencePoll) {
                "1Hz로 남은 거리 다시 요청"
            } else {
                "1Hz 남은 거리 폴링 꺼짐"
            },
        )
        ProbeButton("경로: 제주공항 렌터카 (33.5059, 126.4929)", onJeju)
        ProbeButton("경로: 주유소 이름", onGas)
        ProbeButton("SIMULATION_GUIDING", onSimulation)
        ProbeButton("get*Info 폴링", onPoll)
        ProbeButton("차량 정보 다시 읽기", onVehicle)
        ProbeButton("음성 프로브 (ko-KR)", onVoice)
        Text("최근 로그", style = MaterialTheme.typography.titleMedium)
        SelectionContainer {
            Text(
                text = probe.lines.joinToString("\n"),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ProbeSwitch(checked: Boolean, onChecked: (Boolean) -> Unit, label: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Switch(checked = checked, onCheckedChange = onChecked)
        Text(label)
    }
}

@Composable
private fun ProbeButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp),
    ) {
        Text(label)
    }
}
