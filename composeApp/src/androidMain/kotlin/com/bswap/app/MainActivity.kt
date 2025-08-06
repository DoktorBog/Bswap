package com.bswap.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.bswap.app.BswapApp
import com.bswap.navigation.rememberBackStack
import com.bswap.navigation.pop

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize app context for platform-specific operations
        initializeAppContext(applicationContext)
        
        setContent {
            val backStack = rememberBackStack()
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (backStack.size > 1) backStack.pop() else finish()
                }
            })
            BswapApp(backStack)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    BswapApp()
}