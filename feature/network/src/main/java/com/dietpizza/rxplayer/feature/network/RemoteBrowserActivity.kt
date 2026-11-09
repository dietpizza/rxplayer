package com.dietpizza.rxplayer.feature.network

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.dietpizza.rxplayer.core.ui.RxActivity
import com.dietpizza.rxplayer.core.ui.RxIntents
import dagger.hilt.android.AndroidEntryPoint

/**
 * Remote file browser for one saved server (plan §4 #10). Folder changes happen in place (with a
 * breadcrumb bar and back going up a level); playback opens PlayerActivity.
 */
@AndroidEntryPoint
class RemoteBrowserActivity : RxActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setRxContent {
            RemoteBrowserScreen(
                onBack = ::finish,
                onOpenMedia = { playlist ->
                    val intent = if (playlist.viewer) {
                        RxIntents.viewer(this, playlist.keys, playlist.startIndex)
                    } else {
                        RxIntents.player(this, playlist.keys, playlist.startIndex)
                    }
                    startActivity(intent)
                },
            )
        }
    }

    companion object {
        /** `network_server.id` (required). Read by [RemoteBrowserViewModel]. */
        const val EXTRA_SERVER_ID = "rx.extra.SERVER_ID"

        /** Display name, so the app bar has a title on the first frame (optional). */
        const val EXTRA_SERVER_NAME = "rx.extra.SERVER_NAME"

        fun intent(context: Context, serverId: Long, serverName: String? = null): Intent =
            Intent(context, RemoteBrowserActivity::class.java)
                .putExtra(EXTRA_SERVER_ID, serverId)
                .putExtra(EXTRA_SERVER_NAME, serverName)
    }
}
