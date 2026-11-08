package org.jellyfin.androidtv.bestv

import org.jellyfin.sdk.model.api.BaseItemKind

/** Production geometry for gallery 15 (entry cards) and 16 (a category's poster page).
 * Coordinates are 960 x 540 logical pixels, half of the supplied drawings.
 * This has no Android dependencies so layout bounds can also be verified on the JVM.
 */
object LibraryPageLayout {
    data class Box(val x: Float, val y: Float, val width: Float, val height: Float) {
        val right get() = x + width
        val bottom get() = y + height
    }
    enum class Entry(val title: String, val kind: BaseItemKind?, val artwork: String) {
        MOVIES("电影", BaseItemKind.MOVIE, "library_entry_movies"),
        SERIES("剧集", BaseItemKind.SERIES, "library_entry_series"),
        RECENT("最近添加", null, "library_entry_recent");

        fun query() = LibraryBrowse.Query(kind=kind, sort=LibraryBrowse.Sort.NAME, ascending=true)
    }

    val HOME_SEARCH = Box(770f, 69f, 158f, 38f)
    val HOME_RESUME_LABEL = Box(32f, 101f, 896f, 22f)
    val HOME_RESUME_ROW = Box(32f, 132f, 896f, 130f)
    val HOME_EXPLORE_LABEL = Box(32f, 289f, 896f, 22f)
    const val HOME_CARD_ARTWORK_HEIGHT = 130f
    const val HOME_CARD_INFO_TOP = 80f
    const val HOME_CARD_IMAGE_HEIGHT = 80f
    val HOME_CARD_TITLE = Box(10f, 87f, 267f, 22f)
    val HOME_CARD_SUBTITLE = Box(10f, 110f, 267f, 16f)
    val HOME_CARD_PROGRESS = Box(8f, 127f, 271f, 2f)

    fun homeExploreLabel(hasResume: Boolean) =
        HOME_EXPLORE_LABEL

    fun homeEntry(index: Int, hasResume: Boolean=true): Box {
        require(index in Entry.entries.indices)
        return Box(32f + index * 301f, 321f, 287f, 130f)
    }

    val GRID_SEARCH = Box(460f, 68f, 148f, 38f)
    val GRID_FILTER = Box(620f, 68f, 148f, 38f)
    val GRID_SORT = Box(780f, 68f, 148f, 38f)
    val GRID_PREVIOUS = Box(372f, 424f, 36f, 36f)
    val GRID_RANGE = Box(420f, 424f, 120f, 36f)
    val GRID_NEXT = Box(552f, 424f, 36f, 36f)

    fun pagerPrevious(large: Boolean=false) = if (large) GRID_PREVIOUS.copy(y=434f) else GRID_PREVIOUS
    fun pagerRange(large: Boolean=false) = if (large) GRID_RANGE.copy(y=434f) else GRID_RANGE
    fun pagerNext(large: Boolean=false) = if (large) GRID_NEXT.copy(y=434f) else GRID_NEXT

    fun columns(large: Boolean) = if (large) 5 else 6

    fun poster(index: Int, large: Boolean=false): Box {
        require(index in 0 until columns(large))
        // Larger captions need their own room. Five 160px cards with 24px gaps
        // fill the same 896px content width and retain a strict 2:3 image.
        return if (large) Box(32f + index * 184f, 112f, 160f, 312f)
            else Box(32f + index * 151f, 135f, 137f, 270f)
    }

    fun posterImageHeight(large: Boolean=false) = poster(0, large).width * 1.5f
    fun posterTitle(large: Boolean=false) =
        Box(6f, posterImageHeight(large) - 32f, poster(0, large).width - 12f, 28f)
    fun posterMetadata(large: Boolean=false) =
        Box(8f, posterImageHeight(large) + 7f, poster(0, large).width - 16f, if (large) 20f else 18f)
}
