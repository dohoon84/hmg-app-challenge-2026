package com.krazylabs.miri.ui.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.krazylabs.miri.R
import com.krazylabs.miri.mvp.CheckItem
import com.krazylabs.miri.mvp.CheckKind
import com.krazylabs.miri.mvp.CheckRules
import com.krazylabs.miri.mvp.ReturnGuidanceFeature
import com.krazylabs.miri.ui.ShownCard
import com.krazylabs.miri.ui.theme.IviTheme

/** E2 app area only: one vehicle glyph on the canvas. The system chrome stays outside the app. */
@Composable
fun DrivingMark(dark: Boolean) {
    val colors = IviTheme.colors
    SpecFrame {
        SpecIcon(R.drawable.miri_ic_vehicle, 793f, 557f, 96f, 96f, colors.frameTertiary)
    }
}

data class F3Model(
    val label: String,
    val hero: String,
    val heroRest: String,
    val sentence: String,
    val leftLabel: String? = null,
    val leftValue: String? = null,
    val rightLabel: String? = null,
    val rightValue: String? = null,
    val showPort: Boolean = true,
    val sentenceX: Float = 880f,
)

@Composable
fun F3ReturnScreen(
    card: ShownCard?,
    dark: Boolean,
    onGuide: () -> Unit = {},
    onDone: () -> Unit = {},
    model: F3Model? = null,
    guideNote: String = "",
    showReturnGuide: Boolean = ReturnGuidanceFeature.visible(),
) {
    val colors = IviTheme.colors
    val face = model ?: f3From(card)
    val car = if (dark) R.drawable.miri_car_top_dark else R.drawable.miri_car_top_light
    SpecFrame {
        Image(
            painterResource(car),
            contentDescription = null,
            modifier = Modifier.figma(110f, 40f, 620f, 1128f),
            contentScale = ContentScale.Crop,
        )
        if (face.showPort) Hotspot(515.07f, 823.95f)
        SpecIcon(R.drawable.miri_ic_parking, 880f, 48f, 32f, 32f, colors.frameSecondary, "정차")
        SpecText("정차 중", 924f, 50f, MiriType.labelMedium(), colors.frameSecondary)
        SpecText("반납 준비", 880f, 112f, MiriType.titleLargeNormal(), colors.frameInk)
        SpecText(face.label, 880f, 300f, MiriType.labelMedium(), colors.frameTertiary)
        Row(Modifier.figma(880f, 336f, 400f, 80f), verticalAlignment = Alignment.Bottom) {
            Text(face.hero, style = MiriType.headlineLargeExtra(), color = colors.frameInk, maxLines = 1)
            Text(face.heroRest, style = MiriType.titleMediumNormal(), color = colors.frameSecondary, maxLines = 1)
        }
        if (face.sentenceX > 880f) {
            SpecIcon(R.drawable.miri_ic_check_40, 880f, 470f, 40f, 40f, colors.accent)
        }
        SpecText(face.sentence, face.sentenceX, 476f, MiriType.bodyLargeNormal(), colors.frameInk)
        val left = face.leftValue?.takeIf { it.isNotBlank() }
        val right = face.rightValue?.takeIf { it.isNotBlank() }
        if (left != null || right != null) {
            Box(Modifier.figma(880f, 580f, 754f, 1f).background(colors.divider))
        }
        if (left != null) {
            SpecText(face.leftLabel.orEmpty(), 880f, 620f, MiriType.labelMedium(), colors.frameTertiary)
            SpecText(left, 880f, 656f, MiriType.titleMediumStrong(), colors.frameInk)
        }
        if (right != null) {
            SpecText(face.rightLabel.orEmpty(), 1180f, 620f, MiriType.labelMedium(), colors.frameTertiary)
            SpecText(right, 1180f, 656f, MiriType.titleMediumStrong(), colors.frameInk)
        }
        SpecButton("다 봤어요", 880f, 1062f, 352f, kind = SpecButtonKind.Point, onClick = onDone)
        if (showReturnGuide) {
            val noted = guideNote.isNotBlank()
            SpecButton(
                "반납 장소로 안내",
                1256f,
                if (noted) 962f else 1062f,
                378f,
                h = if (noted) 200f else 100f,
                kind = SpecButtonKind.Dashed,
                onClick = onGuide,
                note = if (noted) guideNote else "",
            )
        }
    }
}

