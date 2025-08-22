package com.footstique.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.footstique.player.core.ui.theme.FootstiquePlayerTheme
import com.footstique.player.ui.urlinput.UrlInputScreen

// تم حذف @AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FootstiquePlayerTheme {
                UrlInputScreen()
            }
        }
    }
}