package com.krazylabs.miri.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import com.krazylabs.miri.R
import com.krazylabs.miri.mvp.F2Draft
import com.krazylabs.miri.mvp.PLACE_SEARCH
import com.krazylabs.miri.mvp.PLACE_SEARCH_EMPTY
import com.krazylabs.miri.ui.PlaceChip
import com.krazylabs.miri.ui.theme.IviTheme

@Composable
fun F2ReturnScreen(
    draft: F2Draft,
    places: List<PlaceChip>,
    clock: WallClock,
    onOpenDates: () -> Unit,
    onDate: (Int) -> Unit,
    onTime: (Int) -> Unit,
    onPlace: (String) -> Unit,
    onDone: () -> Unit,
    onCloseDates: () -> Unit = {},
    sheetPressedOffset: Int? = null,
    searchEnabled: Boolean = true,
    placeSheetOpen: Boolean = false,
    bookmarks: List<PlaceChip> = emptyList(),
    sheetRecents: List<PlaceChip> = emptyList(),
    onOpenSearch: () -> Unit = {},
    onCloseSearch: () -> Unit = {},
) {
    val colors = IviTheme.colors
    val today = clock.today()
    val segment = segmentIndex(draft.dayOffset, draft.epochDay, today)
    val slotDay = if (segment == 0) 0 else segment
    val minutes = visibleReturnMinutes(slotDay, clock.minuteOfDay())
    val selectedIndex = draft.minuteOfDay?.let { minutes.indexOf(it) } ?: -1
    val scrollPx = if (selectedIndex >= 0) (selectedIndex * 164 - 402).coerceAtLeast(0) else 0
    SpecFrame {
        SpecIcon(R.drawable.miri_ic_parking, 48f, 48f, 32f, 32f, colors.frameSecondary, "정차")
        SpecText("정차 중", 92f, 50f, MiriType.labelMedium(), colors.frameSecondary)
        SpecText(F2Draft.TITLE, 48f, 132f, MiriType.headlineSmallStrong(), colors.frameInk)
        SpecText("반납", 48f, 272f, MiriType.labelMedium(), colors.frameTertiary)
        Summary(today, draft)
        Box(Modifier.figma(48, 430, 760, 1).background(colors.divider))
        SpecText("날짜", 48f, 470f, MiriType.labelMedium(), colors.frameTertiary)
        Segment(segment, onToday = { onDate(0) }, onTomorrow = { onDate(1) }, onPick = onOpenDates)
        SpecText("시각", 48f, 666f, MiriType.labelMedium(), colors.frameTertiary)
        TimeRow(minutes, draft.minuteOfDay, scrollPx, showLeftFade = scrollPx > 0, onTime)
        SpecText("반납 장소", 880f, 272f, MiriType.labelMedium(), colors.frameTertiary)
        places.forEachIndexed { index, place ->
            PlaceRow(
                place = place,
                y = 312f + index * 168f,
                selected = place.id == draft.placeId,
                onClick = { onPlace(place.id) },
            )
            if (index < places.lastIndex) {
                Box(Modifier.figma(880f, 474f + index * 168f, 754f, 1f).background(colors.divider))
            }
        }
        val searchY = 312f + places.size * 168f
        FinderRow(searchY, searchEnabled, onOpenSearch)
        val hintY = (searchY + 168f).coerceAtLeast(840f)
        if (hintY <= 1120f) {
            SpecIcon(R.drawable.miri_ic_annotation, 880f, hintY, 32f, 32f, colors.frameTertiary)
            SpecText("장소를 모르면 시각만 저장해요.", 928f, hintY, MiriType.bodySmallNormal(), colors.frameTertiary)
        }
        SpecButton(
            label = "다 됐어요",
            x = 48f,
            y = 1062f,
            w = 352f,
            kind = if (draft.doneEnabled) SpecButtonKind.Point else SpecButtonKind.Disabled,
            onClick = onDone,
        )
        if (!draft.doneEnabled) {
            SpecText("시각을 고르면 누를 수 있어요.", 432f, 1098f, MiriType.bodySmallNormal(), colors.frameTertiary)
        }
        if (draft.dateSheetOpen) {
            DateSheet(
                today = today,
                pressed = sheetPressedOffset ?: draft.dayOffset,
                onPick = onDate,
                onClose = onCloseDates,
            )
        }
        if (placeSheetOpen) {
            PlaceSheet(bookmarks, sheetRecents, onPlace, onCloseSearch)
        }
    }
}

