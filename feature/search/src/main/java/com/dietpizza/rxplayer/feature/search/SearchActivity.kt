package com.dietpizza.rxplayer.feature.search

import android.os.Bundle
import com.dietpizza.rxplayer.core.ui.RxActivity
import com.dietpizza.rxplayer.core.ui.RxIntents
import dagger.hilt.android.AndroidEntryPoint

/**
 * Search (plan §4 #11): filename + folder-name search over every local photo and video, with
 * filter chips. Opened with [RxIntents.search] from the Gallery and Videos tabs.
 */
@AndroidEntryPoint
class SearchActivity : RxActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setRxContent {
            SearchScreen(
                onBack = ::finish,
                onOpen = { keys, index, allVideos ->
                    val intent = if (allVideos) {
                        RxIntents.player(this, keys, index)
                    } else {
                        RxIntents.viewer(this, keys, index)
                    }
                    startActivity(intent)
                },
            )
        }
    }
}
