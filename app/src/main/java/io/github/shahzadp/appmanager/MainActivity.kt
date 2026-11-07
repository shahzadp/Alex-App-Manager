package io.github.shahzadp.appmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.shahzadp.appmanager.ui.AlexAppsApp
import io.github.shahzadp.appmanager.ui.AppViewModel
import io.github.shahzadp.appmanager.ui.theme.AlexAppsTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val theme by viewModel.themeMode.collectAsStateWithLifecycle()
            AlexAppsTheme(mode = theme) {
                AlexAppsApp(viewModel)
            }
        }
    }
}
