package com.dietpizza.rxplayer.feature.network

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.dietpizza.rxplayer.core.ui.RxActivity
import dagger.hilt.android.AndroidEntryPoint

/** Add or edit a saved server (plan §4 #9). */
@AndroidEntryPoint
class ServerActivity : RxActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setRxContent { ServerScreen(onBack = ::finish) }
    }

    companion object {
        /** `network_server.id` to edit; absent = add a new server. Read by [ServerViewModel]. */
        const val EXTRA_SERVER_ID = "rx.extra.SERVER_ID"

        fun intent(context: Context, serverId: Long? = null): Intent =
            Intent(context, ServerActivity::class.java).apply {
                if (serverId != null) putExtra(EXTRA_SERVER_ID, serverId)
            }
    }
}
