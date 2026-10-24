package com.krazylabs.miri.ui.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import com.krazylabs.miri.ui.ShownCard
import com.krazylabs.miri.ui.theme.AstaSans
import com.krazylabs.miri.ui.theme.IviTheme

data class RoadStat(val value: String, val label: String)

fun roadStats(lines: List<String>): List<RoadStat> = lines.map { line ->
    val toll = Regex("""톨게이트\s+(\d+)""").find(line)
    if (toll != null) return@map RoadStat(toll.groupValues[1], "톨게이트")
    val highway = Regex("""고속도로 진입\s+(\d+)""").find(line)
    if (highway != null) return@map RoadStat(highway.groupValues[1], "고속도로 진입")
    RoadStat(line, "")
}

fun portValue(lines: List<String>): String {
    val line = lines.firstOrNull { it.startsWith("충전구는") || it.startsWith("주유구는") } ?: return ""
    return line
        .removePrefix("충전구는 ")
        .removePrefix("주유구는 ")
        .removeSuffix("에 있어요.")
        .removeSuffix(".")
        .trim()
}

fun connectorValue(lines: List<String>, chips: List<String>): String? {
    val fromLine = lines.firstOrNull { it.startsWith("커넥터") }
        ?.substringAfter(":")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    if (fromLine != null) return fromLine
    return chips.firstOrNull { it.contains("콤보") || it.contains("커넥터") }
}

@Composable
fun D1RoadScreen(card: ShownCard?, dark: Boolean) {
    val colors = IviTheme.colors
    val stats = roadStats(card?.lines.orEmpty()).ifEmpty {
        listOf(RoadStat("1", "톨게이트"), RoadStat("1", "고속도로 진입"))
    }
    ScreenCanvas {
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = Frame.pad, top = 52.dp, end = Frame.pad, bottom = 36.dp),
        ) {
            StatusLine("정차 중 · 출발 전")
            Spacer(Modifier.height(28.dp))
            HeroTitle(card?.title ?: "오늘 길")
            Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1.05f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(if (dark) R.drawable.miri_car_top_dark else R.drawable.miri_car_top_light),
                        contentDescription = null,
                        modifier = Modifier.fillMaxHeight(0.92f),
                        contentScale = ContentScale.Fit,
                    )
                }
                Column(Modifier.weight(0.95f), verticalArrangement = Arrangement.spacedBy(36.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        stats.take(2).forEach { stat ->
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stat.value,
                                    color = colors.textPrimary,
                                    fontFamily = AstaSans,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 96.sp,
                                    lineHeight = 104.sp,
                                )
                                Text(
                                    stat.label,
                                    color = colors.textSecondary,
                                    fontFamily = AstaSans,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp,
                                )
                            }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "이 차에서 다른 점",
                            color = colors.textTertiary,
                            fontFamily = AstaSans,
                            fontSize = 20.sp,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(if (dark) R.drawable.miri_stalk_dark else R.drawable.miri_stalk_light),
                                contentDescription = null,
                                modifier = Modifier
                                    .width(220.dp)
                                    .height(120.dp)
                                    .clip(RoundedCornerShape(20.dp)),
                                contentScale = ContentScale.Crop,
                            )
                            Spacer(Modifier.width(20.dp))
                            Text(
                                "와이퍼는 오른쪽 레버예요",
                                color = colors.textPrimary,
                                fontFamily = AstaSans,
                                fontWeight = FontWeight.Bold,
                                fontSize = 26.sp,
                                lineHeight = 36.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun D2ChargeScreen(card: ShownCard?, dark: Boolean) {
    val colors = IviTheme.colors
    val title = card?.title ?: "충전소에 가요"
    val charging = title.contains("충전")
    val port = portValue(card?.lines.orEmpty()).ifEmpty { if (charging) "오른쪽 뒤" else "" }
    val connector = connectorValue(card?.lines.orEmpty(), card?.chips.orEmpty())
    val percent = card?.chargePercent
    ScreenCanvas {
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = Frame.pad, top = 52.dp, end = Frame.pad, bottom = 36.dp),
        ) {
            StatusLine("정차 중 · 출발 전")
            Spacer(Modifier.height(28.dp))
            HeroTitle(title)
            Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1.05f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(
                            if (dark) R.drawable.miri_car_port_dark else R.drawable.miri_car_port_light,
                        ),
                        contentDescription = null,
                        modifier = Modifier.fillMaxHeight(0.92f),
                        contentScale = ContentScale.Fit,
                    )
                }
                Column(
                    Modifier.weight(0.95f),
                    verticalArrangement = Arrangement.spacedBy(28.dp),
                ) {
                    if (percent != null) {
                        Column {
                            SectionLabel(if (charging) "충전" else "연료")
                            Text(
                                "$percent% 남음",
                                color = colors.textPrimary,
                                fontFamily = AstaSans,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 64.sp,
                                lineHeight = 76.sp,
                            )
                        }
                    }
                    if (port.isNotEmpty()) {
                        Column {
                            SectionLabel(if (charging) "충전구" else "주유구")
                            Spacer(Modifier.height(4.dp))
                            Text(
                                port,
                                color = colors.accent,
                                fontFamily = AstaSans,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 48.sp,
                                lineHeight = 60.sp,
                            )
                        }
                    }
                    if (connector != null) {
                        Column {
                            SectionLabel("커넥터")
                            Spacer(Modifier.height(10.dp))
                            Text(
                                connector,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(colors.accentContainer)
                                    .padding(horizontal = 22.dp, vertical = 12.dp),
                                color = colors.accent,
                                fontFamily = AstaSans,
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}
