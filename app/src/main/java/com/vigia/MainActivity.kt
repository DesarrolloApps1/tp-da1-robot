package com.vigia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.vigia.ui.navigation.AppNavHost
import com.vigia.ui.theme.VigiaTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VigiaTheme {
                AppNavHost()
            }
        }
    }
}
