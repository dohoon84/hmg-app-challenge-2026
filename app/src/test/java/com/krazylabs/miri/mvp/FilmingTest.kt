package com.krazylabs.miri.mvp

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FilmingTest {
    @Test
    fun filmingHidesEachDevElement() {
        val chrome = FilmingChrome.of(filming = true, debug = true, returnGuidance = true)
        assertFalse(chrome.scenarioPanel)
        assertFalse(chrome.longPress)
        assertFalse(chrome.applyValues)
        assertFalse(chrome.demoValueIndicators)
        assertFalse(chrome.captions)
        assertFalse(chrome.highlights)
        assertFalse(chrome.returnGuide)
        assertFalse(chrome.foundationOverlay)
        assertFalse(chrome.lampRow)
        assertFalse(chrome.toasts)
        assertNull(chrome.toastText("debug"))
    }

    @Test
    fun aDebugBuildShowsEachDevElementUntilFilmingStarts() {
        val chrome = FilmingChrome.of(filming = false, debug = true, returnGuidance = true)
        assertTrue(chrome.scenarioPanel)
        assertTrue(chrome.longPress)
        assertTrue(chrome.applyValues)
        assertTrue(chrome.demoValueIndicators)
        assertTrue(chrome.captions)
        assertTrue(chrome.highlights)
        assertTrue(chrome.returnGuide)
        assertTrue(chrome.foundationOverlay)
        assertTrue(chrome.lampRow)
        assertTrue(chrome.toasts)
        assertEquals("debug", chrome.toastText("debug"))
    }

    @Test
    fun theDashedGuideStaysHiddenWhenTheFlagIsOff() {
        val chrome = FilmingChrome.of(filming = false, debug = true, returnGuidance = false)
        assertTrue(chrome.scenarioPanel)
        assertFalse(chrome.returnGuide)
    }

    @Test
    fun filmingForcesDarkAndANormalBuildFollowsTheSystem() {
        assertTrue(FilmingChrome.useDark(systemDark = false, filming = true))
        assertTrue(FilmingChrome.useDark(systemDark = true, filming = true))
        assertFalse(FilmingChrome.useDark(systemDark = false, filming = false))
        assertTrue(FilmingChrome.useDark(systemDark = true, filming = false))
    }

    @Test
    fun filmingIsAbsentFromRelease() {
        assertFalse(FilmingChrome.ships(false))
        val chrome = FilmingChrome.of(filming = false, debug = false, returnGuidance = true)
        assertFalse(chrome.scenarioPanel)
        assertFalse(chrome.longPress)
        assertFalse(chrome.applyValues)
        assertFalse(chrome.demoValueIndicators)
        assertFalse(chrome.captions)
        assertFalse(chrome.highlights)
        assertFalse(chrome.returnGuide)
        assertFalse(chrome.foundationOverlay)
        assertFalse(chrome.lampRow)
        assertFalse(chrome.toasts)
        assertFalse(FilmingChrome.useDark(systemDark = false, filming = false))

        val root = moduleRoot()
        val debugManifest = File(root, "src/debug/AndroidManifest.xml").readText()
        assertTrue(debugManifest.contains("FilmingReceiver"))
        assertTrue(debugManifest.contains("android:exported=\"true\""))
        assertFalse(debugManifest.contains("uses-permission"))
        listOf("main", "release", "pleos", "fake").forEach { sourceSet ->
            val dir = File(root, "src/$sourceSet")
            assertTrue(dir.isDirectory)
            dir.walkTopDown()
                .filter { it.isFile && (it.extension == "kt" || it.extension == "xml") }
                .forEach { file ->
                    assertFalse(file.path, file.readText().contains("FilmingReceiver"))
                }
        }
        assertTrue(File(root, "src/debug/java/com/krazylabs/miri/mvp/FilmingReceiver.kt").isFile)
        assertFalse(File(root, "src/pleosDebug/AndroidManifest.xml").readText().contains("uses-permission"))
    }

    @Test
    fun theReceiverParsesFilmingAndAutoPlay() {
        assertEquals(
            FilmingCommand.Set(true),
            FilmingCommands.parse(FilmingCommands.ACTION_FILMING, FilmingExtras(on = true)),
        )
        assertEquals(
            FilmingCommand.Set(false),
            FilmingCommands.parse(FilmingCommands.ACTION_FILMING, FilmingExtras(on = false)),
        )
        assertEquals(
            FilmingCommand.Ignore,
            FilmingCommands.parse(FilmingCommands.ACTION_FILMING, FilmingExtras()),
        )
        assertEquals(
            FilmingCommand.Play(null, 1f),
            FilmingCommands.parse(FilmingCommands.ACTION_AUTOPLAY, FilmingExtras(start = true)),
        )
        assertEquals(
            FilmingCommand.Stop,
            FilmingCommands.parse(FilmingCommands.ACTION_AUTOPLAY, FilmingExtras(start = false, step = "F3")),
        )
        assertEquals(
            FilmingCommand.Ignore,
            FilmingCommands.parse(FilmingCommands.ACTION_AUTOPLAY, FilmingExtras()),
        )
        assertEquals(
            FilmingCommand.Ignore,
            FilmingCommands.parse("com.krazylabs.miri.action.OTHER", FilmingExtras(on = true, start = true)),
        )
    }

    @Test
    fun theReceiverParsesEachStepAndTheHoldScale() {
        FilmingCommands.steps.forEach { step ->
            assertEquals(step, FilmingCommands.resolveStep(step))
            assertEquals(
                FilmingCommand.Play(step, 2f),
                FilmingCommands.parse(
                    FilmingCommands.ACTION_AUTOPLAY,
                    FilmingExtras(start = true, step = step, hold = 2f),
                ),
            )
        }
        assertEquals("C2", FilmingCommands.resolveStep(FilmingCommands.DONE_ALIAS))
        assertEquals(
            FilmingCommand.Play("C2", 1.5f),
            FilmingCommands.parse(
                FilmingCommands.ACTION_AUTOPLAY,
                FilmingExtras(start = true, step = "C3", hold = 1.5f),
            ),
        )
        assertEquals(
            FilmingCommand.Play("F1", 1f),
            FilmingCommands.parse(
                FilmingCommands.ACTION_AUTOPLAY,
                FilmingExtras(start = true, step = "F1", hold = 0f),
            ),
        )
        assertEquals(
            FilmingCommand.Play(null, 1f),
            FilmingCommands.parse(
                FilmingCommands.ACTION_AUTOPLAY,
                FilmingExtras(start = true, step = "  ", hold = -2f),
            ),
        )
        assertEquals(
            FilmingCommand.Ignore,
            FilmingCommands.parse(
                FilmingCommands.ACTION_AUTOPLAY,
                FilmingExtras(start = true, step = "NOPE"),
            ),
        )
    }

    private fun moduleRoot(): File {
        val cwd = File("").absoluteFile
        return when {
            File(cwd, "src/debug/AndroidManifest.xml").isFile -> cwd
            File(cwd, "app/src/debug/AndroidManifest.xml").isFile -> File(cwd, "app")
            else -> error("filming manifest not found from $cwd")
        }
    }
}
