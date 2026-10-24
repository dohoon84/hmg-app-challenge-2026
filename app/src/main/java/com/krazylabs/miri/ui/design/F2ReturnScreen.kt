package com.krazylabs.miri.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krazylabs.miri.mvp.F2Draft
import com.krazylabs.miri.ui.PlaceChip
import com.krazylabs.miri.ui.theme.AstaSans
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
) {
    val today = clock.today()
    val colors = IviTheme.colors
    val selectedIndex = segmentIndex(draft.dayOffset, draft.epochDay, today)
    ScreenCanvas {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(start = Frame.pad, top = 52.dp, end = Frame.pad, bottom = 36.dp),
            ) {
                StatusLine("정차 중")
                Spacer(Modifier.height(36.dp))
                HeroTitle(F2Draft.TITLE)
                Spacer(Modifier.height(28.dp))
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    Column(Modifier.weight(1.05f).fillMaxHeight()) {
                        SectionLabel("반납")
                        Spacer(Modifier.height(12.dp))
                        Text(
                            returnSummary(today, draft.dayOffset, draft.epochDay, draft.minuteOfDay),
                            color = if (draft.minuteOfDay == null) colors.textSecondary else colors.textPrimary,
                            fontFamily = AstaSans,
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp,
                            lineHeight = 42.sp,
                        )
                        Spacer(Modifier.height(28.dp))
                        DaySegment(
                            selected = selectedIndex,
                            onToday = { onDate(0) },
                            onTomorrow = { onDate(1) },
                            onPickDate = onOpenDates,
                        )
                        Spacer(Modifier.height(28.dp))
                        SectionLabel("시각")
                        Spacer(Modifier.height(16.dp))
                        TimeChipRow(
                            dayOffset = draft.dayOffset,
                            nowMinute = clock.minuteOfDay(),
                            selected = draft.minuteOfDay,
                            onTime = onTime,
                        )
                        Spacer(Modifier.weight(1f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            InfoMark()
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "장소를 모르면 시각만 저장해요.",
                                color = colors.textTertiary,
                                fontFamily = AstaSans,
                                fontSize = 20.sp,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PrimaryButton(F2Draft.DONE, draft.doneEnabled, onDone)
                            if (!draft.doneEnabled) {
                                Spacer(Modifier.width(20.dp))
                                Text(
                                    F2Draft.DONE_HINT,
                                    color = colors.textTertiary,
                                    fontFamily = AstaSans,
                                    fontSize = 20.sp,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(36.dp))
                    Column(Modifier.weight(0.95f).fillMaxHeight()) {
                        SectionLabel("반납 장소")
                        Spacer(Modifier.height(8.dp))
                        places.forEach { place ->
                            PlaceRow(
                                place = place,
                                selected = place.id == draft.placeId,
                                onClick = { onPlace(place.id) },
                            )
                        }
                    }
                }
            }
            if (draft.dateSheetOpen) {
                DateSheet(
                    today = today,
                    selectedOffset = draft.dayOffset,
                    onDate = onDate,
                )
            }
        }
    }
}

@Composable
private fun DaySegment(
    selected: Int,
    onToday: () -> Unit,
    onTomorrow: () -> Unit,
    onPickDate: () -> Unit,
) {
    val colors = IviTheme.colors
    val labels = listOf("오늘" to onToday, "내일" to onTomorrow, "날짜" to onPickDate)
    Row(
        Modifier
            .fillMaxWidth()
            .height(Frame.segmentHeight)
            .clip(RoundedCornerShape(54.dp))
            .background(colors.track)
            .padding(6.dp),
    ) {
        labels.forEachIndexed { index, (label, action) ->
            val on = index == selected
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(48.dp))
                    .background(if (on) colors.accent else Color.Transparent)
                    .quietClick(onClick = action),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (on) colors.onAccent else colors.textPrimary,
                    fontFamily = AstaSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                )
            }
        }
    }
}

