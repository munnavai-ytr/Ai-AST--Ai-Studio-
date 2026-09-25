package com.example.service

import android.accessibilityservice.AccessibilityService
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import com.example.R

/**
 * AI Tap Indicator:
 * Shows a temporary animated glowing circular indicator on the screen at the
 * exact coordinate where an action/tap is dispatched by the Accessibility Service.
 * Provides transparency so the user can clearly see where AI is interacting with the screen.
 */
object TapIndicatorOverlay {

    private const val TAG = "TapIndicatorOverlay"
    private const val INDICATOR_SIZE_DP = 60
    private const val ANIMATION_DURATION_MS = 250L

    /**
     * Places the tap indicator view at the specified (x, y) coordinates,
     * triggers a 250ms scale-up and fade-out animation, and removes the view upon completion.
     */
    fun showTapAt(context: Context, x: Float, y: Float) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            showTapAtInternal(context, x, y)
        } else {
            Handler(Looper.getMainLooper()).post {
                showTapAtInternal(context, x, y)
            }
        }
    }

    private fun showTapAtInternal(context: Context, x: Float, y: Float) {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (windowManager == null) {
            Log.w(TAG, "WindowManager is unavailable, cannot display tap indicator")
            return
        }

        val density = context.resources.displayMetrics.density
        val sizePx = (INDICATOR_SIZE_DP * density).toInt()

        // Select overlay window type:
        // When invoked from an AccessibilityService, TYPE_ACCESSIBILITY_OVERLAY is the native type.
        // Otherwise fallback to TYPE_APPLICATION_OVERLAY on API 26+.
        val overlayType = if (context is AccessibilityService) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams().apply {
            width = sizePx
            height = sizePx
            type = overlayType
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            gravity = Gravity.TOP or Gravity.START
            this.x = (x - sizePx / 2f).toInt()
            this.y = (y - sizePx / 2f).toInt()
        }

        val indicatorView = ImageView(context).apply {
            setImageResource(R.drawable.ic_tap_indicator)
            scaleType = ImageView.ScaleType.FIT_CENTER
            scaleX = 0.35f
            scaleY = 0.35f
            alpha = 1.0f
        }

        try {
            windowManager.addView(indicatorView, params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach tap indicator overlay at ($x, $y)", e)
            return
        }

        val scaleXAnim = ObjectAnimator.ofFloat(indicatorView, "scaleX", 0.35f, 1.25f)
        val scaleYAnim = ObjectAnimator.ofFloat(indicatorView, "scaleY", 0.35f, 1.25f)
        val fadeAnim = ObjectAnimator.ofFloat(indicatorView, "alpha", 1.0f, 0.0f)

        val animatorSet = AnimatorSet().apply {
            playTogether(scaleXAnim, scaleYAnim, fadeAnim)
            duration = ANIMATION_DURATION_MS
            interpolator = DecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    try {
                        windowManager.removeView(indicatorView)
                    } catch (e: Exception) {
                        Log.w(TAG, "Error removing tap indicator view", e)
                    }
                }
            })
        }

        animatorSet.start()
    }
}
