package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.ui.navigation.MoodMuseNavHost
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.viewmodel.MoodMuseViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val vm: MoodMuseViewModel = viewModel()
                MoodMuseNavHost(vm = vm)
            }
        }
    }
}