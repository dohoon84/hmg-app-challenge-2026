package com.krazylabs.miri.ui.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krazylabs.miri.R
import com.krazylabs.miri.ui.theme.AstaSans
import com.krazylabs.miri.ui.theme.IviTheme

enum class VehicleKind { OWN, RENTAL, SHARE }

private data class KindOption(val kind: VehicleKind, val title: String, val body: String)

private val kindOptions = listOf(
    KindOption(VehicleKind.OWN, "내 차", "늘 타는 내 차예요"),
    KindOption(VehicleKind.RENTAL, "렌터카", "기간을 정해 빌린 차예요"),
    KindOption(VehicleKind.SHARE, "쉐어카", "몇 시간만 빌린 차예요"),
)

@Composable
fun F1KindScreen(
    highlighted: VehicleKind?,
    onOwn: () -> Unit,
    onRental: () -> Unit,
    onShare: () -> Unit,
    dark: Boolean,
) {
    val colors = IviTheme.colors
    ScreenCanvas {
        Row(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .width(Frame.pad + Frame.tileWidth)
                    .fillMaxHeight()
                    .padding(start = Frame.pad, top = 52.dp, end = 0.dp, bottom = 40.dp),
            ) {
                StatusLine("정차 중 · 시동 전")
                Spacer(Modifier.height(56.dp))
                HeroTitle("어떤 차로 타고 계세요?")
                Spacer(Modifier.height(72.dp))
                Column(verticalArrangement = Arrangement.spacedBy(Frame.tileGap)) {
                    kindOptions.forEach { option ->
                        KindTile(
                            option = option,
                            selected = option.kind == highlighted,
                            onClick = {
                                when (option.kind) {
                                    VehicleKind.OWN -> onOwn()
                                    VehicleKind.RENTAL -> onRental()
                                    VehicleKind.SHARE -> onShare()
                                }
                            },
                        )
                    }
                }
                Spacer(Modifier.height(36.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InfoMark()
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "고르지 않아도 지금처럼 안내해 드려요.",
                        color = colors.textTertiary,
                        fontFamily = AstaSans,
                        fontSize = 20.sp,
                        lineHeight = 28.sp,
                    )
                }
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(end = 24.dp, top = 80.dp, bottom = 40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(if (dark) R.drawable.miri_car_q34_dark else R.drawable.miri_car_q34_light),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

@Composable
private fun KindTile(option: KindOption, selected: Boolean, onClick: () -> Unit) {
    val colors = IviTheme.colors
    val shape = RoundedCornerShape(Frame.radius)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Frame.tileHeight)
            .then(if (selected) Modifier.border(3.dp, colors.accent, shape) else Modifier)
            .clip(shape)
            .background(if (selected) colors.accentContainer else androidx.compose.ui.graphics.Color.Transparent)
            .quietClick(onClick = onClick)
            .padding(horizontal = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                option.title,
                color = colors.textPrimary,
                fontFamily = AstaSans,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                lineHeight = 40.sp,
            )
            Text(
                option.body,
                color = colors.textTertiary,
                fontFamily = AstaSans,
                fontWeight = FontWeight.Normal,
                fontSize = 22.sp,
                lineHeight = 30.sp,
            )
        }
        if (selected) {
            CheckMark(colors.accent)
        }
    }
}
