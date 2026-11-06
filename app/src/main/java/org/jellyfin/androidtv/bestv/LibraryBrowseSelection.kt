package org.jellyfin.androidtv.bestv

/** One live selection shared by the page and its popup; no pending apply/commit state. */
class LibraryBrowseSelection(initial: LibraryBrowse.Query, private val onChanged: (LibraryBrowse.Query) -> Unit) {
    var current = initial
        private set

    /** Returns whether the selection changed; listeners run before this method returns. */
    fun select(value: LibraryBrowse.Query): Boolean {
        if (value == current) return false
        current = value
        onChanged(value)
        return true
    }

    fun resetFilters() = select(current.copy(
        state = LibraryBrowse.State.ALL,
        genre = null,
        year = null,
        resolution = null,
        region = null,
    ))

    fun selectSort(value: LibraryBrowse.Sort): Boolean {
        if (value == current.sort) return false
        return select(current.copy(sort = value, ascending = value == LibraryBrowse.Sort.NAME))
    }

    fun setAscending(value: Boolean) = select(current.copy(ascending = value))
}
