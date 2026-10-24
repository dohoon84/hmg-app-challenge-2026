package com.krazylabs.miri.ui.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.krazylabs.miri.R
import com.krazylabs.miri.ui.ShownCard
import com.krazylabs.miri.ui.theme.IviTheme

@Composable
fun D1RoadScreen(card: ShownCard?, dark: Boolean, onDone: () -> Unit = {}) {
    val colors = IviTheme.colors
    val stats = roadStats(card?.lines.orEmpty())
    val note = card?.lines?.firstOrNull { it.startsWith("와이퍼") } ?: "와이퍼는 오른쪽 레버예요"
    val stalk = if (dark) R.drawable.miri_stalk_dark else R.drawable.miri_stalk_light
    SpecFrame {
        CroppedImage(
            id = stalk,
            clipX = 48f, clipY = 48f, clipW = 760f, clipH = 1114f,
            imageX = -874f, imageY = 48f, imageW = 1980f, imageH = 1114f,
            radius = 32f,
        )
        Hotspot(537.3f, 501.96f)
        SpecIcon(R.drawable.miri_ic_parking, 880f, 48f, 32f, 32f, colors.frameSecondary, "정차")
        SpecText("정차 중 · 출발 전", 924f, 50f, MiriType.labelMedium(), colors.frameSecondary)
        SpecText(card?.title ?: "오늘 길", 880f, 112f, MiriType.titleLargeNormal(), colors.frameInk)
        SpecIcon(R.drawable.miri_ic_toll, 880f, 320f, 40f, 40f, colors.frameSecondary)
        CountRun(stats.tolls.toString(), " 곳", 880f, 372f)
        SpecText("톨게이트", 880f, 460f, MiriType.labelMedium(), colors.frameTertiary)
        Box(Modifier.figma(1260f, 320f, 1f, 172f).background(colors.divider))
        SpecIcon(R.drawable.miri_ic_entry, 1292f, 320f, 40f, 40f, colors.frameSecondary)
        CountRun(stats.highways.toString(), " 번", 1292f, 372f)
        SpecText("고속도로 진입", 1292f, 460f, MiriType.labelMedium(), colors.frameTertiary)
        Box(Modifier.figma(880f, 580f, 754f, 1f).background(colors.divider))
        SpecText("이 차에서 다른 점", 880f, 636f, MiriType.labelMedium(), colors.frameTertiary)
        SpecText(note, 880f, 676f, MiriType.titleLargeNormal(), colors.frameInk)
        SpecButton("다 봤어요", 880f, 1062f, 352f, kind = SpecButtonKind.Point, onClick = onDone)
    }
}

@Composable
fun D2ChargeScreen(card: ShownCard?, dark: Boolean, onDone: () -> Unit = {}) {
    val colors = IviTheme.colors
    val car = if (dark) R.drawable.miri_car_top_dark else R.drawable.miri_car_top_light
    val port = portPhrase(card)
    val connector = connectorPhrase(card)
    SpecFrame {
        Image(
            painter = painterResource(car),
            contentDescription = null,
            modifier = Modifier.figma(110f, 40f, 620f, 1128f),
            contentScale = ContentScale.Crop,
        )
        Hotspot(515.07f, 823.95f)
        Box(Modifier.figma(583.07f, 866.95f, 272.93f, 2f).background(colors.accent))
        Box(
            Modifier
                .figma(579.07f, 863.95f, 8f, 8f)
                .background(colors.accent, RoundedCornerShape(percent = 50)),
        )
        SpecIcon(R.drawable.miri_ic_parking, 880f, 48f, 32f, 32f, colors.frameSecondary, "정차")
        SpecText("정차 중 · 출발 전", 924f, 50f, MiriType.labelMedium(), colors.frameSecondary)
        SpecText(card?.title ?: "충전소에 가요", 880f, 112f, MiriType.titleLargeNormal(), colors.frameInk)
        SpecText("충전", 880f, 212f, MiriType.labelMedium(), colors.frameTertiary)
        card?.chargePercent?.let { percent ->
            Pill(880f, 248f, "$percent% 남음")
        }
        SpecText("충전구", 880f, 791.95f, MiriType.labelMedium(), colors.frameTertiary)
        SpecText(port, 880f, 827.95f, MiriType.headlineLargeExtra(), colors.accent)
        connector?.let {
            SpecText("커넥터", 880f, 939.95f, MiriType.labelMedium(), colors.frameTertiary)
            Pill(880f, 975.95f, it)
        }
        SpecButton("다 봤어요", 880f, 1062f, 352f, kind = SpecButtonKind.Point, onClick = onDone)
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.CountRun(value: String, unit: String, x: Float, y: Float) {
    val colors = IviTheme.colors
    Row(Modifier.figma(x, y, 200f, 80f), verticalAlignment = Alignment.Bottom) {
        Text(value, style = MiriType.headlineLargeExtra(), color = colors.frameInk, maxLines = 1)
        Text(unit, style = MiriType.bodyMediumNormal(), color = colors.frameTertiary, maxLines = 1)
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.Pill(x: Float, y: Float, label: String) {
    val colors = IviTheme.colors
    Box(
        Modifier
            .offset(px(x), px(y))
            .background(colors.track, RoundedCornerShape(percent = 50))
            .padding(horizontal = px(16), vertical = px(4)),
    ) {
        Text(label, style = MiriType.labelMedium(), color = colors.frameInk, maxLines = 1)
    }
}

private data class RoadCounts(val tolls: Int, val highways: Int)

private fun roadStats(lines: List<String>): RoadCounts {
    fun take(prefix: String): Int =
        lines.firstOrNull { it.startsWith(prefix) }
            ?.removePrefix(prefix)
            ?.takeWhile { it.isDigit() }
            ?.toIntOrNull()
            ?: 0
    return RoadCounts(take("톨게이트 "), take("고속도로 진입 "))
}

private fun portPhrase(card: ShownCard?): String {
    val line = card?.lines?.firstOrNull { it.startsWith("충전구는 ") || it.startsWith("주유구는 ") }
    if (line != null) {
        return line.removePrefix("충전구는 ").removePrefix("주유구는 ").removeSuffix("에 있어요.").removeSuffix("예요.").trim()
    }
    return when (card?.portName) {
        "REAR_RIGHT" -> "오른쪽 뒤"
        "REAR_LEFT" -> "왼쪽 뒤"
        "FRONT_RIGHT" -> "오른쪽 앞"
        "FRONT_LEFT" -> "왼쪽 앞"
        "REAR" -> "뒤쪽"
        "FRONT" -> "앞쪽"
        else -> "오른쪽 뒤"
    }
}

private fun connectorPhrase(card: ShownCard?): String? {
    val chip = card?.chips?.firstOrNull { it != "전기" && it != "연료" }
    if (chip != null) return chip
    return card?.lines?.firstOrNull { it.startsWith("커넥터") }?.substringAfter(":")?.trim()
}
