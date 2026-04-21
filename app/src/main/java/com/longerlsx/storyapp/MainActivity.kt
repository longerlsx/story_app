package com.longerlsx.storyapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.longerlsx.storyapp.app.StoryApp
import com.longerlsx.storyapp.ui.theme.StoryAppTheme

class MainActivity : ComponentActivity() {
    private var pendingExternalIntent by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingExternalIntent = intent
        setContent {
            StoryAppTheme {
                StoryApp(
                    externalIntent = pendingExternalIntent,
                    onExternalIntentConsumed = {
                        pendingExternalIntent = null
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingExternalIntent = intent
    }
}
