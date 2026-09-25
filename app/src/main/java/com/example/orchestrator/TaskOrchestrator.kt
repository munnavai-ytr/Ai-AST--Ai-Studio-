package com.example.orchestrator

import android.content.Context
import android.util.Log
import com.example.data.AiRouter
import com.example.service.AssistantActionCommand
import com.example.service.AssistantActionManager
import com.example.service.AssistantActionResult
import com.example.service.VoiceAssistantAccessibilityService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * State representation for multi-step task execution.
 */
sealed class OrchestratorState {
    data object Idle : OrchestratorState()
    data class Planning(val originalCommand: String) : OrchestratorState()
    data class AwaitingUserInput(val question: String) : OrchestratorState()
    data class ExecutingStep(
        val stepDescription: String,
        val stepIndex: Int,
        val totalSteps: Int
    ) : OrchestratorState()
    data class ConfirmingWithUser(val summary: String) : OrchestratorState()
    data class Completed(val summary: String) : OrchestratorState()
    data class Failed(val reason: String) : OrchestratorState()
}

/**
 * Data representation of the structured plan fetched from AI.
 */
data class TaskPlan(
    val steps: List<String> = emptyList(),
    val needsClarification: Boolean = false,
    val clarifyingQuestion: String = ""
)

/**
 * Orchestrator responsible for breaking down high-level user commands into
 * multi-step execution plans, asking clarifying questions when necessary,
 * and dispatching steps sequentially to AssistantActionManager.
 */
object TaskOrchestrator {

    private const val TAG = "TaskOrchestrator"
    private const val MAX_STEPS_LIMIT = 15

    private val _state = MutableStateFlow<OrchestratorState>(OrchestratorState.Idle)
    val state: StateFlow<OrchestratorState> = _state.asStateFlow()

    private var userInputDeferred: CompletableDeferred<String>? = null
    private var currentCommand: String = ""
    private var isBengaliCurrent: Boolean = false

