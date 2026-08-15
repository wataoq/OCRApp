package com.local.bookocr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.local.bookocr.ui.navigation.BookOcrNavHost
import com.local.bookocr.ui.theme.BookOcrTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BookOcrTheme {
                BookOcrNavHost()
            }
        }
    }
}
