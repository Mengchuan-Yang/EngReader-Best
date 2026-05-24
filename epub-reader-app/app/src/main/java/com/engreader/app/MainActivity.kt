package com.engreader.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
  private val pendingImportUri = MutableStateFlow<Uri?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.auto(
        android.graphics.Color.TRANSPARENT,
        android.graphics.Color.TRANSPARENT,
      ),
      navigationBarStyle = SystemBarStyle.auto(
        android.graphics.Color.TRANSPARENT,
        android.graphics.Color.TRANSPARENT,
      ),
    )
    handleIntent(intent)
    setContent {
      MainNavigation(pendingImportUri = pendingImportUri, onImportConsumed = { pendingImportUri.value = null })
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    handleIntent(intent)
  }

  private fun handleIntent(intent: Intent?) {
    val importUri =
      when (intent?.action) {
        Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        Intent.ACTION_VIEW -> intent.data
        else -> null
      }
    if (importUri != null) {
      pendingImportUri.value = importUri
    }
  }
}