    /**
     * Starts multi-step execution for the given user command.
     * 1. Asks AiRouter for a sequential plan.
     * 2. If needsClarification is true, transitions to AwaitingUserInput and waits for provideUserAnswer().
     * 3. Once plan is confirmed, updates state to ExecutingStep for each step and executes actions.
     * 4. Once all steps conclude, transitions to ConfirmingWithUser.
     */
    suspend fun startTask(
        userCommand: String,
        isBengali: Boolean,
        context: Context? = null
    ) {
        currentCommand = userCommand
        isBengaliCurrent = isBengali
        _state.value = OrchestratorState.Planning(originalCommand = userCommand)

        val planningPrompt = buildPlanningPrompt(userCommand, isBengali)
        val planResult = AiRouter.ask(planningPrompt, isBengali)

        if (planResult.isFailure) {
            val error = planResult.exceptionOrNull()?.message ?: "Failed to generate execution plan"
            Log.e(TAG, "Planning failed: $error")
            _state.value = OrchestratorState.Failed(reason = error)
            return
        }

        val rawPlanText = planResult.getOrNull() ?: ""
        var plan = parseTaskPlan(rawPlanText)

        // If clarification is required from the user before executing
        if (plan.needsClarification && plan.clarifyingQuestion.isNotBlank()) {
            _state.value = OrchestratorState.AwaitingUserInput(question = plan.clarifyingQuestion)

            val deferred = CompletableDeferred<String>()
            userInputDeferred = deferred
            val userAnswer = try {
                deferred.await()
            } finally {
                userInputDeferred = null
            }

            // Resume planning with the user's provided answer
            val refinedPrompt = buildRefinedPlanningPrompt(userCommand, userAnswer, isBengali)
            _state.value = OrchestratorState.Planning(originalCommand = "$userCommand ($userAnswer)")

            val refinedResult = AiRouter.ask(refinedPrompt, isBengali)
            if (refinedResult.isFailure) {
                val error = refinedResult.exceptionOrNull()?.message ?: "Failed to refine plan with user input"
                Log.e(TAG, "Refined planning failed: $error")
                _state.value = OrchestratorState.Failed(reason = error)
                return
            }

            val refinedRawText = refinedResult.getOrNull() ?: ""
            plan = parseTaskPlan(refinedRawText)

            if (plan.needsClarification && plan.clarifyingQuestion.isNotBlank()) {
                _state.value = OrchestratorState.AwaitingUserInput(question = plan.clarifyingQuestion)
                return
            }
        }

        if (plan.steps.isEmpty()) {
            val summary = if (isBengali) {
                "কোনো নির্দিষ্ট পদক্ষেপ প্রয়োজন নেই।"
            } else {
                "No execution steps required."
            }
            _state.value = OrchestratorState.ConfirmingWithUser(summary = summary)
            return
        }

        // Safety limit: ensure task does not exceed 15 steps
        if (plan.steps.size > MAX_STEPS_LIMIT) {
            _state.value = OrchestratorState.Failed("Too many steps, stopping for safety")
            return
        }

        val effectiveContext = context ?: VoiceAssistantAccessibilityService.getInstance()
        val totalSteps = plan.steps.size

        // Execute sequential steps
        for ((index, stepDesc) in plan.steps.withIndex()) {
            val stepIndex = index + 1
            if (stepIndex > MAX_STEPS_LIMIT) {
                _state.value = OrchestratorState.Failed("Too many steps, stopping for safety")
                return
            }

            _state.value = OrchestratorState.ExecutingStep(
                stepDescription = stepDesc,
                stepIndex = stepIndex,
                totalSteps = totalSteps
            )

            // 1. Fetch current screen summary from VoiceAssistantAccessibilityService
            val accessibilityService = VoiceAssistantAccessibilityService.getInstance()
            val screenSummary = accessibilityService?.getCurrentScreenSummary() ?: ""

            // 2. Query AiRouter with current step description + current screen summary to decide action
            val stepPrompt = buildStepActionPrompt(stepDesc, screenSummary, isBengali)
            val actionResult = AiRouter.ask(stepPrompt, isBengali)
            val rawActionText = actionResult.getOrNull() ?: ""

            // Parse returned action, or fallback to parsing step description directly
            val parsedCommand = AssistantActionManager.parseCommand(rawActionText)
                ?: AssistantActionManager.parseCommand(stepDesc)

            if (parsedCommand == null) {
                _state.value = OrchestratorState.Failed("Could not determine actionable command for step $stepIndex: '$stepDesc'")
                return
            }

            if (effectiveContext == null) {
                _state.value = OrchestratorState.Failed("Accessibility Service or Context unavailable for executing step $stepIndex")
                return
            }

            // 3. Execute action via AssistantActionManager
            val executionResult = try {
                AssistantActionManager.executeCommand(effectiveContext, parsedCommand)
            } catch (e: Exception) {
                Log.e(TAG, "Error executing action for step $stepIndex: ${e.message}", e)
                AssistantActionResult(
                    success = false,
                    message = e.localizedMessage ?: "Exception during action execution",
                    actionType = parsedCommand.action
                )
            }

            // 4. If execution succeeded, continue to next step; if failed, transition state to Failed with reason
            if (!executionResult.success) {
                _state.value = OrchestratorState.Failed("Step $stepIndex failed: ${executionResult.message}")
                return
            }

            // 5. Pacing delay of 1500ms between steps for UI animation and screen load
            delay(1500)
        }

        val finalSummary = if (isBengali) {
            "কাজটি সফলভাবে সম্পন্ন হয়েছে ($totalSteps টি ধাপ কার্যকর করা হয়েছে)।"
        } else {
            "Task completed successfully ($totalSteps steps executed)."
        }
        _state.value = OrchestratorState.ConfirmingWithUser(summary = finalSummary)
    }

    /**
     * Resumes task execution by providing user input when in AwaitingUserInput state.
     */
    suspend fun provideUserAnswer(answer: String) {
        val deferred = userInputDeferred
        if (deferred != null && deferred.isActive) {
            deferred.complete(answer)
        } else if (_state.value is OrchestratorState.AwaitingUserInput) {
            startTask("$currentCommand (User answer: $answer)", isBengaliCurrent)
        }
    }

    /**
     * Confirms and marks the task as completed.
     */
    fun completeTask(summary: String? = null) {
        val current = _state.value
        val finalSummary = summary ?: if (current is OrchestratorState.ConfirmingWithUser) {
            current.summary
        } else {
            "Task completed."
        }
        _state.value = OrchestratorState.Completed(summary = finalSummary)
    }

