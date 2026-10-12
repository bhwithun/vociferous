package com.brian.dialbuddy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.brian.dialbuddy.telecom.Calls
import com.brian.dialbuddy.ui.screens.InCallScreen
import com.brian.dialbuddy.ui.theme.DialBuddyTheme

class InCallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by Calls.state.collectAsState()
            LaunchedEffect(state.calls.isEmpty()) {
                if (state.calls.isEmpty()) finish()
            }
            DialBuddyTheme {
                InCallScreen(state)
            }
        }
    }
}
