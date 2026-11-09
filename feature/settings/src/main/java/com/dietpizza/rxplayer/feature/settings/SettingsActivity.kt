package com.dietpizza.rxplayer.feature.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.dietpizza.rxplayer.core.ui.RxActivity
import dagger.hilt.android.AndroidEntryPoint

/** Settings is its own Activity so opening/closing uses the system activity transition + predictive back. */
@AndroidEntryPoint
class SettingsActivity : RxActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setRxContent { SettingsScreen(onBack = ::finish) }
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, SettingsActivity::class.java)
    }
}
