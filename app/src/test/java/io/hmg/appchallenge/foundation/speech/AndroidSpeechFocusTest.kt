package io.hmg.appchallenge.foundation.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class AndroidSpeechFocusTest {
    @Test
    fun repeatedRequestsAbandonThePreviousGrant() {
        val context = RuntimeEnvironment.getApplication()
        val focus = AndroidSpeechFocus(context)
        val shadow = Shadows.shadowOf(context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)

        assertNull(shadow.lastAbandonedAudioFocusRequest)
        assertTrue(focus.requestMayDuck {})
        val first = shadow.lastAudioFocusRequest.audioFocusRequest
        assertNull(shadow.lastAbandonedAudioFocusRequest)

        assertTrue(focus.requestMayDuck {})
        assertSame(first, shadow.lastAbandonedAudioFocusRequest)
        val second = shadow.lastAudioFocusRequest.audioFocusRequest
        assertNotSame(first, second)

        assertTrue(focus.requestMayDuck {})
        assertSame(second, shadow.lastAbandonedAudioFocusRequest)
        val third = shadow.lastAudioFocusRequest.audioFocusRequest
        focus.abandon()
        assertSame(third, shadow.lastAbandonedAudioFocusRequest)
        focus.abandon()
        assertSame(third, shadow.lastAbandonedAudioFocusRequest)
    }

    @Test
    fun focusAttributesMatchSpeechAndDuckLossCancels() {
        val context = RuntimeEnvironment.getApplication()
        val focus = AndroidSpeechFocus(context)
        val shadow = Shadows.shadowOf(context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
        val losses = mutableListOf<SpeechFocusLoss>()
        assertTrue(focus.requestMayDuck { losses += it })
        val recorded = shadow.lastAudioFocusRequest
        val attributes = recorded.audioFocusRequest.audioAttributes
        assertEquals(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION, attributes.usage)
        assertEquals(AudioAttributes.CONTENT_TYPE_SPEECH, attributes.contentType)
        recorded.listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
        assertEquals(listOf(SpeechFocusLoss.LOST_TRANSIENT_CAN_DUCK), losses)
    }

    @Test
    fun sharedSpeechAttributesStayOffNavigationGuidance() {
        val attributes = speechAudioAttributes()
        assertEquals(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION, attributes.usage)
        assertEquals(AudioAttributes.CONTENT_TYPE_SPEECH, attributes.contentType)
    }
}
