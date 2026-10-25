package com.krazylabs.miri.ui.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.krazylabs.miri.mvp.CheckItem
import com.krazylabs.miri.mvp.CheckKind
import com.krazylabs.miri.ui.ShownCard
import com.krazylabs.miri.ui.theme.FoundationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun theGuideNoteSitsInsideTheDashedButton() {
        rule.setContent {
            FoundationTheme {
                Box(Modifier.requiredSize(1682.dp, 1210.dp)) {
                    F3ReturnScreen(
                        card = null,
                        dark = false,
                        model = f3DepartPreview(),
                        guideNote = "권한 공개 후 제공",
                        showReturnGuide = true,
                    )
                }
            }
        }
        val button = rule.onNodeWithTag(SpecDash.TAG).fetchSemanticsNode().boundsInRoot
        val note = rule.onNodeWithText("권한 공개 후 제공").fetchSemanticsNode().boundsInRoot
        assertTrue(note.top >= button.top)
        assertTrue(note.bottom <= button.bottom + 1f)
        rule.onNodeWithText("반납 장소로 안내").assertExists()
        rule.onNodeWithText("20:00").assertExists()
        rule.onNodeWithText("약 42분").assertExists()
    }

    @Test
    fun aSettledRearDoorReadsClosed() {
        rule.setContent {
            FoundationTheme {
                Box(Modifier.requiredSize(1682.dp, 1210.dp)) {
                    C1CheckScreen(
                        card = ShownCard(
                            title = "반납 전 점검",
                            lines = listOf("오른쪽 뒷문이 열려 있어요."),
                            checks = listOf(
                                CheckItem(CheckKind.DOOR, "오른쪽 뒷문이 열려 있어요.", settled = true),
                            ),
                        ),
                        dark = false,
                    )
                }
            }
        }
        rule.onNodeWithText("닫힘").assertExists()
        rule.onNodeWithText("오른쪽 뒷문", substring = true).assertExists()
        rule.onAllNodesWithText("열림").assertCountEquals(0)
    }

    @Test
    fun aSettledRearDoorDimsWithACheckInsteadOfTheCautionPill() {
        rule.setContent {
            FoundationTheme {
                Box(Modifier.requiredSize(1682.dp, 1210.dp)) {
                    C1CheckScreen(
                        card = ShownCard(
                            title = "반납 전 점검",
                            lines = listOf("오른쪽 뒷문이 열려 있어요."),
                            checks = listOf(
                                CheckItem(CheckKind.DOOR, "오른쪽 뒷문이 열려 있어요.", settled = true),
                            ),
                        ),
                        dark = false,
                    )
                }
            }
        }
        rule.onNodeWithText("닫힘").assertExists()
        rule.onAllNodesWithText("열림").assertCountEquals(0)
        rule.onAllNodesWithTag(CheckRowStyle.CAUTION).assertCountEquals(0)
        rule.onNodeWithTag(CheckRowStyle.CHECK).assertExists()
        rule.onNodeWithTag(CheckRowStyle.SETTLED).assert(
            SemanticsMatcher("settled row alpha") { node ->
                node.config.getOrNull(CheckRowStyle.alphaKey) == CheckRowStyle.ALPHA
            },
        )
    }

    @Test
    fun aHiddenGuideOmitsTheButtonAndTheNote() {
        rule.setContent {
            FoundationTheme {
                Box(Modifier.requiredSize(1682.dp, 1210.dp)) {
                    F3ReturnScreen(
                        card = null,
                        dark = true,
                        model = f3DepartPreview(),
                        guideNote = "권한 공개 후 제공",
                        showReturnGuide = false,
                    )
                }
            }
        }
        rule.onAllNodesWithTag(SpecDash.TAG).assertCountEquals(0)
        rule.onAllNodesWithText("권한 공개 후 제공").assertCountEquals(0)
        rule.onNodeWithText("다 봤어요").assertExists()
    }
}
