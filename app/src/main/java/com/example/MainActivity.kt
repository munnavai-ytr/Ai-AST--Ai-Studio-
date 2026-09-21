package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.service.WakeWordService
import com.example.service.WakeWordStateManager
import com.example.ui.VoiceToTextScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    handleWakeIntent(intent)
    setContent {
      MyApplicationTheme {
        VoiceToTextScreen()
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleWakeIntent(intent)
  }

  private fun handleWakeIntent(intent: Intent?) {
    if (intent?.getBooleanExtra(WakeWordService.EXTRA_WAKE_TRIGGERED, false) == true) {
      WakeWordStateManager.notifyWakeWordDetected(null)
    }
  }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
  Text(text = "Hello $name!", modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun VoiceToTextPreview() {
  MyApplicationTheme {
    VoiceToTextScreen()
  }
}