fun f3ChargePreview() = F3Model(
    "더 충전할 양", "18", "%", "반납까지 약 18% 더 충전해야 해요.",
    "지금 충전량", "62%", "반납 기준", "80%",
)

fun f3DepartPreview() = F3Model(
    "출발", "19:00", " 쯤", "19:00쯤 출발하면 여유 있게 도착해요.",
    "반납", "20:00", "가는 데", "약 42분", showPort = false,
)

fun f3FilledPreview() = F3Model(
    "지금 충전량", "81", "%", "반납 기준만큼 충전됐어요.",
    "반납 기준", "80%", sentenceX = 936f,
)

fun f3FallbackPreview() = F3Model(
    "반납까지", "3", "시간", "반납 20:00까지 3시간 남았어요.",
    showPort = false,
)

fun f3FuelPreview() = F3Model(
    "더 채울 연료", "18", "%", "반납까지 연료를 약 18% 더 채워야 해요.",
    "지금", "62%", "반납 기준", "80%",
)

fun f3From(card: ShownCard?): F3Model = f3Model(card?.lines.orEmpty())

fun f3Model(lines: List<String>): F3Model {
    val depart = lines.firstOrNull { it.contains("출발하면") }
    val sentence = depart
        ?: lines.firstOrNull { !it.contains("%") || it.contains("해요") || it.contains("됐어요") }.orEmpty()
    val rest = lines.filter { it != sentence && !it.startsWith("반납 ") && !it.startsWith("가는 데") }
    if (sentence.contains("출발하면")) {
        val time = sentence.substringBefore("쯤").trim()
        val back = lines.firstOrNull { it.startsWith("반납 ") && it.contains(":") && !it.contains("까지") }
            ?.removePrefix("반납 ")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        val going = lines.firstOrNull { it.startsWith("가는 데") }
            ?.removePrefix("가는 데")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        return F3Model(
            "출발",
            time,
            " 쯤",
            sentence,
            leftLabel = back?.let { "반납" },
            leftValue = back,
            rightLabel = going?.let { "가는 데" },
            rightValue = going,
            showPort = false,
        )
    }
    if (sentence.contains("남았어요") || sentence == "1분도 안 남았어요") {
        val hero = when {
            sentence.startsWith("1분") -> "1" to "분"
            else -> {
                val body = sentence.substringAfter("까지 ").substringBefore(" 남았어요")
                val num = body.takeWhile { it.isDigit() }
                num to body.removePrefix(num)
            }
        }
        return F3Model("반납까지", hero.first, hero.second, sentence, showPort = false)
    }
    if (sentence.contains("기준만큼")) {
        val level = Regex("\\d+").find(sentence)?.value ?: ""
        return F3Model("지금 충전량", level.ifEmpty { "80" }, "%", sentence, "반납 기준", rest.firstOrNull(), sentenceX = 936f)
    }
    val gap = Regex("(\\d+)%").find(sentence)?.groupValues?.get(1) ?: ""
    val fuel = sentence.contains("연료")
    return F3Model(
        label = if (fuel) "더 채울 연료" else "더 충전할 양",
        hero = gap,
        heroRest = "%",
        sentence = sentence.ifEmpty { "반납 준비" },
        leftLabel = if (fuel) "지금" else "지금 충전량",
        leftValue = rest.getOrNull(0),
        rightLabel = "반납 기준",
        rightValue = rest.getOrNull(1),
    )
}

