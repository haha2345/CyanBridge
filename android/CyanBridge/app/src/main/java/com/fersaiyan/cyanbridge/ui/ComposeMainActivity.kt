package com.fersaiyan.cyanbridge.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fersaiyan.cyanbridge.ui.navigation.AppNavigation
import com.fersaiyan.cyanbridge.ui.theme.CyanBridgeTheme

/**
 * New Compose-based main activity that replaces the legacy XML-based MainActivity
 * for the competition demo. The legacy MainActivity is kept for backward compatibility
 * with existing BLE/WiFi/AI features.
 */
class ComposeMainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CyanBridgeTheme {
                AppNavigation()
            }
        }
    }
}