@Composable
private fun BoxScope.Summary(today: java.time.LocalDate, draft: F2Draft) {
    val colors = IviTheme.colors
    val day = returnDayWord(today, draft.dayOffset, draft.epochDay)
    val minute = draft.minuteOfDay
    if (minute == null) {
        Row(Modifier.offset(px(48f), px(318f)), verticalAlignment = Alignment.CenterVertically) {
            Text("$day ", style = MiriType.titleLargeNormal(), color = colors.frameSecondary, maxLines = 1)
            Text("· 시각을 골라 주세요", style = MiriType.titleLargeNormal(), color = colors.frameTertiary, maxLines = 1)
        }
    } else {
        // One line, so the 40px date and the 60px time share a baseline.
        // Runs differ in size, weight, and the two spec colours. The space
        // after the day word is the gap in "오늘 20:00".
        val dayStyle = MiriType.titleLargeNormal()
        val timeStyle = MiriType.headlineLargeExtra()
        Text(
            buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        color = colors.frameSecondary,
                        fontSize = dayStyle.fontSize,
                        fontWeight = dayStyle.fontWeight,
                    ),
                ) { append("$day ") }
                withStyle(
                    SpanStyle(
                        color = colors.frameInk,
                        fontSize = timeStyle.fontSize,
                        fontWeight = timeStyle.fontWeight,
                    ),
                ) { append(clockLabel(minute)) }
            },
            Modifier.offset(px(48f), px(304f)),
            style = timeStyle,
            maxLines = 1,
        )
    }
}