@Composable
fun C1CheckScreen(card: ShownCard?, dark: Boolean) {
    val colors = IviTheme.colors
    val items = card?.checks?.takeIf { it.isNotEmpty() } ?: listOf(
        CheckItem(CheckKind.DOOR, "오른쪽 뒷문"),
        CheckItem(CheckKind.LIGHT, "실내등"),
        CheckItem(CheckKind.LIGHT, "뒤쪽 왼쪽 독서등"),
    )
    val doorOpen = items.any { opensTheBody(it.text) }
    val car = when {
        doorOpen && dark -> R.drawable.miri_car_rear_door_open_dark
        doorOpen -> R.drawable.miri_car_rear_door_open_light
        dark -> R.drawable.miri_car_top_dark
        else -> R.drawable.miri_car_top_light
    }
    SpecFrame {
        Image(painterResource(car), null, Modifier.figma(110f, 40f, 620f, 1128f), contentScale = ContentScale.Crop)
        items.forEachIndexed { index, item ->
            val mark = bodyMark(item.text) ?: return@forEachIndexed
            NumberBadge(mark.first, mark.second, "${index + 1}")
        }
        SpecIcon(R.drawable.miri_ic_parking, 880f, 48f, 32f, 32f, colors.frameSecondary, "정차")
        SpecText("정차 중", 924f, 50f, MiriType.labelMedium(), colors.frameSecondary)
        SpecText(card?.title ?: "반납 전 점검", 880f, 112f, MiriType.titleLargeNormal(), colors.frameInk)
        val rows = listOf(300f, 500f, 760f)
        val dividers = listOf(422f, 682f)
        items.take(rows.size).forEachIndexed { index, item ->
            val y = rows[index]
            val line = item.text
            NumberBadge(880f, y, "${index + 1}")
            if (item.kind == CheckKind.CHARGE || item.kind == CheckKind.FUEL) {
                ChargeLine(line, 944f, y)
            } else {
                SpecText(shortItem(line), 944f, y, MiriType.titleMediumStrong(), colors.frameInk)
            }
            StatusBadge(1558f, y, CheckRules.statusBadge(item.kind))
            if (line.contains("실내등")) {
                SpecText("켜 둔 채 내리면 배터리가 약해질 수 있어요.", 944f, 558f, MiriType.bodySmallNormal(), colors.frameTertiary)
            }
            dividers.getOrNull(index)?.let { divider ->
                Box(Modifier.figma(944f, divider, 690f, 1f).background(colors.divider))
            }
        }
        Box(Modifier.figma(880f, 964f, 754f, 1f).background(colors.divider))
        SpecIcon(R.drawable.miri_ic_belongings, 880f, 996f, 32f, 32f, colors.frameSecondary)
        SpecText(
            card?.note ?: CheckRules.REAR_NOTE,
            928f, 994f, MiriType.bodySmallNormal(), colors.frameTertiary,
            w = 504f, h = 68f, maxLines = 2,
        )
        SpecText(CheckRules.FOOTER, 880f, 1128f, MiriType.bodySmallNormal(), colors.frameTertiary)
    }
}

@Composable
fun C3ReturnScreen(card: ShownCard?, dark: Boolean) {
    val colors = IviTheme.colors
    val car = if (dark) R.drawable.miri_car_q34_dark else R.drawable.miri_car_q34_light
    val trip = card?.lines?.firstOrNull { it.contains("km") }
    SpecFrame {
        Image(painterResource(car), null, Modifier.figma(0f, 24f, 1682f, 900f), contentScale = ContentScale.Crop)
        SpecIcon(R.drawable.miri_ic_parking, 48f, 48f, 32f, 32f, colors.frameSecondary, "정차")
        SpecText("정차 중", 92f, 50f, MiriType.labelMedium(), colors.frameSecondary)
        SpecText("다녀왔어요", 48f, 958f, MiriType.headlineLargeExtra(), colors.frameInk)
        SpecText("반납", 48f, 1062f, MiriType.titleLargeNormal(), colors.frameSecondary)
        if (trip != null) {
            SpecText(trip, 880f, 1128f, MiriType.bodySmallNormal(), colors.frameTertiary, w = 754f, h = 56f, maxLines = 2)
        }
    }
}

