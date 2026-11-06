package org.jellyfin.androidtv.bestv

/** Reject stale async filtering/paging results, including A -> B -> cached A selections. */
class LibraryGridUpdates {
    private data class Key(val query: LibraryBrowse.Query,val offset: Int)
    data class Request internal constructor(val query: LibraryBrowse.Query,val offset: Int,internal val generation: Long)
    private var generation=0L
    private var requested: Key?=null
    private var rendered: Key?=null
    val hasResult get()=rendered!=null
    fun begin(query: LibraryBrowse.Query,offset: Int): Request? {
        generation++
        requested=Key(query,offset)
        return if(requested==rendered)null else Request(query,offset,generation)
    }
    fun accept(request: Request,actualOffset: Int): Boolean {
        if(request.generation!=generation||requested!=Key(request.query,request.offset))return false
        rendered=Key(request.query,actualOffset);requested=rendered
        return true
    }
}
