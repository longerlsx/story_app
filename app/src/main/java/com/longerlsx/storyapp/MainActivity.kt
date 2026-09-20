package com.longerlsx.storyapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.os.BundleCompat
import com.longerlsx.storyapp.app.StoryApp
import com.longerlsx.storyapp.ui.theme.StoryAppTheme

class MainActivity : ComponentActivity() {
    private var pendingExternalIntent by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingExternalIntent = if (savedInstanceState == null) {
            intent
        } else {
            BundleCompat.getParcelable(savedInstanceState, PENDING_EXTERNAL_INTENT, Intent::class.java)
        }
        setContent {
            val externalIntent = pendingExternalIntent
            StoryAppTheme {
                StoryApp(
                    externalIntent = externalIntent,
                    onExternalIntentConsumed = {
                        if (pendingExternalIntent === externalIntent) {
                            pendingExternalIntent = null
                        }
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingExternalIntent = intent
    }

    override fun onSaveInstanceState(outState: Bundle) {
        // A null pending command means it was already consumed; the launch Intent
        // must not replay it when Android recreates this Activity.
        outState.putParcelable(PENDING_EXTERNAL_INTENT, pendingExternalIntent)
        super.onSaveInstanceState(outState)
    }

    private companion object {
        const val PENDING_EXTERNAL_INTENT = "pendingExternalIntent"
    }
}