@Composable
fun C2DoneScreen(card: ShownCard?, dark: Boolean) {
    val colors = IviTheme.colors
    val car = if (dark) R.drawable.miri_car_q34_dark else R.drawable.miri_car_q34_light
    SpecFrame {
        Image(painterResource(car), null, Modifier.figma(0f, 24f, 1682f, 900f), contentScale = ContentScale.Crop)
        SpecIcon(R.drawable.miri_ic_parking, 48f, 48f, 32f, 32f, colors.frameSecondary, "정차")
        SpecText("정차 중", 92f, 50f, MiriType.labelMedium(), colors.frameSecondary)
        SpecIcon(R.drawable.miri_ic_check_48, 48f, 972f, 48f, 48f, colors.accent)
        SpecText("점검 끝.", 112f, 958f, MiriType.headlineLargeExtra(), colors.frameInk)
        SpecText("좋은 하루 보내세요.", 48f, 1062f, MiriType.titleLargeNormal(), colors.frameSecondary)
        val trip = card?.lines?.firstOrNull { it.contains("km") }
        if (trip != null) {
            SpecText(trip, 1137f, 1128f, MiriType.bodySmallNormal(), colors.frameTertiary)
        }
    }
}

@Composable
fun F4ChecklistScreen(card: ShownCard?, dark: Boolean) {
    val colors = IviTheme.colors
    val car = if (dark) R.drawable.miri_car_rear_door_open_dark else R.drawable.miri_car_rear_door_open_light
    val charge = card?.lines?.firstOrNull { it.contains("반납 기준") } ?: "충전 62% · 반납 기준 80%"
    val open = card?.lines?.firstOrNull { it.contains("문") || it.contains("트렁크") } ?: "오른쪽 뒷문"
    SpecFrame {
        Image(painterResource(car), null, Modifier.figma(110f, 40f, 620f, 1128f), contentScale = ContentScale.Crop)
        Hotspot(564.5f, 678.5f)
        Hotspot(515.07f, 823.95f)
        SpecIcon(R.drawable.miri_ic_parking, 880f, 48f, 32f, 32f, colors.frameSecondary, "정차")
        SpecText("정차 중", 924f, 50f, MiriType.labelMedium(), colors.frameSecondary)
        SpecText(card?.title ?: "반납 장소에 도착했어요", 880f, 112f, MiriType.titleLargeNormal(), colors.frameInk)
        SpecText(
            card?.badge ?: "아직 확인할 곳이 2개 남아 있어요.",
            880f, 180f, MiriType.bodyMediumNormal(), colors.frameSecondary,
        )
        SpecIcon(R.drawable.miri_ic_door, 880f, 316f, 40f, 40f, colors.frameSecondary)
        SpecText(shortItem(open), 944f, 312f, MiriType.titleMediumStrong(), colors.frameInk)
        val doorKind = card?.checks?.firstOrNull {
            it.kind == CheckKind.DOOR || it.kind == CheckKind.WINDOW || it.kind == CheckKind.LIGHT
        }?.kind ?: CheckKind.DOOR
        StatusBadge(1558f, 312f, CheckRules.statusBadge(doorKind))
        Box(Modifier.figma(944f, 420f, 690f, 1f).background(colors.divider))
        SpecIcon(R.drawable.miri_ic_charge, 880f, 476f, 40f, 40f, colors.frameSecondary)
        SpecText("충전 · 반납 기준", 944f, 452f, MiriType.labelMedium(), colors.frameTertiary)
        ChargeLine(charge)
        Box(Modifier.figma(944f, 590f, 690f, 1f).background(colors.divider))
        Box(Modifier.figma(880f, 626f, 754f, 64f).alpha(0.45f)) {
            SpecIcon(R.drawable.miri_ic_check_40, 0f, 10f, 40f, 40f, colors.accent)
            SpecText("실내등", 64f, 6f, MiriType.titleMediumStrong(), colors.frameTertiary)
            SpecText("꺼졌어요", 664f, 14f, MiriType.bodySmallNormal(), colors.frameTertiary)
        }
        Box(Modifier.figma(880f, 770f, 754f, 1f).background(colors.divider))
        SpecIcon(R.drawable.miri_ic_window, 880f, 804f, 32f, 32f, colors.frameSecondary)
        SpecText("창문은 신호가 없어요. 봐 주세요.", 928f, 802f, MiriType.bodySmallNormal(), colors.frameSecondary)
        SpecIcon(R.drawable.miri_ic_belongings, 880f, 884f, 32f, 32f, colors.frameSecondary)
        SpecText(card?.note ?: CheckRules.REAR_NOTE, 928f, 882f, MiriType.bodySmallNormal(), colors.frameTertiary)
        SpecText(CheckRules.FOOTER, 880f, 1128f, MiriType.bodySmallNormal(), colors.frameTertiary)
    }
}

