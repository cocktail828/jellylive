package org.jellyfin.androidtv.bestv

import org.jellyfin.sdk.model.api.BaseItemKind

fun main() {
    var checks = 0
    fun verify(name: String, value: Boolean) { check(value) {name}; checks++; println("PASS $name") }
    val events = mutableListOf<LibraryBrowse.Query>()
    val initial = LibraryBrowse.Query(kind = BaseItemKind.MOVIE, sort = LibraryBrowse.Sort.ADDED)
    val selection = LibraryBrowseSelection(initial) { events += it }
    verify("opening a popup does not change page selection", selection.current == initial && events.isEmpty())
    verify("selection returns changed", selection.select(selection.current.copy(genre = "Drama")))
    verify("selection notifies immediately before returning", events.single().genre == "Drama" && events.last() == selection.current)
    verify("same selection does not notify twice", !selection.select(selection.current) && events.size == 1)
    selection.select(selection.current.copy(state = LibraryBrowse.State.WATCHING))
    selection.select(selection.current.copy(resolution = 2160, year = 2024, region = "France"))
    verify("sequential choices keep previous groups", events.last().genre == "Drama" && events.last().state == LibraryBrowse.State.WATCHING && events.last().resolution == 2160)
    verify("initial query remains immutable", initial.genre == null && initial.state == LibraryBrowse.State.ALL)
    selection.selectSort(LibraryBrowse.Sort.NAME)
    verify("name sorting takes effect immediately", events.last().sort == LibraryBrowse.Sort.NAME && events.last().ascending)
    selection.setAscending(false)
    val beforeSameSort = events.size
    verify("reselecting current sort keeps chosen direction", !selection.selectSort(LibraryBrowse.Sort.NAME) && !selection.current.ascending && events.size == beforeSameSort)
    verify("reset takes effect immediately", selection.resetFilters() && events.last() == selection.current && selection.current.genre == null && selection.current.state == LibraryBrowse.State.ALL)
    verify("reset clears every filter group", selection.current.year == null && selection.current.resolution == null && selection.current.region == null)
    verify("reset preserves category and sorting", selection.current.kind == BaseItemKind.MOVIE && selection.current.sort == LibraryBrowse.Sort.NAME && !selection.current.ascending)
    val beforeReset = events.size
    verify("repeated reset is not another page refresh", !selection.resetFilters() && events.size == beforeReset)
    selection.selectSort(LibraryBrowse.Sort.RATING)
    verify("rating defaults to descending on a new sort", events.last().sort == LibraryBrowse.Sort.RATING && !events.last().ascending)
    verify("direction changes take effect immediately", selection.setAscending(true) && events.last().ascending)
    val beforeDirection = events.size
    verify("repeated direction does not notify again", !selection.setAscending(true) && events.size == beforeDirection)
    val scopedEvents = mutableListOf<LibraryBrowse.Query>()
    val history = LibraryBrowseSelection(initial.copy(kind = null, recent = true, genre = "Action", sort = LibraryBrowse.Sort.WATCHED)) {scopedEvents += it}
    history.resetFilters()
    verify("reset keeps recent-history scope", scopedEvents.single().recent && scopedEvents.single().kind == null && scopedEvents.single().sort == LibraryBrowse.Sort.WATCHED)
    println("$checks immediate library selection checks passed")
}
