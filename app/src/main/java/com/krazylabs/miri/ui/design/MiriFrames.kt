package com.krazylabs.miri.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krazylabs.miri.ui.pressScale
import com.krazylabs.miri.ui.theme.AstaSans
import com.krazylabs.miri.ui.theme.IviTheme

object Frame {
    val pad = 48.dp
    val tileWidth = 760.dp
    val tileHeight = 184.dp
    val tileGap = 16.dp
    val radius = 28.dp
    val chipWidth = 150.dp
    val chipHeight = 100.dp
    val chipGap = 16.dp
    val buttonWidth = 352.dp
    val buttonHeight = 100.dp
    val buttonRadius = 32.dp
    val segmentHeight = 108.dp
}

@Composable
fun ScreenCanvas(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .background(IviTheme.colors.canvas),
    ) {
        content()
    }
}

@Composable
fun StatusLine(text: String, modifier: Modifier = Modifier) {
    val colors = IviTheme.colors
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(10.dp)) {
            drawCircle(colors.textTertiary)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text,
            color = colors.textTertiary,
            fontFamily = AstaSans,
            fontWeight = FontWeight.Normal,
            fontSize = 20.sp,
            lineHeight = 28.sp,
        )
    }
}

@Composable
fun HeroTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        color = IviTheme.colors.textPrimary,
        fontFamily = AstaSans,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 40.sp,
        lineHeight = 52.sp,
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        color = IviTheme.colors.textTertiary,
        fontFamily = AstaSans,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    )
}

@Composable
fun CheckMark(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(36.dp)) {
        val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val path = Path().apply {
            moveTo(size.width * 0.16f, size.height * 0.52f)
            lineTo(size.width * 0.40f, size.height * 0.76f)
            lineTo(size.width * 0.84f, size.height * 0.26f)
        }
        drawPath(path, color, style = stroke)
    }
}

@Composable
fun Modifier.quietClick(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this
        .pressScale(interaction)
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
}

@Composable
fun PrimaryButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = Frame.buttonWidth,
) {
    val colors = IviTheme.colors
    Box(
        modifier
            .width(width)
            .size(width = width, height = Frame.buttonHeight)
            .clip(RoundedCornerShape(Frame.buttonRadius))
            .background(if (enabled) colors.accent else colors.track)
            .quietClick(enabled, onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (enabled) colors.onAccent else colors.textDisabled,
            fontFamily = AstaSans,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            lineHeight = 32.sp,
        )
    }
}

@Composable
fun InfoMark(modifier: Modifier = Modifier) {
    val color = IviTheme.colors.textTertiary
    Canvas(modifier.size(22.dp)) {
        drawCircle(color, radius = size.minDimension * 0.08f, center = Offset(size.width / 2f, size.height * 0.28f))
        drawLine(
            color,
            start = Offset(size.width / 2f, size.height * 0.46f),
            end = Offset(size.width / 2f, size.height * 0.78f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}
