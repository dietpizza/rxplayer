package com.dietpizza.rxplayer.feature.search

import android.os.Bundle
import com.dietpizza.rxplayer.core.ui.RxActivity
import com.dietpizza.rxplayer.core.ui.RxIntents
import dagger.hilt.android.AndroidEntryPoint

/**
 * Search (plan §4 #11): filename + folder-name search with filter chips, over the library of the
 * tab that opened it ([RxIntents.search]'s scope). Results open where that tab opens them:
 * Gallery in the viewer and its albums, Videos in the player and its folder screen.
 */
@AndroidEntryPoint
class SearchActivity : RxActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scope = SearchScope.of(intent.getStringExtra(RxIntents.EXTRA_SEARCH_SCOPE))
        setRxContent {
            SearchScreen(
                scope = scope,
                onBack = ::finish,
                onOpen = { keys, index ->
                    val intent = when (scope) {
                        SearchScope.GALLERY -> RxIntents.viewer(this, keys, index)
                        SearchScope.VIDEOS -> RxIntents.player(this, keys, index)
                    }
                    startActivity(intent)
                },
                onOpenFolder = { folder ->
                    val intent = when (scope) {
                        SearchScope.GALLERY -> RxIntents.galleryAlbum(this, folder.path, folder.name)
                        SearchScope.VIDEOS -> RxIntents.videoFolder(this, folder.path, folder.name)
                    }
                    startActivity(intent)
                },
            )
        }
    }
}
