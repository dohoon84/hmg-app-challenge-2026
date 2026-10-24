package com.krazylabs.miri.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import com.krazylabs.miri.R
import com.krazylabs.miri.ui.theme.IviTheme

enum class VehicleKind { OWN, RENTAL, SHARE }

@Composable
fun F1KindScreen(
    highlighted: VehicleKind?,
    onOwn: () -> Unit,
    onRental: () -> Unit,
    onShare: () -> Unit,
    dark: Boolean,
) {
    val colors = IviTheme.colors
    val car = if (dark) R.drawable.miri_car_q34_dark else R.drawable.miri_car_q34_light
    SpecFrame {
        CroppedImage(
            id = car,
            clipX = 822f, clipY = 0f, clipW = 860f, clipH = 1210f,
            imageX = 62f, imageY = 0f, imageW = 2151f, imageH = 1210f,
        )
        EdgeFade(822f, 0f, 260f, 1210f, toRight = false)
        SpecIcon(R.drawable.miri_ic_parking, 48f, 48f, 32f, 32f, colors.frameSecondary, "정차")
        SpecText("정차 중", 92f, 50f, MiriType.labelMedium(), colors.frameSecondary)
        SpecText("어떤 차로 타고 계세요?", 48f, 132f, MiriType.headlineSmallStrong(), colors.frameInk)
        KindTile(
            x = 48f, y = 320f,
            title = "내 차",
            subtitle = "늘 타는 내 차예요",
            selected = highlighted == VehicleKind.OWN,
            onClick = onOwn,
        )
        KindTile(
            x = 48f, y = 520f,
            title = "렌터카",
            subtitle = "기간을 정해 빌린 차예요",
            selected = highlighted == VehicleKind.RENTAL,
            onClick = onRental,
        )
        KindTile(
            x = 48f, y = 720f,
            title = "쉐어카",
            subtitle = "몇 시간만 빌린 차예요",
            selected = highlighted == VehicleKind.SHARE,
            onClick = onShare,
        )
        SpecIcon(R.drawable.miri_ic_annotation, 48f, 1006f, 32f, 32f, colors.frameTertiary)
        SpecText(
            "고르지 않아도 지금처럼 안내해 드려요.",
            96f, 1006f,
            MiriType.bodySmallNormal(),
            colors.frameTertiary,
        )
    }
}

@Composable
private fun BoxScope.KindTile(
    x: Float,
    y: Float,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = IviTheme.colors
    val shape = RoundedCornerShape(px(32))
    Box(
        Modifier
            .figma(x, y, 760f, 184f)
            .clipQuiet(shape)
            .background(if (selected) colors.accentContainer else colors.canvas)
            .border(
                width = px(if (selected) 3 else 2),
                color = if (selected) colors.accent else colors.divider,
                shape = shape,
            )
            .quietClick(onClick = onClick),
    ) {
        SpecText(
            title,
            40f,
            38f,
            if (selected) MiriType.titleLargeExtra() else MiriType.titleLargeStrong(),
            if (selected) colors.accent else colors.frameInk,
        )
        SpecText(subtitle, 40f, 106f, MiriType.bodySmallNormal(), colors.frameTertiary)
        if (selected) {
            SpecIcon(R.drawable.miri_ic_check_48, 672f, 68f, 48f, 48f, colors.accent)
        }
    }
}

@Composable
private fun Modifier.clipQuiet(shape: Shape): Modifier = clip(shape)
