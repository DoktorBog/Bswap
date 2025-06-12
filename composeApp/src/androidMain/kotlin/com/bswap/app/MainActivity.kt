package com.bswap.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.bswap.app.ComposeApp
import com.bswap.navigation.rememberBackStack
import com.bswap.navigation.pop

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val backStack = rememberBackStack()
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (backStack.size > 1) backStack.pop() else finish()
                }
            })
            ComposeApp(backStack)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    ComposeApp()
}