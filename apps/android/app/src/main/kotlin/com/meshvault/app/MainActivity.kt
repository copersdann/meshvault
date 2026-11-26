package com.meshvault.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.meshvault.app.ui.App

class MainActivity : ComponentActivity() {
    private var externalUri by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        externalUri = intent?.data?.toString()
        setContent { App(externalUri = externalUri) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        externalUri = intent.data?.toString()
    }
}
