package com.example

import com.example.service.WakeWordStateManager
import com.example.voice.AcousticFeatureExtractor
import com.example.voice.VoiceProfile
import com.example.voice.VoiceSampleFeatures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class ExampleUnitTest {

    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testRmsCalculation() {
        val silentSamples = ShortArray(1600) { 0 }
        val silentRms = AcousticFeatureExtractor.calculateRms(silentSamples)
        assertEquals(0f, silentRms, 0.001f)

        val loudSamples = ShortArray(1000) { 10000 }
        val loudRms = AcousticFeatureExtractor.calculateRms(loudSamples)
        assertEquals(10000f, loudRms, 1.0f)
    }

    @Test
    fun testSyntheticTonePitchExtraction() {
        val sampleRate = 16000
        val targetFreq = 200.0 // 200 Hz
        val durationSamples = (sampleRate * 0.5).toInt() // 500 ms
        val samples = ShortArray(durationSamples) { i ->
            (sin(2.0 * PI * targetFreq * i / sampleRate) * 16000).toInt().toShort()
        }

        val features = AcousticFeatureExtractor.extractFeatures(samples)
        assertNotNull(features)
        // Check pitch is near 200 Hz
        assertEquals(200f, features!!.pitchHz, 25f)
    }

    @Test
    fun testVoiceProfileMatchesOwnerAndRejectsOtherVoices() {
        // Enrolled user with average pitch 190 Hz
        val sample1 = VoiceSampleFeatures(
            pitchHz = 190f,
            spectralCentroid = 950f,
            lowBandEnergyRatio = 0.45f,
            midBandEnergyRatio = 0.35f,
            highBandEnergyRatio = 0.20f,
            zeroCrossingRate = 45f
        )
        val sample2 = VoiceSampleFeatures(
            pitchHz = 185f,
            spectralCentroid = 920f,
            lowBandEnergyRatio = 0.44f,
            midBandEnergyRatio = 0.36f,
            highBandEnergyRatio = 0.20f,
            zeroCrossingRate = 42f
        )
        val sample3 = VoiceSampleFeatures(
            pitchHz = 195f,
            spectralCentroid = 980f,
            lowBandEnergyRatio = 0.46f,
            midBandEnergyRatio = 0.34f,
            highBandEnergyRatio = 0.20f,
            zeroCrossingRate = 47f
        )

        val profile = VoiceProfile(
            isEnrolled = true,
            sampleCount = 3,
            samples = listOf(sample1, sample2, sample3),
            averagePitchHz = 190f,
            averageCentroid = 950f,
            averageLowRatio = 0.45f,
            averageMidRatio = 0.35f,
            averageHighRatio = 0.20f,
            averageZcr = 45f,
            matchThresholdPercent = 60.0f
        )

        // Same user speaking "Hey Mimi" (pitch 188 Hz)
        val ownerCandidate = VoiceSampleFeatures(
            pitchHz = 188f,
            spectralCentroid = 960f,
            lowBandEnergyRatio = 0.45f,
            midBandEnergyRatio = 0.35f,
            highBandEnergyRatio = 0.20f,
            zeroCrossingRate = 44f
        )
        val ownerMatch = profile.match(ownerCandidate)
        assertTrue(ownerMatch.isMatch)
        assertTrue(ownerMatch.similarityPercent >= 80f)

        // Different person with deep voice (pitch 95 Hz, different timbre)
        val strangerCandidate = VoiceSampleFeatures(
            pitchHz = 95f,
            spectralCentroid = 450f,
            lowBandEnergyRatio = 0.70f,
            midBandEnergyRatio = 0.20f,
            highBandEnergyRatio = 0.10f,
            zeroCrossingRate = 18f
        )
        val strangerMatch = profile.match(strangerCandidate)
        assertFalse(strangerMatch.isMatch)
        assertTrue(strangerMatch.similarityPercent < 60f)
    }

    @Test
    fun testWakeWordStateManager() {
        WakeWordStateManager.setServiceRunning(true, "Listening for 'Hey Mimi'...")
        assertTrue(WakeWordStateManager.isServiceRunning.value)
        assertEquals("Listening for 'Hey Mimi'...", WakeWordStateManager.serviceStatusMessage.value)

        WakeWordStateManager.setServiceRunning(false)
        assertFalse(WakeWordStateManager.isServiceRunning.value)
    }

    @Test
    fun testParseGeminiOpenCommand() {
        val json = """{"action": "open", "app": "youtube"}"""
        val command = com.example.service.AssistantActionManager.parseCommand(json)
        assertNotNull(command)
        assertEquals("open", command?.action)
        assertEquals("youtube", command?.app)
    }

    @Test
    fun testParseGeminiClickCommand() {
        val json = """{"action": "click", "text": "Search"}"""
        val command = com.example.service.AssistantActionManager.parseCommand(json)
        assertNotNull(command)
        assertEquals("click", command?.action)
        assertEquals("Search", command?.text)
    }

    @Test
    fun testParseGeminiEmbeddedCodeBlockCommand() {
        val rawResponse = """
            Sure, I will open YouTube for you right away!
            ```json
            {"action": "open", "app": "youtube"}
            ```
        """.trimIndent()
        val command = com.example.service.AssistantActionManager.parseCommand(rawResponse)
        assertNotNull(command)
        assertEquals("open", command?.action)
        assertEquals("youtube", command?.app)
    }

    @Test
    fun testParseGeminiGlobalNavigationCommands() {
        val homeCommand = com.example.service.AssistantActionManager.parseCommand("""{"action": "home"}""")
        assertNotNull(homeCommand)
        assertEquals("home", homeCommand?.action)

        val backCommand = com.example.service.AssistantActionManager.parseCommand("""{"action": "back"}""")
        assertNotNull(backCommand)
        assertEquals("back", backCommand?.action)
    }

    @Test
    fun testParseGeminiOpenSettingsCommand() {
        val json = """{"action": "open_settings", "setting": "wifi"}"""
        val command = com.example.service.AssistantActionManager.parseCommand(json)
        assertNotNull(command)
        assertEquals("open_settings", command?.action)
        assertEquals("wifi", command?.setting)
    }

    @Test
    fun testParseGeminiFlashlightCommand() {
        val json = """{"action": "flashlight", "state": "on"}"""
        val command = com.example.service.AssistantActionManager.parseCommand(json)
        assertNotNull(command)
        assertEquals("flashlight", command?.action)
        assertEquals("on", command?.state)
    }

    @Test
    fun testParseGeminiPlayYouTubeCommand() {
        val json = """{"action": "play_youtube", "query": "relaxing lofi"}"""
        val command = com.example.service.AssistantActionManager.parseCommand(json)
        assertNotNull(command)
        assertEquals("play_youtube", command?.action)
        assertEquals("relaxing lofi", command?.query)
    }

    @Test
    fun testParseNonActionTextReturnsNull() {
        val normalText = "The capital of France is Paris. How else can I help?"
        val command = com.example.service.AssistantActionManager.parseCommand(normalText)
        org.junit.Assert.assertNull(command)
    }

    @Test
    fun testParseMimiPersonaResponseWithReplyAndActions() {
        val json = """
            {
               "reply": "দোস্ত, ইউটিউব ওপেন করে দিচ্ছি!",
               "actions": [
                  {"type": "OPEN_APP", "target": "com.google.android.youtube"}
               ]
            }
        """.trimIndent()

        val mimiResponse = com.example.service.AssistantActionManager.parseMimiResponse(json)
        assertEquals("দোস্ত, ইউটিউব ওপেন করে দিচ্ছি!", mimiResponse.reply)
        assertEquals(1, mimiResponse.actions.size)
        val action = mimiResponse.actions[0]
        assertEquals("open", action.action)
        assertEquals("com.google.android.youtube", action.app)
    }

    @Test
    fun testParseMimiPersonaGlobalActionAndClick() {
        val json = """
            {
               "reply": "I got you! Going to home screen now.",
               "actions": [
                  {"type": "GLOBAL_ACTION", "action": "HOME"}
               ]
            }
        """.trimIndent()

        val mimiResponse = com.example.service.AssistantActionManager.parseMimiResponse(json)
        assertEquals("I got you! Going to home screen now.", mimiResponse.reply)
        assertEquals(1, mimiResponse.actions.size)
        assertEquals("home", mimiResponse.actions[0].action)
    }

    @Test
    fun testParseMimiChatOnlyResponseWithoutActions() {
        val json = """
            {
               "reply": "I'm doing awesome, buddy! How about you?",
               "actions": []
            }
        """.trimIndent()

        val mimiResponse = com.example.service.AssistantActionManager.parseMimiResponse(json)
        assertEquals("I'm doing awesome, buddy! How about you?", mimiResponse.reply)
        assertTrue(mimiResponse.actions.isEmpty())
    }

    @Test
    fun testAiRouterRoutingLogic() {
        // Under 8 words -> Route to Groq
        assertTrue(com.example.data.AiRouter.shouldRouteToGroq("YouTube"))
        assertTrue(com.example.data.AiRouter.shouldRouteToGroq("ইউটিউব খোলো"))
        assertTrue(com.example.data.AiRouter.shouldRouteToGroq("Turn on the flashlight"))

        // Contains simple keyword -> Route to Groq even if longer than 8 words
        val longWithKeyword = "Mimi please could you kindly open the camera app right now for me"
        assertTrue(com.example.data.AiRouter.shouldRouteToGroq(longWithKeyword))

        val longBengaliWithKeyword = "মিমি তুমি কি অনুগ্রহ করে আমার ফোনের ফ্ল্যাশলাইট অন করতে পারবে প্লিজ"
        assertTrue(com.example.data.AiRouter.shouldRouteToGroq(longBengaliWithKeyword))

        // Long complex prompt with 8 or more words and NO simple keywords -> Route to Gemini
        val longComplexPrompt = "Can you explain the detailed differences between quantum physics and classical mechanics in simple everyday terms?"
        assertFalse(com.example.data.AiRouter.shouldRouteToGroq(longComplexPrompt))
    }

    @Test
    fun testParseGeminiClickWithCoordinatesCommand() {
        val json = """{"action": "click", "x": 450.5, "y": 920.0}"""
        val command = com.example.service.AssistantActionManager.parseCommand(json)
        assertNotNull(command)
        assertEquals("click", command?.action)
        assertEquals(450.5f, command?.x ?: 0f, 0.01f)
        assertEquals(920.0f, command?.y ?: 0f, 0.01f)
    }
}

