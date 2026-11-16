package org.jellyfin.androidtv.bestv
fun main(){
 val a=CctvAlbum("VIDA1","节目","特别节目")
 val first=CctvEpisode("a1234567890123456789012345678901","春节特别节目",2)
 val next=CctvEpisode("b1234567890123456789012345678901","端午特别节目",7)
 val item=CctvPlayback.item(a,first)
 check(item.id==CctvPlayback.item(a,first).id)
 check(item.id!=CctvPlayback.item(a,next).id)
 check(item.parentIndexNumber==null&&item.indexNumber==null)
 check(CctvPlayback.episode(item.id,listOf(first,next))==first)
 check(CctvPlayback.next(first,listOf(first,next))==next)
 check(CctvPlayback.next(next,listOf(first,next))==null)
 println("CCTV shared playback identity/order PASS")
}
