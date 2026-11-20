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
 check(CctvPlayback.highestQuality(listOf(Triple(720,1280,8_000_000),Triple(1080,1920,6_000_000)))==1)
 check(CctvPlayback.highestQuality(listOf(Triple(1080,1920,6_000_000),Triple(1080,1920,8_000_000)))==1)
 check(CctvPlayback.highestQuality(emptyList())==-1)
 println("CCTV shared playback identity/order and highest-quality policy PASS")
}
