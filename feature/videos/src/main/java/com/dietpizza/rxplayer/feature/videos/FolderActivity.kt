package com.dietpizza.rxplayer.feature.videos

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.dietpizza.rxplayer.core.ui.RxActivity
import com.dietpizza.rxplayer.core.ui.RxIntents
import dagger.hilt.android.AndroidEntryPoint

/** The videos in one folder (plan §4 #7). Its own Activity, so open/close use the system transition. */
@AndroidEntryPoint
class FolderActivity : RxActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setRxContent {
            FolderScreen(
                onBack = ::finish,
                onPlay = { playlist, index -> startActivity(RxIntents.player(this, playlist, index)) },
            )
        }
    }

    companion object {
        /** Absolute folder path (required). Read by [FolderViewModel] through SavedStateHandle. */
        const val EXTRA_FOLDER_PATH = "rx.extra.FOLDER_PATH"

        /** Display name, so the app bar has a title on the first frame (optional). */
        const val EXTRA_FOLDER_NAME = "rx.extra.FOLDER_NAME"

        fun intent(context: Context, folderPath: String, folderName: String? = null): Intent =
            Intent(context, FolderActivity::class.java)
                .putExtra(EXTRA_FOLDER_PATH, folderPath)
                .putExtra(EXTRA_FOLDER_NAME, folderName)
    }
}
