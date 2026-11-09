package com.dietpizza.rxplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dietpizza.rxplayer.core.designsystem.theme.RxTheme
import com.dietpizza.rxplayer.feature.gallery.GalleryScreen
import dagger.hilt.android.AndroidEntryPoint

/** STUB — replaced by the shell agent. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { RxTheme { GalleryScreen(onOpenSettings = {}) } }
    }
}
