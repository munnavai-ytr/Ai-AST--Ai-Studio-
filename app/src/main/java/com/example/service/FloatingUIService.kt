package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.gemini.GeminiApiClient
import com.example.tts.AndroidTtsManager
import com.example.voice.SpeechRecognitionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Floating UI Service that draws a system alert window overlay over other apps.
 * Features:
 * 1. Dark-themed floating container with draggable header.
 * 2. Dynamic glowing microphone indicator with pulsing animation.
 * 3. Speech recognition listening loop specifically for the floating dialog.
 * 4. Text input field (EditText) with "Send" button for silent text typing.
 * 5. Gemini API assistant integration with human-like friend persona.
 * 6. AccessibilityService command execution.
 */
class FloatingUIService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var ttsManager: AndroidTtsManager? = null

    private var micPulseView: View? = null
    private var micIconView: ImageView? = null
    private var statusTextView: TextView? = null
    private var responseTextView: TextView? = null
    private var inputEditText: EditText? = null
    private var sendButton: ImageButton? = null
    private var micButton: FrameLayout? = null
    private var closeButton: ImageButton? = null

    private var isListening = false
    private var pulseAnim: ScaleAnimation? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ttsManager = AndroidTtsManager(applicationContext)
        showFloatingWindow()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (floatingView == null) {
            showFloatingWindow()
        } else {
            // Automatically start listening whenever overlay is requested
            startListening()
        }
        return START_NOT_STICKY
    }

    private fun showFloatingWindow() {
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Cannot draw overlay: SYSTEM_ALERT_WINDOW permission missing")
            stopSelf()
            return
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(this)
        floatingView = inflater.inflate(R.layout.layout_floating_assistant, null)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            x = 0
            y = 40
        }

        setupViews(floatingView!!, params)

        try {
            windowManager?.addView(floatingView, params)
            startListening()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add floating window: ${e.message}", e)
            stopSelf()
        }
    }

    private fun setupViews(view: View, params: WindowManager.LayoutParams) {
        micPulseView = view.findViewById(R.id.view_mic_pulse)
        micIconView = view.findViewById(R.id.img_mic_icon)
        statusTextView = view.findViewById(R.id.txt_floating_status)
        responseTextView = view.findViewById(R.id.txt_floating_response)
        inputEditText = view.findViewById(R.id.edt_floating_input)
        sendButton = view.findViewById(R.id.btn_floating_send)
        micButton = view.findViewById(R.id.layout_mic_button)
        closeButton = view.findViewById(R.id.btn_floating_close)

        closeButton?.setOnClickListener {
            stopListening()
            stopSelf()
        }

        micButton?.setOnClickListener {
            if (isListening) {
                stopListening()
            } else {
                startListening()
            }
        }

        sendButton?.setOnClickListener {
            val text = inputEditText?.text?.toString()?.trim() ?: ""
            if (text.isNotBlank()) {
                inputEditText?.setText("")
                processUserPrompt(text)
            }
        }

        // Draggable touch handling on header
        val header = view.findViewById<View>(R.id.layout_floating_header)
        header?.setOnTouchListener(object : View.OnTouchListener {
            private var initialY = 0
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                if (event == null) return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialY = params.y
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dy = (initialTouchY - event.rawY).toInt()
                        params.y = (initialY + dy).coerceAtLeast(0)
                        try {
                            windowManager?.updateViewLayout(floatingView, params)
                        } catch (_: Exception) {}
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun startListening() {
        if (isListening) return

        // 1. Yield microphone from background wake-word listener
        SpeechRecognitionManager.requestManualRecognitionStart()

        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(createListener())
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            }

            speechRecognizer?.startListening(intent)
            isListening = true
            updateListeningUI(true)
            statusTextView?.text = "Listening... Speak to Mimi"
        } catch (e: Exception) {
            Log.e(TAG, "Error starting floating speech recognizer: ${e.message}")
            stopListening()
        }
    }

    private fun stopListening() {
        isListening = false
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (_: Exception) {}

        SpeechRecognitionManager.onManualRecognitionFinished()
        updateListeningUI(false)
    }

    private fun updateListeningUI(listening: Boolean) {
        val cyan = ContextCompat.getColor(this, R.color.neon_cyan)
        val muted = ContextCompat.getColor(this, R.color.text_secondary)
        if (listening) {
            micIconView?.setColorFilter(cyan)
            startPulseAnimation()
        } else {
            micIconView?.setColorFilter(muted)
            stopPulseAnimation()
        }
    }

    private fun startPulseAnimation() {
        if (pulseAnim != null) return
        pulseAnim = ScaleAnimation(
            1f, 1.45f, 1f, 1.45f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 800
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        }
        micPulseView?.visibility = View.VISIBLE
        micPulseView?.startAnimation(pulseAnim)
    }

    private fun stopPulseAnimation() {
        pulseAnim?.cancel()
        pulseAnim = null
        micPulseView?.clearAnimation()
        micPulseView?.visibility = View.INVISIBLE
    }

    private fun createListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) {}
            override fun onBeginningOfSpeech() {
                statusTextView?.text = "Mimi is listening..."
            }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                statusTextView?.text = "Thinking..."
            }

            override fun onError(error: Int) {
                if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                    mainHandler.postDelayed({
                        if (floatingView != null) {
                            startListening()
                        }
                    }, 1000)
                    return
                }
                stopListening()
                statusTextView?.text = "Tap mic or type a command"
            }

            override fun onResults(results: android.os.Bundle?) {
                stopListening()
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: ""
                if (text.isNotBlank()) {
                    processUserPrompt(text)
                }
            }

            override fun onPartialResults(partialResults: android.os.Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partial = matches?.firstOrNull() ?: ""
                if (partial.isNotBlank()) {
                    statusTextView?.text = partial
                }
            }

            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        }
    }

    private fun processUserPrompt(prompt: String) {
        statusTextView?.text = "\"$prompt\""
        responseTextView?.visibility = View.VISIBLE
        responseTextView?.text = "Mimi is processing..."

        serviceScope.launch(Dispatchers.IO) {
            val result = GeminiApiClient.askAssistant(prompt, isBengali = true)
            result.onSuccess { rawResponse ->
                val mimiResponse = AssistantActionManager.parseMimiResponse(rawResponse)

                withContext(Dispatchers.Main) {
                    val replyToDisplay = mimiResponse.reply.ifBlank { "ঠিক আছে, কাজটা করছি।" }
                    responseTextView?.text = replyToDisplay

                    // Mimi speaks the reply naturally using TextToSpeech.QUEUE_FLUSH
                    if (replyToDisplay.isNotBlank()) {
                        ttsManager?.speak(replyToDisplay, isBengali = true)
                    }
                }

                // Execute all actions dynamically and simultaneously via AccessibilityService or Intent
                var actionStatus = ""
                for (action in mimiResponse.actions) {
                    val execResult = AssistantActionManager.executeCommand(applicationContext, action)
                    actionStatus += "${execResult.message} "
                }

                withContext(Dispatchers.Main) {
                    if (mimiResponse.reply.isBlank() && actionStatus.isNotBlank()) {
                        responseTextView?.text = actionStatus
                    }

                    // Auto dismiss after 4 seconds if action executed
                    if (mimiResponse.actions.isNotEmpty()) {
                        mainHandler.postDelayed({
                            stopSelf()
                        }, 4000)
                    }
                }
            }.onFailure { err ->
                withContext(Dispatchers.Main) {
                    responseTextView?.text = "Sorry: ${err.localizedMessage ?: "Could not process"}"
                }
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        stopListening()
        ttsManager?.shutdown()
        ttsManager = null
        if (floatingView != null) {
            try {
                windowManager?.removeView(floatingView)
            } catch (_: Exception) {}
            floatingView = null
        }
        super.onDestroy()
    }

    companion object {
        const val TAG = "FloatingUIService"

        fun start(context: Context) {
            if (Settings.canDrawOverlays(context)) {
                val intent = Intent(context, FloatingUIService::class.java)
                context.startService(intent)
            } else {
                Log.w(TAG, "Overlay permission not granted yet")
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingUIService::class.java)
            context.stopService(intent)
        }
    }
}
