package com.jlr.streamdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jlr.streamdemo.ui.VideoScreen
import com.jlr.streamdemo.ui.theme.JLRStreamDemoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JLRStreamDemoTheme {
                VideoScreen()
            }
        }
    }
}
