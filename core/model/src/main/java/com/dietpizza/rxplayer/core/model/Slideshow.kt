package com.dietpizza.rxplayer.core.model

import kotlin.random.Random

/** What a slideshow item is, for the sheet's **Include** toggles. */
enum class SlideshowMediaType {
    /** Any image that isn't a GIF. */
    PHOTO,
    VIDEO,

    /** `image/gif`. */
    GIF,
    ;

    companion object {
        /** [isVideo] wins; then `image/gif` is a GIF; everything else is a photo. */
        fun of(mimeType: String?, isVideo: Boolean): SlideshowMediaType = when {
            isVideo -> VIDEO
            mimeType.equals(GIF_MIME, ignoreCase = true) -> GIF
            else -> PHOTO
        }

        private const val GIF_MIME = "image/gif"
    }
}

/** The slideshow type of a gallery item. */
val GalleryItem.slideshowType: SlideshowMediaType get() = SlideshowMediaType.of(mimeType, isVideo)

/**
 * Options picked in the slideshow sheet before starting; the last ones used are saved.
 * At least one of [includePhotos]/[includeVideos]/[includeGifs] is on ([withType] keeps it so).
 */
data class SlideshowOptions(
    val includePhotos: Boolean = true,
    val includeVideos: Boolean = true,
    val includeGifs: Boolean = true,
    /** Random order (the start item, if any, stays first). */
    val shuffle: Boolean = false,
    /** Start over after the last item; off closes the slideshow when it's done. */
    val loop: Boolean = true,
    /** How long each photo / GIF stays up. Videos play to their end. One of [SECONDS_CHOICES]. */
    val secondsPerPhoto: Int = DEFAULT_SECONDS,
) {
    val includesAnything: Boolean get() = includePhotos || includeVideos || includeGifs

    fun includes(type: SlideshowMediaType): Boolean = when (type) {
        SlideshowMediaType.PHOTO -> includePhotos
        SlideshowMediaType.VIDEO -> includeVideos
        SlideshowMediaType.GIF -> includeGifs
    }

    fun includes(mimeType: String?, isVideo: Boolean): Boolean = includes(SlideshowMediaType.of(mimeType, isVideo))

    /** Turns one **Include** toggle on or off; turning off the last one left on is ignored. */
    fun withType(type: SlideshowMediaType, included: Boolean): SlideshowOptions {
        val next = when (type) {
            SlideshowMediaType.PHOTO -> copy(includePhotos = included)
            SlideshowMediaType.VIDEO -> copy(includeVideos = included)
            SlideshowMediaType.GIF -> copy(includeGifs = included)
        }
        return if (next.includesAnything) next else this
    }

    /** Fixes values read from storage or an intent: unknown seconds, nothing included. */
    fun sanitized(): SlideshowOptions {
        val seconds = if (secondsPerPhoto in SECONDS_CHOICES) secondsPerPhoto else DEFAULT_SECONDS
        val fixed = if (seconds == secondsPerPhoto) this else copy(secondsPerPhoto = seconds)
        return if (fixed.includesAnything) fixed else fixed.copy(includePhotos = true, includeVideos = true, includeGifs = true)
    }

    companion object {
        const val DEFAULT_SECONDS = 5
        val SECONDS_CHOICES: List<Int> = listOf(3, 5, 10, 15)
        val DEFAULT = SlideshowOptions()
    }
}

/** How many items of each type a slideshow source has (the sheet's counts and Start check). */
data class SlideshowCounts(val photos: Int = 0, val videos: Int = 0, val gifs: Int = 0) {
    fun of(type: SlideshowMediaType): Int = when (type) {
        SlideshowMediaType.PHOTO -> photos
        SlideshowMediaType.VIDEO -> videos
        SlideshowMediaType.GIF -> gifs
    }

    /** Items [options] would show. */
    fun matching(options: SlideshowOptions): Int =
        (if (options.includePhotos) photos else 0) +
            (if (options.includeVideos) videos else 0) +
            (if (options.includeGifs) gifs else 0)

    companion object {
        val EMPTY = SlideshowCounts()

        fun <T> of(items: Iterable<T>, typeOf: (T) -> SlideshowMediaType): SlideshowCounts {
            var photos = 0
            var videos = 0
            var gifs = 0
            for (item in items) {
                when (typeOf(item)) {
                    SlideshowMediaType.PHOTO -> photos++
                    SlideshowMediaType.VIDEO -> videos++
                    SlideshowMediaType.GIF -> gifs++
                }
            }
            return SlideshowCounts(photos, videos, gifs)
        }
    }
}

/** The items [options] includes, in their original order. */
fun <T> List<T>.filterForSlideshow(options: SlideshowOptions, typeOf: (T) -> SlideshowMediaType): List<T> =
    filter { options.includes(typeOf(it)) }

/**
 * Play order for a slideshow over [items] (already filtered). Returns the list and the index
 * to start at. Without shuffle the order is kept and [startIndex] (clamped; negative = 0) is
 * the start. With shuffle the list is shuffled and the start is index 0: the item at
 * [startIndex] goes first when [startIndex] is in range, else any item may.
 */
fun <T> slideshowOrder(
    items: List<T>,
    startIndex: Int,
    shuffle: Boolean,
    random: Random = Random.Default,
): Pair<List<T>, Int> {
    if (items.isEmpty()) return emptyList<T>() to 0
    if (!shuffle) return items to startIndex.coerceIn(0, items.lastIndex)
    if (startIndex !in items.indices) return items.shuffled(random) to 0
    val rest = ArrayList<T>(items.size)
    for (i in items.indices) if (i != startIndex) rest += items[i]
    rest.shuffle(random)
    return (listOf(items[startIndex]) + rest) to 0
}
