package com.krazylabs.miri.ui.design

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.krazylabs.miri.ui.pressScale
import com.krazylabs.miri.ui.theme.AstaSans
import com.krazylabs.miri.ui.theme.IviTheme

/**
 * Figma px of the 1682×1210 app area, as physical pixels.
 * At mdpi this is 1dp. On a denser panel the dp shrinks so the frame stays the same size.
 */
@Composable
fun px(value: Float): Dp = (value / LocalDensity.current.density).dp

@Composable
fun px(value: Int): Dp = px(value.toFloat())

@Composable
fun pxSp(value: Float): TextUnit = with(LocalDensity.current) { px(value).toSp() }

@Composable
fun pxSp(value: Int): TextUnit = pxSp(value.toFloat())

/** Place a layer at Figma x/y/w/h inside a [SpecFrame]. */
@Composable
fun Modifier.figma(x: Float, y: Float, w: Float, h: Float): Modifier =
    offset(px(x), px(y)).size(px(w), px(h))

@Composable
fun Modifier.figma(x: Int, y: Int, w: Int, h: Int): Modifier =
    figma(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat())

private val lineBox = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

/** Named styles from tokens.md. Sizes are Figma px, not the older IviType scale. */
object MiriType {
    @Composable fun headlineLargeExtra() = face(60, 80, FontWeight.ExtraBold)
    @Composable fun headlineSmallStrong() = face(48, 64, FontWeight.Bold)
    @Composable fun titleLargeExtra() = face(40, 52, FontWeight.ExtraBold)
    @Composable fun titleLargeStrong() = face(40, 52, FontWeight.Bold)
    @Composable fun titleLargeNormal() = face(40, 52, FontWeight.Normal)
    @Composable fun titleMediumExtra36() = face(36, 44, FontWeight.ExtraBold)
    @Composable fun titleMediumStrong() = face(36, 44, FontWeight.Bold)
    @Composable fun titleMediumNormal() = face(36, 44, FontWeight.Normal)
    @Composable fun titleSmallStrong() = face(32, 40, FontWeight.Bold)
    @Composable fun bodyLargeStrong() = face(30, 38, FontWeight.Bold)
    @Composable fun bodyLargeNormal() = face(30, 38, FontWeight.Normal)
    @Composable fun bodyMediumNormal() = face(28, 36, FontWeight.Normal)
    @Composable fun bodySmallStrong() = face(26, 34, FontWeight.Bold)
    @Composable fun bodySmallNormal() = face(26, 34, FontWeight.Normal)
    @Composable fun labelMedium() = face(24, 28, FontWeight.Normal)

    @Composable
    private fun face(size: Int, line: Int, weight: FontWeight) = TextStyle(
        fontFamily = AstaSans,
        fontWeight = weight,
        fontSize = pxSp(size),
        lineHeight = pxSp(line),
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = lineBox,
    )
}

@Composable
fun SpecFrame(content: @Composable BoxScope.() -> Unit) {
    val colors = IviTheme.colors
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(px(40)))
            .background(colors.canvas),
        content = content,
    )
}

@Composable
fun BoxScope.SpecText(
    text: String,
    x: Float,
    y: Float,
    style: TextStyle,
    color: Color,
    w: Float? = null,
    h: Float? = null,
    align: TextAlign = TextAlign.Start,
    maxLines: Int = 1,
) {
    val box = if (w != null && h != null) {
        Modifier.figma(x, y, w, h)
    } else {
        Modifier.offset(px(x), px(y))
    }
    Text(
        text,
        modifier = box,
        style = style,
        color = color,
        textAlign = align,
        maxLines = maxLines,
        softWrap = maxLines > 1,
    )
}

@Composable
fun BoxScope.SpecIcon(
    @DrawableRes id: Int,
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    tint: Color,
    description: String? = null,
) {
    Image(
        painter = painterResource(id),
        contentDescription = description,
        modifier = Modifier.figma(x, y, w, h),
        contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(tint),
    )
}

