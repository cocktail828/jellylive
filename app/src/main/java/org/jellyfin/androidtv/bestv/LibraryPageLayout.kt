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

        fun query() = LibraryBrowse.Query(kind=kind, sort=if(this==RECENT) LibraryBrowse.Sort.ADDED else LibraryBrowse.Sort.NAME, ascending=this!=RECENT)
    }

    val HOME_SEARCH = Box(754f, 69f, 158f, 38f)
    val HOME_RESUME_LABEL = Box(48f, 101f, 864f, 22f)
    val HOME_RESUME_ROW = Box(50f, 132f, 862f, 130f)
    val HOME_EXPLORE_LABEL = Box(48f, 289f, 864f, 22f)
    const val HOME_CARD_ARTWORK_HEIGHT = 130f
    const val HOME_CARD_INFO_TOP = 80f
    const val HOME_CARD_IMAGE_HEIGHT = 80f
    val HOME_CARD_TITLE = Box(10f, 87f, 256f, 22f)
    val HOME_CARD_SUBTITLE = Box(10f, 110f, 256f, 16f)
    val HOME_CARD_PROGRESS = Box(8f, 127f, 260f, 2f)

    fun homeExploreLabel(hasResume: Boolean) =
        HOME_EXPLORE_LABEL

    fun homeEntry(index: Int, hasResume: Boolean=true): Box {
        require(index in Entry.entries.indices)
        return Box(50f + index * 292f, 321f, 276f, 130f)
    }

    val GRID_SEARCH = Box(444f, 68f, 148f, 38f)
    val GRID_FILTER = Box(604f, 68f, 148f, 38f)
    val GRID_SORT = Box(764f, 68f, 148f, 38f)
    val GRID_PREVIOUS = Box(372f, 424f, 36f, 36f)
    val GRID_RANGE = Box(420f, 424f, 120f, 36f)
    val GRID_NEXT = Box(552f, 424f, 36f, 36f)

    fun pagerPrevious(large: Boolean=false) = if (large) GRID_PREVIOUS.copy(y=450f) else GRID_PREVIOUS
    fun pagerRange(large: Boolean=false) = if (large) GRID_RANGE.copy(y=450f) else GRID_RANGE
    fun pagerNext(large: Boolean=false) = if (large) GRID_NEXT.copy(y=450f) else GRID_NEXT

    fun toolbar(box: Box, large: Boolean) = if(large) box.copy(y=75f,height=50f) else box

    fun columns(large: Boolean) = if (large) 5 else 6

    fun poster(index: Int, large: Boolean=false): Box {
        require(index in 0 until columns(large))
        // Five larger posters or six normal posters fit the approved 5% TV safe area.
        return if (large) Box(50f + index * 175f, 143f, 159f, 272f)
            else Box(50f + index * 146f, 135f, 130f, 222f)
    }

    fun posterImageHeight(large: Boolean=false) = poster(0, large).width * 1.5f
    fun posterTitle(large: Boolean=false) =
        Box(9f, posterImageHeight(large) - (if(large) 48f else 32f), poster(0, large).width - 18f, if(large) 48f else 32f)
    fun posterMetadata(large: Boolean=false) =
        Box(0f, posterImageHeight(large) + 4f, poster(0, large).width, if(large) 28f else 22f)
}
