package com.engreader.app

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.engreader.app.ui.main.MainScreen
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun MainNavigation(pendingImportUri: MutableStateFlow<Uri?>, onImportConsumed: () -> Unit) =
  MainScreen(
    pendingImportUri = pendingImportUri,
    onImportConsumed = onImportConsumed,
    modifier = Modifier,
  )
