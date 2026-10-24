package com.krazylabs.miri.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.widthIn
import com.krazylabs.miri.BuildConfig
import com.krazylabs.miri.domain.GearBackend
import com.krazylabs.miri.mvp.ScenarioOverrides
import com.krazylabs.miri.speech.SpeechUsage

@Composable
fun ScenarioPanel(
    overrides: ScenarioOverrides,
    onChange: (ScenarioOverrides) -> Unit,
    onRoute: () -> Unit,
    onRoad: () -> Unit,
    onGearBackend: (String) -> Unit = {},
    onOpenFoundation: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var meters by rememberSaveable { mutableStateOf(overrides.remainMeters?.toString().orEmpty()) }
    var seconds by rememberSaveable { mutableStateOf(overrides.remainSeconds?.toString().orEmpty()) }
    var returnMin by rememberSaveable { mutableStateOf(overrides.returnInMinutes?.toString().orEmpty()) }
    var target by rememberSaveable { mutableStateOf(overrides.chargeTargetPercent?.toString().orEmpty()) }
    LaunchedEffect(
        overrides.remainMeters,
        overrides.remainSeconds,
        overrides.returnInMinutes,
        overrides.chargeTargetPercent,
    ) {
        meters = overrides.remainMeters?.toString().orEmpty()
        seconds = overrides.remainSeconds?.toString().orEmpty()
        returnMin = overrides.returnInMinutes?.toString().orEmpty()
        target = overrides.chargeTargetPercent?.toString().orEmpty()
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.widthIn(max = 520.dp).fillMaxWidth(),
    ) {
        Text("데모 값", style = MaterialTheme.typography.titleMedium)
        Text(
            "기어·문·방향지시등은 Virtual Console에서 바꿉니다. 이 앱은 VC를 호출하지 않습니다.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = meters,
                onValueChange = { meters = it },
                label = { Text("남은 m") },
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = seconds,
                onValueChange = { seconds = it },
                label = { Text("남은 초") },
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = returnMin,
                onValueChange = { returnMin = it },
                label = { Text("반납까지 분") },
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = target,
                onValueChange = { target = it },
                label = { Text("충전 기준 %") },
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            TextButton(onClick = {
                onChange(
                    overrides.copy(
                        remainMeters = meters.toIntOrNull(),
                        remainSeconds = seconds.toIntOrNull(),
                        returnInMinutes = returnMin.toIntOrNull(),
                        chargeTargetPercent = target.toIntOrNull(),
                    ),
                )
            }) { Text("값 적용") }
            TextButton(onClick = { onChange(overrides.copy(pretendCharging = !overrides.pretendCharging)) }) {
                Text(if (overrides.pretendCharging) "충전 목적지 끔" else "충전 목적지")
            }
            TextButton(onClick = { onChange(overrides.copy(pretendTolls = 2, pretendHighways = 1)) }) {
                Text("톨 2 · 고속 1")
            }
        }
        val guidance = when (overrides.speechUsage) {
            SpeechUsage.NAVIGATION_GUIDANCE -> true
            SpeechUsage.SONIFICATION -> false
            else -> BuildConfig.SPEECH_USAGE == SpeechUsage.NAVIGATION_GUIDANCE
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            TextButton(onClick = {
                onChange(
                    overrides.copy(
                        speechUsage = if (guidance) {
                            SpeechUsage.SONIFICATION
                        } else {
                            SpeechUsage.NAVIGATION_GUIDANCE
                        },
                    ),
                )
            }) { Text(if (guidance) "음성: 내비 안내" else "음성: 효과음") }
            Button(onClick = { onChange(overrides.copy(armNewRental = true)) }) { Text("새 대여") }
            TextButton(onClick = {
                onChange(overrides.copy(lightsUseDoorStandIn = !overrides.lightsUseDoorStandIn))
            }) { Text(if (overrides.lightsUseDoorStandIn) "문 대체 끔" else "실내등→문") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRoad) { Text("오늘 길") }
            Button(onClick = onRoute) { Text("충전 경로") }
        }
        val gear = overrides.gearBackend.ifBlank { BuildConfig.GEAR_BACKEND }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            TextButton(onClick = {
                onGearBackend(if (gear == GearBackend.PLEOS) GearBackend.AAOS else GearBackend.PLEOS)
            }) { Text(if (gear == GearBackend.PLEOS) "기어: PLEOS" else "기어: AAOS") }
            TextButton(onClick = onOpenFoundation) { Text("이 좌표 ETA") }
        }
    }
}
