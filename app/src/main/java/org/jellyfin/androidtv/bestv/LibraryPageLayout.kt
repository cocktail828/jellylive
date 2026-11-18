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
    val HOME_RESUME_ROW = Box(50f, 132f, 862f, 170f)
    val HOME_EXPLORE_LABEL = Box(48f, 329f, 864f, 22f)
    const val HOME_CARD_ARTWORK_HEIGHT = 130f
    const val HOME_CARD_INFO_TOP = 80f
    const val HOME_CARD_IMAGE_HEIGHT = 80f
    val HOME_CARD_TITLE = Box(10f, 87f, 256f, 22f)
    val HOME_CARD_SUBTITLE = Box(10f, 110f, 256f, 16f)
    val HOME_CARD_PROGRESS = Box(8f, 124f, 260f, 3f)

    fun homeExploreLabel(hasResume: Boolean) =
        HOME_EXPLORE_LABEL

    fun homeEntry(index: Int, hasResume: Boolean=true): Box {
        require(index in Entry.entries.indices)
        return Box(50f + index * 292f, 361f, 276f, 130f)
    }

    val GRID_SEARCH = Box(444f, 68f, 148f, 38f)
    val GRID_FILTER = Box(604f, 68f, 148f, 38f)
    val GRID_SORT = Box(764f, 68f, 148f, 38f)
    fun toolbar(box: Box) = box

    fun columns() = 6

    fun poster(index: Int): Box {
        require(index in 0 until columns())
        // Six posters fit the fixed TV layout and the 5% safe area.
        return Box(50f + index * 146f, 135f, 130f, 222f)
    }

    fun scrollViewport(): Box {
        val top=poster(0).y-4f
        return Box(48f,top,864f,513f-top)
    }
    fun scrollPoster(index: Int): Box {
        val base=poster(index % columns())
        return base.copy(x=base.x-48f,y=4f+(index / columns())*(base.height+16f))
    }
    fun appendLimit(shown: Int,total: Int,focused: Int): Int {
        val columns=columns()
        return when {
            shown==0 -> minOf(total,columns*3)
            focused>=shown-columns*2 -> minOf(total,shown+columns*2)
            else -> shown
        }
    }

    fun posterImageHeight() = poster(0).width * 1.5f
    fun posterTitle() =
        Box(9f, posterImageHeight() - (32f), poster(0).width - 18f, 32f)
    fun posterMetadata() =
        Box(0f, posterImageHeight() + 4f, poster(0).width, 22f)
}
