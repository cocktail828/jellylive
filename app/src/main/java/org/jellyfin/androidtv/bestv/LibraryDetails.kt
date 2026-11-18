package org.jellyfin.androidtv.bestv

import org.jellyfin.sdk.model.api.BaseItemPerson
import org.jellyfin.sdk.model.api.PersonKind

/** Detail text and paged rail geometry, in the production 960×540 TV canvas. */
object LibraryDetails {
    const val RAIL_WIDTH=864f
    fun people(values: List<BaseItemPerson>) = values.sortedBy {if(it.type==PersonKind.DIRECTOR)0 else 1}
    fun pageCount(count: Int,size: Int): Int {require(count>=0&&size>0);return (count+size-1)/size}
    fun pageOffset(index: Int,size: Int): Float {require(index>=0&&size>0);return index/size*RAIL_WIDTH}
    fun person(index: Int) = LibraryPageLayout.Box(pageOffset(index,6)+index%6*146f,0f,132f,122f)

    fun episode(index: Int) = LibraryPageLayout.Box(pageOffset(index,4)+2f+index%4*220f,0f,200f,113f)

    /** Fit the complete suffix too, without cutting a UTF-16 surrogate pair. */
    fun ellipsize(text: String,fits: (String)->Boolean): String {
        if(fits(text))return text
        if(!fits("..."))return ""
        var low=0;var high=text.codePointCount(0,text.length)
        while(low<high){
            val mid=(low+high+1)/2
            val candidate=text.substring(0,text.offsetByCodePoints(0,mid)).trimEnd()+"..."
            if(fits(candidate))low=mid else high=mid-1
        }
        return text.substring(0,text.offsetByCodePoints(0,low)).trimEnd()+"..."
    }
}
