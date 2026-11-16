package org.jellyfin.androidtv.bestv

/** Native TV geometry: 3840 × 2160 artwork mapped to a 960 × 540 logical canvas. */
object TvMediaBrowserLayout {
    const val CONTENT_X=200f
    const val CONTENT_WIDTH=728f
    data class Box(val x: Float,val y: Float,val width: Float,val height: Float)
    fun columns(large: Boolean)=if(large)4 else 5
    fun imageHeight(large: Boolean)=if(large)258f else 204f
    fun top(large: Boolean)=if(large)188f else 138f
    fun poster(index: Int,large: Boolean): Box {
        val width=if(large)172f else 136f
        return Box(index%columns(large)*(if(large)184f else 147f),index/columns(large)*(if(large)352f else 272f),width,imageHeight(large)+if(large)78f else 52f)
    }
    fun appendLimit(shown: Int,total: Int,focused: Int,large: Boolean): Int {
        val columns=columns(large)
        return when {shown==0->minOf(total,columns*3);focused>=shown-columns*2->minOf(total,shown+columns*2);else->shown}
    }
    /** count is the recovery action sentinel; otherwise the result is a card index. */
    fun down(index: Int,count: Int,large: Boolean,retry: Boolean): Int =
        if(index/columns(large)<(count-1)/columns(large))minOf(index+columns(large),count-1) else if(retry)count else index
    fun contentHeight(count: Int,large: Boolean,retry: Boolean=false): Float {
        val end=if(count>0)poster(count-1,large).let {it.y+it.height} else 28f
        return end+if(retry)76f else 6f
    }
}
