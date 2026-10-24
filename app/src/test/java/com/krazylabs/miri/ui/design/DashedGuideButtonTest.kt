package com.krazylabs.miri.ui.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.krazylabs.miri.ui.theme.FoundationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DashedGuideButtonTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun theGuideButtonExposesADashedStroke() {
        assertEquals("14,10", SpecDash.describe())
        rule.setContent {
            FoundationTheme {
                Box(Modifier.requiredSize(420.dp, 160.dp)) {
                    SpecButton(
                        "반납 장소로 안내",
                        0f,
                        0f,
                        378f,
                        kind = SpecButtonKind.Dashed,
                        onClick = {},
                    )
                }
            }
        }
        rule.onNodeWithTag(SpecDash.TAG).assert(
            SemanticsMatcher("dashed outline") { node ->
                node.config.getOrNull(SpecDash.key) == SpecDash.describe()
            },
        )
    }
}