@Composable
private fun BoxScope.Segment(selected: Int, onToday: () -> Unit, onTomorrow: () -> Unit, onPick: () -> Unit) {
    val colors = IviTheme.colors
    Box(Modifier.figma(48f, 508f, 760f, 108f).background(colors.track, RoundedCornerShape(percent = 50)))
    val labels = listOf("오늘" to onToday, "내일" to onTomorrow, "날짜" to onPick)
    labels.forEachIndexed { index, (label, action) ->
        val x = 54f + index * 249.333f
        val on = index == selected
        Box(
            Modifier
                .figma(x, 514f, 249.333f, 96f)
                .clip(RoundedCornerShape(percent = 50))
                .background(if (on) colors.accent else androidx.compose.ui.graphics.Color.Transparent)
                .quietClick(onClick = action),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                style = if (on) MiriType.bodyLargeStrong() else MiriType.bodyLargeNormal(),
                color = if (on) colors.onAccent else colors.frameSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun BoxScope.TimeRow(
    minutes: List<Int>,
    selected: Int?,
    scrollPx: Int,
    showLeftFade: Boolean,
    onTime: (Int) -> Unit,
) {
    val colors = IviTheme.colors
    Box(Modifier.figma(48f, 704f, 760f, 100f).clip(RoundedCornerShape(0))) {
        Row(
            Modifier.horizontalScroll(rememberScrollState(scrollPx), enabled = true),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            minutes.forEachIndexed { index, minute ->
                val on = minute == selected
                Box(
                    Modifier
                        .padding(end = px(14))
                        .size(px(150), px(100))
                        .clip(RoundedCornerShape(percent = 50))
                        .background(if (on) colors.accent else androidx.compose.ui.graphics.Color.Transparent)
                        .border(
                            px(if (on) 0 else 2),
                            if (on) androidx.compose.ui.graphics.Color.Transparent else colors.borderStrong,
                            RoundedCornerShape(percent = 50),
                        )
                        .quietClick { onTime(minute) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        clockLabel(minute),
                        style = if (on) MiriType.bodyLargeStrong() else MiriType.bodyLargeNormal(),
                        color = if (on) colors.onAccent else colors.frameInk,
                    )
                }
            }
        }
    }
    if (showLeftFade) EdgeFade(48f, 704f, 110f, 100f, toRight = false)
    EdgeFade(668f, 704f, 140f, 100f, toRight = true)
}

@Composable
private fun BoxScope.PlaceRow(place: PlaceChip, y: Float, selected: Boolean, onClick: () -> Unit) {
    val colors = IviTheme.colors
    val shape = RoundedCornerShape(px(24))
    Box(
        Modifier
            .figma(864f, y, 786f, 156f)
            .clip(shape)
            .background(if (selected) colors.accentContainer else androidx.compose.ui.graphics.Color.Transparent)
            .quietClick(onClick = onClick),
    ) {
        val nameX = if (selected) 72f else 16f
        if (selected) {
            SpecIcon(R.drawable.miri_ic_check_40, 16f, 34f, 40f, 40f, colors.accent)
        }
        SpecText(
            place.label,
            nameX,
            30f,
            MiriType.titleMediumStrong(),
            if (selected) colors.accent else colors.frameInk,
        )
        place.caption?.let {
            SpecText(it, nameX, 86f, MiriType.bodySmallNormal(), colors.frameTertiary)
        }
        place.distance?.let { distance ->
            Box(Modifier.figma(520f, 48f, 250f, 52f), contentAlignment = Alignment.CenterEnd) {
                DistanceRun(distance, emphasize = selected)
            }
        }
    }
}

@Composable
private fun BoxScope.FinderRow(y: Float, enabled: Boolean, onClick: () -> Unit) {
    val colors = IviTheme.colors
    Box(
        Modifier
            .figma(864f, y, 786f, 156f)
            .clip(RoundedCornerShape(px(24)))
            .quietClick(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        SpecText(
            PLACE_SEARCH,
            16f,
            56f,
            MiriType.titleMediumStrong(),
            if (enabled) colors.frameInk else colors.frameTertiary,
        )
    }
}

@Composable
private fun BoxScope.PlaceSheet(
    bookmarks: List<PlaceChip>,
    recents: List<PlaceChip>,
    onPlace: (String) -> Unit,
    onClose: () -> Unit,
) {
    val colors = IviTheme.colors
    Box(Modifier.figma(0, 0, 1682, 1210).background(colors.overlay).quietClick(onClick = onClose))
    Box(
        Modifier
            .figma(0f, 600f, 1682f, 610f)
            .clip(RoundedCornerShape(topStart = px(40), topEnd = px(40)))
            .background(colors.popup),
    ) {
        SpecText(PLACE_SEARCH, 48f, 56f, MiriType.titleLargeNormal(), colors.frameInk)
        Column(
            Modifier
                .offset(px(48f), px(140f))
                .size(px(1586f), px(400f))
                .verticalScroll(rememberScrollState()),
        ) {
            if (bookmarks.isEmpty() && recents.isEmpty()) {
                Text(PLACE_SEARCH_EMPTY, style = MiriType.bodyLargeNormal(), color = colors.frameSecondary)
            } else {
                if (bookmarks.isNotEmpty()) {
                    Text("즐겨찾기", style = MiriType.labelMedium(), color = colors.frameTertiary)
                    bookmarks.forEach { place -> SheetPlace(place) { onPlace(place.id) } }
                }
                if (recents.isNotEmpty()) {
                    Text("최근 목적지", style = MiriType.labelMedium(), color = colors.frameTertiary, modifier = Modifier.padding(top = px(24)))
                    recents.forEach { place -> SheetPlace(place) { onPlace(place.id) } }
                }
            }
        }
    }
    SpecButton("닫기", 1414f, 640f, 220f, kind = SpecButtonKind.Filled, onClick = onClose)
}

@Composable
private fun SheetPlace(place: PlaceChip, onClick: () -> Unit) {
    val colors = IviTheme.colors
    Column(
        Modifier
            .padding(top = px(20))
            .quietClick(onClick = onClick),
    ) {
        Text(place.label, style = MiriType.titleMediumStrong(), color = colors.frameInk, maxLines = 1)
        place.caption?.takeIf { it.isNotBlank() }?.let { caption ->
            Text(caption, style = MiriType.bodySmallNormal(), color = colors.frameTertiary, maxLines = 1)
        }
    }
}

@Composable
private fun BoxScope.DateSheet(
    today: java.time.LocalDate,
    pressed: Int,
    onPick: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val colors = IviTheme.colors
    Box(Modifier.figma(0, 0, 1682, 1210).background(colors.overlay).quietClick(onClick = onClose))
    Box(
        Modifier
            .figma(0f, 600f, 1682f, 610f)
            .clip(RoundedCornerShape(topStart = px(40), topEnd = px(40)))
            .background(colors.popup),
    ) {
        SpecText("반납 날짜", 48f, 56f, MiriType.titleLargeNormal(), colors.frameInk)
        SpecText("${today.monthValue}월", 48f, 124f, MiriType.labelMedium(), colors.frameTertiary)
        SpecText("누르면 바로 고르고 닫혀요.", 48f, 404f, MiriType.bodySmallNormal(), colors.frameTertiary)
        repeat(7) { offset ->
            val date = today.plusDays(offset.toLong())
            val x = 48f + offset * 228f
            val on = offset == pressed
            Box(
                Modifier
                    .figma(x, 196f, 210f, 168f)
                    .clip(RoundedCornerShape(px(32)))
                    .background(if (on) colors.accent else androidx.compose.ui.graphics.Color.Transparent)
                    .border(px(if (on) 0 else 2), if (on) colors.accent else colors.borderStrong, RoundedCornerShape(px(32)))
                    .quietClick { onPick(offset) },
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        sheetDayCaption(today, offset),
                        style = MiriType.labelMedium(),
                        color = if (on) colors.onAccent else colors.frameTertiary,
                    )
                    Text(
                        date.dayOfMonth.toString(),
                        style = MiriType.headlineLargeExtra(),
                        color = if (on) colors.onAccent else colors.frameInk,
                    )
                }
            }
        }
    }
    SpecButton("닫기", 1414f, 640f, 220f, kind = SpecButtonKind.Filled, onClick = onClose)
}