    /**
     * Resets the orchestrator state back to Idle.
     */
    fun reset() {
        userInputDeferred?.cancel()
        userInputDeferred = null
        _state.value = OrchestratorState.Idle
    }

    /**
     * Fails the current task with a specified reason.
     */
    fun failTask(reason: String) {
        userInputDeferred?.cancel()
        userInputDeferred = null
        _state.value = OrchestratorState.Failed(reason = reason)
    }

    /**
     * Parses the AI response to extract structured TaskPlan.
     */
    fun parseTaskPlan(rawText: String): TaskPlan {
        if (rawText.isBlank()) return TaskPlan()

        val cleanJson = extractCleanJson(rawText)
        return try {
            val json = JSONObject(cleanJson)
            val targetJson = if (json.has("steps") || json.has("needsClarification")) {
                json
            } else if (json.has("reply")) {
                val nestedReply = json.optString("reply")
                try {
                    val nestedJson = JSONObject(extractCleanJson(nestedReply))
                    if (nestedJson.has("steps") || nestedJson.has("needsClarification")) {
                        nestedJson
                    } else {
                        json
                    }
                } catch (_: Exception) {
                    json
                }
            } else {
                json
            }

            val needsClarification = targetJson.optBoolean("needsClarification", false)
            val clarifyingQuestion = targetJson.optString("clarifyingQuestion", "")

            val stepsList = mutableListOf<String>()
            val stepsArray = targetJson.optJSONArray("steps")
            if (stepsArray != null) {
                for (i in 0 until stepsArray.length()) {
                    val step = stepsArray.optString(i)
                    if (step.isNotBlank()) {
                        stepsList.add(step.trim())
                    }
                }
            }

            TaskPlan(
                steps = stepsList,
                needsClarification = needsClarification,
                clarifyingQuestion = clarifyingQuestion
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse task plan with JSONObject, falling back to regex: ${e.message}")
            fallbackRegexParse(cleanJson)
        }
    }

    private fun extractCleanJson(rawText: String): String {
        val trimmed = rawText.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed
        }
        val codeBlockRegex = Regex("```(?:json)?\\s*(\\{.*?\\})\\s*```", RegexOption.DOT_MATCHES_ALL)
        val match = codeBlockRegex.find(trimmed)
        if (match != null) {
            return match.groupValues[1].trim()
        }
        val firstBrace = trimmed.indexOf('{')
        val lastBrace = trimmed.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1)
        }
        return trimmed
    }

    private fun fallbackRegexParse(text: String): TaskPlan {
        val needsClarificationMatch = Regex("\"needsClarification\"\\s*:\\s*(true|false)", RegexOption.IGNORE_CASE).find(text)
        val needsClarification = needsClarificationMatch?.groupValues?.get(1)?.toBooleanStrictOrNull() ?: false

        val questionMatch = Regex("\"clarifyingQuestion\"\\s*:\\s*\"((?:\\\\\"|[^\"])*)\"", RegexOption.IGNORE_CASE).find(text)
        val clarifyingQuestion = questionMatch?.groupValues?.get(1)?.replace("\\\"", "\"")?.trim() ?: ""

        val steps = mutableListOf<String>()
        val stepsArrayMatch = Regex("\"steps\"\\s*:\\s*\\[(.*?)\\]", RegexOption.DOT_MATCHES_ALL).find(text)
        if (stepsArrayMatch != null) {
            val arrayBody = stepsArrayMatch.groupValues[1]
            val stringItemRegex = Regex("\"((?:\\\\\"|[^\"])*)\"")
            stringItemRegex.findAll(arrayBody).forEach {
                val item = it.groupValues[1].replace("\\\"", "\"").trim()
                if (item.isNotBlank()) {
                    steps.add(item)
                }
            }
        }

        // If no JSON array steps were found, check for numbered plain-text steps
        if (steps.isEmpty()) {
            val lineRegex = Regex("(?:^|\\n)\\s*(?:\\d+[.)]|[-*])\\s*(.+)")
            lineRegex.findAll(text).forEach {
                val step = it.groupValues[1].trim()
                if (step.isNotBlank() && !step.startsWith("{") && !step.startsWith("\"")) {
                    steps.add(step)
                }
            }
        }

        return TaskPlan(
            steps = steps,
            needsClarification = needsClarification,
            clarifyingQuestion = clarifyingQuestion
        )
    }

    private fun buildPlanningPrompt(userCommand: String, isBengali: Boolean): String {
        val languageInstruction = if (isBengali) {
            "Explain step descriptions or questions in natural, friendly Bengali (বাংলা)."
        } else {
            "Explain step descriptions or questions in clear, concise English."
        }
        return """
            You are Mimi's Multi-Step Task Planner.
            Analyze the user's high-level command and devise an actionable step-by-step device execution plan.
            Command: "$userCommand"

            $languageInstruction

            CRITICAL RULES:
            1. If the command lacks critical information or options needed to execute safely (e.g. login method, email/phone preference, specific choice), set "needsClarification": true and formulate a friendly question in "clarifyingQuestion". In this case, "steps" can be empty.
            2. If sufficient information is provided, set "needsClarification": false and list sequential, concrete action steps in the "steps" array.
            3. Each step in "steps" should clearly describe the device interaction (e.g. 'Open Fiverr app', 'Click Sign In', 'Search for graphic design').

            You MUST respond ONLY with a raw JSON object conforming to this exact schema without any markdown wrapping:
            {
              "steps": ["Step 1 description", "Step 2 description"],
              "needsClarification": false,
              "clarifyingQuestion": ""
            }
        """.trimIndent()
    }

    private fun buildRefinedPlanningPrompt(
        originalCommand: String,
        userAnswer: String,
        isBengali: Boolean
    ): String {
        val languageInstruction = if (isBengali) {
            "Explain step descriptions or questions in natural, friendly Bengali (বাংলা)."
        } else {
            "Explain step descriptions or questions in clear, concise English."
        }
        return """
            You are Mimi's Multi-Step Task Planner.
            Original Command: "$originalCommand"
            User's Clarification/Answer: "$userAnswer"

            $languageInstruction

            With this user clarification, break the task down into sequential, actionable steps in the "steps" array.
            If further clarification is still critically needed, set "needsClarification": true and ask in "clarifyingQuestion".
            Otherwise, set "needsClarification": false.

            You MUST respond ONLY with a raw JSON object conforming to this exact schema without any markdown wrapping:
            {
              "steps": ["Step 1 description", "Step 2 description"],
              "needsClarification": false,
              "clarifyingQuestion": ""
            }
        """.trimIndent()
    }

    private fun buildStepActionPrompt(
        stepDescription: String,
        screenSummary: String,
        isBengali: Boolean
    ): String {
        val screenContext = if (screenSummary.isNotBlank()) {
            """
            CURRENT SCREEN NODES (Accessibility View Hierarchy):
            $screenSummary
            """.trimIndent()
        } else {
            "CURRENT SCREEN NODES: [Screen hierarchy not available or accessibility not connected]"
        }

        val languageInstruction = if (isBengali) {
            "Provide conversational reply in Bengali."
        } else {
            "Provide conversational reply in English."
        }

        return """
            You are Mimi's UI Automation Agent.
            The user wants to complete this task step:
            "$stepDescription"

            $screenContext

            Based on the current step description and the visible UI elements on the screen above, determine the exact single action to perform right now.
            Available action types:
            1. {"type": "OPEN_APP", "target": "package_or_app_name"}
            2. {"type": "CLICK", "text": "Exact text on button/element"} or {"type": "CLICK", "x": 100.0, "y": 200.0} or {"type": "CLICK", "targetId": "view_id"}
            3. {"type": "GLOBAL_ACTION", "action": "HOME"} (or "BACK", "RECENTS", "NOTIFICATIONS")
            4. {"type": "SCROLL", "direction": "FORWARD"} (or "BACKWARD")
            5. {"type": "OPEN_SETTINGS", "setting": "wifi/bluetooth/display/sound/battery/location/apps"}
            6. {"type": "FLASHLIGHT", "state": "on"} (or "off")
            7. {"type": "PLAY_YOUTUBE", "query": "search query"}

            $languageInstruction

            You MUST respond ONLY with a raw JSON object matching this schema without markdown code blocks:
            {
              "reply": "Brief description of the action being taken",
              "actions": [
                {
                  "type": "CLICK",
                  "text": "Target text"
                }
              ]
            }
        """.trimIndent()
    }
}
