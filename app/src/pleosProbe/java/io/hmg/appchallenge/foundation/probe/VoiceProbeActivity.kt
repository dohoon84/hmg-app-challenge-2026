package io.hmg.appchallenge.foundation.probe

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.hmg.appchallenge.foundation.ui.theme.FoundationTheme

/**
 * pleosProbe screen that writes Korean TTS samples. It does not play them and it does not
 * call the product announcer. Not in the release source set.
 */
class VoiceProbeActivity : ComponentActivity() {
    private val probe by lazy { VoiceProbe.obtain(applicationContext) }

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
                VoiceProbeScreen(probe = probe, onRun = probe::run)
            }
        }
        if (savedInstanceState == null && intent.getBooleanExtra(EXTRA_AUTORUN, false)) {
            probe.run()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_AUTORUN, false)) {
            probe.run()
        }
    }

    companion object {
        const val EXTRA_AUTORUN = "autorun"
    }
}

@Composable
private fun VoiceProbeScreen(probe: VoiceProbe, onRun: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("음성 프로브", style = MaterialTheme.typography.headlineMedium)
        Text("ko-KR 음성을 wav로만 저장합니다. 재생하지 않고, 차량 제어 API는 호출하지 않습니다.")
        Text(probe.status, style = MaterialTheme.typography.titleMedium)
        SelectionContainer {
            Text(
                text = probe.directoryText.ifBlank { "아직 시작하지 않았습니다." },
                fontFamily = FontFamily.Monospace,
            )
        }
        Button(
            onClick = onRun,
            enabled = !probe.running,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp),
        ) {
            Text("ko-KR 음성 파일 만들기")
        }
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