@Composable
fun BoxScope.Hotspot(x: Float, y: Float) {
    val accent = IviTheme.colors.accent
    Box(Modifier.figma(x, y, 88f, 88f), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(px(44))
                .background(accent.copy(alpha = 0.28f), RoundedCornerShape(percent = 50)),
        )
        SpecIconLocal(com.krazylabs.miri.R.drawable.miri_ic_hotspot, accent)
    }
}

@Composable
private fun SpecIconLocal(@DrawableRes id: Int, tint: Color) {
    Image(
        painter = painterResource(id),
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        colorFilter = ColorFilter.tint(tint),
    )
}

enum class SpecButtonKind { Point, Disabled, Filled, Dashed }

@Composable
fun BoxScope.SpecButton(
    label: String,
    x: Float,
    y: Float,
    w: Float,
    h: Float = 100f,
    kind: SpecButtonKind,
    onClick: () -> Unit,
) {
    val colors = IviTheme.colors
    val shape = RoundedCornerShape(px(32))
    val fill = when (kind) {
        SpecButtonKind.Point -> colors.accent
        SpecButtonKind.Disabled -> colors.buttonDisabled
        SpecButtonKind.Filled -> colors.buttonFilled
        SpecButtonKind.Dashed -> Color.Transparent
    }
    val labelColor = when (kind) {
        SpecButtonKind.Point -> colors.onAccent
        SpecButtonKind.Disabled -> colors.frameQuaternary
        SpecButtonKind.Filled -> colors.onFilled
        SpecButtonKind.Dashed -> colors.accent
    }
    val stroke = when (kind) {
        SpecButtonKind.Disabled -> Modifier.border(px(2), colors.dividerSecondary, shape)
        SpecButtonKind.Dashed -> Modifier.border(px(3), colors.accent, shape)
        else -> Modifier
    }
    Box(
        Modifier
            .figma(x, y, w, h)
            .clip(shape)
            .background(fill)
            .then(stroke)
            .quietClick(enabled = kind != SpecButtonKind.Disabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MiriType.titleSmallStrong(), color = labelColor, maxLines = 1)
    }
}

@Composable
fun BoxScope.EdgeFade(x: Float, y: Float, w: Float, h: Float, toRight: Boolean) {
    val canvas = IviTheme.colors.canvas
    val colors = if (toRight) {
        listOf(canvas.copy(alpha = 0f), canvas)
    } else {
        listOf(canvas, canvas.copy(alpha = 0f))
    }
    Box(
        Modifier
            .figma(x, y, w, h)
            .background(Brush.horizontalGradient(colors)),
    )
}

@Composable
fun BoxScope.CroppedImage(
    @DrawableRes id: Int,
    clipX: Float,
    clipY: Float,
    clipW: Float,
    clipH: Float,
    imageX: Float,
    imageY: Float,
    imageW: Float,
    imageH: Float,
    radius: Float = 0f,
) {
    Box(
        Modifier
            .figma(clipX, clipY, clipW, clipH)
            .clip(RoundedCornerShape(px(radius))),
    ) {
        Image(
            painter = painterResource(id),
            contentDescription = null,
            modifier = Modifier
                .offset(px(imageX - clipX), px(imageY - clipY))
                .requiredSize(px(imageW), px(imageH)),
            contentScale = ContentScale.FillBounds,
        )
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
fun DistanceRun(distance: String, emphasize: Boolean) {
    val colors = IviTheme.colors
    val number = distance.removeSuffix(" km").removeSuffix("km").trim()
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            number,
            style = MiriType.titleLargeExtra(),
            color = if (emphasize) colors.accent else colors.frameInk,
            maxLines = 1,
        )
        Text(
            " km",
            style = MiriType.bodyMediumNormal(),
            color = colors.frameTertiary,
            maxLines = 1,
        )
    }
}
