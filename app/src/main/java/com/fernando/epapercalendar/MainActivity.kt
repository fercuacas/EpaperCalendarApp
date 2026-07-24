package com.fernando.epapercalendar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fernando.epapercalendar.ui.EpaperCalendarApp
import com.fernando.epapercalendar.ui.theme.EpaperCalendarTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EpaperCalendarTheme {
                EpaperCalendarApp()
            }
        }
    }
}
