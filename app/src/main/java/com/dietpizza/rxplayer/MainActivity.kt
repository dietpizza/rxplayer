package com.dietpizza.rxplayer

import android.os.Bundle
import com.dietpizza.rxplayer.core.ui.RxActivity
import com.dietpizza.rxplayer.feature.settings.SettingsActivity
import com.dietpizza.rxplayer.ui.RxApp
import dagger.hilt.android.AndroidEntryPoint

/** Launcher activity: permission gate + the Gallery / Videos / Network tabs. */
@AndroidEntryPoint
class MainActivity : RxActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setRxContent {
            RxApp(onOpenSettings = { startActivity(SettingsActivity.intent(this)) })
        }
    }
}