@Composable
private fun TimeChipRow(
    dayOffset: Int,
    nowMinute: Int,
    selected: Int?,
    onTime: (Int) -> Unit,
) {
    val slots = visibleReturnMinutes(dayOffset, nowMinute)
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val colors = IviTheme.colors
    LaunchedEffect(selected, slots) {
        val index = selected?.let { slots.indexOf(it) } ?: -1
        if (index > 0) {
            val pitch = with(density) { (Frame.chipWidth + Frame.chipGap).toPx() }
            val target = (index * pitch - pitch).toInt().coerceAtLeast(0)
            scroll.scrollTo(target)
        }
    }
    Box(Modifier.fillMaxWidth().height(Frame.chipHeight)) {
        Row(
            Modifier
                .fillMaxSize()
                .horizontalScroll(scroll),
            horizontalArrangement = Arrangement.spacedBy(Frame.chipGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            slots.forEach { minute ->
                val on = minute == selected
                val shape = RoundedCornerShape(50.dp)
                Box(
                    Modifier
                        .width(Frame.chipWidth)
                        .height(Frame.chipHeight)
                        .clip(shape)
                        .background(if (on) colors.accent else Color.Transparent)
                        .border(if (on) 0.dp else 1.5.dp, if (on) Color.Transparent else colors.outline, shape)
                        .quietClick { onTime(minute) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        clockLabel(minute),
                        color = if (on) colors.onAccent else colors.textPrimary,
                        fontFamily = AstaSans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                    )
                }
            }
        }
        val fade = colors.canvas
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .width(72.dp)
                .fillMaxHeight()
                .background(Brush.horizontalGradient(listOf(Color.Transparent, fade))),
        )
        if (scroll.value > 0) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .width(72.dp)
                    .fillMaxHeight()
                    .background(Brush.horizontalGradient(listOf(fade, Color.Transparent))),
            )
        }
    }
}

@Composable
private fun PlaceRow(place: PlaceChip, selected: Boolean, onClick: () -> Unit) {
    val colors = IviTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(Frame.radius))
            .background(if (selected) colors.accentContainer else Color.Transparent)
            .quietClick(onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            CheckMark(colors.accent, Modifier.padding(end = 8.dp))
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                place.label,
                color = colors.textPrimary,
                fontFamily = AstaSans,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                lineHeight = 34.sp,
            )
            place.caption?.let { caption ->
                Text(
                    caption,
                    color = colors.textTertiary,
                    fontFamily = AstaSans,
                    fontSize = 18.sp,
                    lineHeight = 24.sp,
                )
            }
        }
        place.distance?.let { distance ->
            Text(
                distance,
                color = if (selected) colors.accent else colors.textTertiary,
                fontFamily = AstaSans,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
            )
        }
    }
}

@Composable
private fun DateSheet(
    today: java.time.LocalDate,
    selectedOffset: Int,
    onDate: (Int) -> Unit,
) {
    val colors = IviTheme.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .quietClick { onDate(selectedOffset) },
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp))
                .background(colors.track)
                .quietClick { }
                .padding(start = 40.dp, end = 40.dp, top = 36.dp, bottom = 40.dp),
        ) {
            Text(
                "${today.monthValue}월",
                color = colors.textPrimary,
                fontFamily = AstaSans,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
            )
            Spacer(Modifier.height(28.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                F2Draft.dateOffsets().forEach { offset ->
                    val date = today.plusDays(offset.toLong())
                    val on = offset == selectedOffset
                    Column(
                        Modifier
                            .weight(1f)
                            .quietClick { onDate(offset) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            sheetDayCaption(today, offset),
                            color = colors.textTertiary,
                            fontFamily = AstaSans,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            date.dayOfMonth.toString(),
                            color = if (on) colors.accent else colors.textPrimary,
                            fontFamily = AstaSans,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 40.sp,
                            lineHeight = 48.sp,
                        )
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(
                "누르면 바로 고르고 닫혀요.",
                color = colors.textTertiary,
                fontFamily = AstaSans,
                fontSize = 18.sp,
            )
        }
    }
}