@Composable
private fun BoxScope.ChargeLine(line: String, x: Float = 944f, y: Float = 488f) {
    val colors = IviTheme.colors
    val parts = Regex("(\\d+%)").findAll(line).map { it.value }.toList()
    val annotated = buildAnnotatedString {
        var rest = line
        parts.forEach { token ->
            val at = rest.indexOf(token)
            if (at > 0) withStyle(SpanStyle(color = colors.frameSecondary, fontWeight = androidx.compose.ui.text.font.FontWeight.Normal)) {
                append(rest.substring(0, at))
            }
            withStyle(SpanStyle(color = colors.frameInk, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold)) {
                append(token)
            }
            rest = rest.substring(at + token.length)
        }
        if (rest.isNotEmpty()) {
            withStyle(SpanStyle(color = colors.frameSecondary)) { append(rest) }
        }
    }
    Text(annotated, Modifier.figma(x, y, 500f, 44f), style = MiriType.titleMediumNormal(), maxLines = 1)
}

@Composable
private fun BoxScope.NumberBadge(x: Float, y: Float, label: String) {
    val colors = IviTheme.colors
    Box(
        Modifier
            .figma(x, y, 44f, 44f)
            .background(colors.canvas, RoundedCornerShape(px(22)))
            .border(px(3), colors.accent, RoundedCornerShape(px(22))),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MiriType.bodySmallStrong(), color = colors.accent)
    }
}

@Composable
private fun BoxScope.StatusBadge(x: Float, y: Float, label: String) {
    val colors = IviTheme.colors
    Box(
        Modifier
            .offset(px(x), px(y))
            .height(px(44f))
            .background(colors.cautionBadge, RoundedCornerShape(percent = 50))
            .padding(horizontal = px(14f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MiriType.bodyMediumStrong(), color = colors.onCautionBadge, maxLines = 1)
    }
}

/** Door, trunk, and hood lines use the open-door artwork. A window is not a door. */
private fun opensTheBody(line: String): Boolean {
    if (line.contains("창문")) return false
    return line.contains("문") || line.contains("트렁크") || line.contains("보닛")
}

/** Car-body badge positions from the C1 mockup. A charge line has no mark. */
private fun bodyMark(line: String): Pair<Float, Float>? = when {
    line.contains("오른쪽 뒷문") -> 587f to 701f
    line.contains("실내등") -> 398f to 519.96f
    line.contains("뒤쪽 왼쪽") && line.contains("독서") -> 336f to 672.24f
    else -> null
}

private fun shortItem(line: String): String = line
    .removeSuffix("이 열려 있어요.")
    .removeSuffix("가 열려 있어요.")
    .removeSuffix("이 켜져 있어요.")
    .removeSuffix(" 창문이 열려 있어요.")
    .ifBlank { line }
