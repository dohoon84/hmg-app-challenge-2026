package com.krazylabs.miri.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krazylabs.miri.ui.theme.AstaSans

/**
 * Small situational line for a demo recording. Same tone as the DEMO boards:
 * black field, bold white line, gray note that this is not part of the app.
 */
@Composable
fun DemoCaption(text: String, enabled: Boolean, modifier: Modifier = Modifier) {
    if (!enabled || text.isBlank()) return
    Column(
        modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text,
            color = Color.White,
            fontFamily = AstaSans,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            maxLines = 1,
        )
        Text(
            "데모 영상 전용 자막 · 앱 화면 아님",
            color = Color(0xFF999999),
            fontFamily = AstaSans,
            fontSize = 12.sp,
            maxLines = 1,
        )
    }
}
