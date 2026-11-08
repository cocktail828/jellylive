package org.jellyfin.androidtv.bestv
import org.jellyfin.sdk.model.api.*
import java.util.UUID
fun main(){
    var checks=0
    fun checkCase(name: String,ok: Boolean){check(ok){name};checks++;println("PASS $name")}
    val series=UUID.randomUUID();val other=UUID.randomUUID()
    fun episode(season: Int?,number: Int?,show: UUID?=series,kind: BaseItemKind=BaseItemKind.EPISODE)=BaseItemDto(id=UUID.randomUUID(),type=kind,seriesId=show,parentIndexNumber=season,indexNumber=number)
    val first=episode(1,1);val second=episode(1,2);val cross=episode(2,1);val special=episode(0,1);val alien=episode(1,3,other)
    val items=listOf(cross,special,alien,second,first,episode(1,null),episode(null,4))
    checkCase("API response order does not determine playback",EpisodeSequence.next(first,items)==second)
    checkCase("next regular season can follow season end",EpisodeSequence.next(second,items)==cross)
    checkCase("last regular episode has no next",EpisodeSequence.next(cross,items)==null)
    checkCase("specials do not enter automatic sequence",EpisodeSequence.next(special,items)==null)
    checkCase("missing season number does not guess playback order",EpisodeSequence.next(episode(null,1),items)==null)
    checkCase("different show is never selected",EpisodeSequence.next(second,listOf(alien))==null)
    checkCase("movies never start episode countdown",EpisodeSequence.next(episode(1,1,kind=BaseItemKind.MOVIE),items)==null)
    checkCase("duplicates cannot replay current episode",EpisodeSequence.next(first,listOf(first,first,second,second))==second)
    checkCase("current item need not be present in a paged response",EpisodeSequence.next(first,listOf(second))==second)
    checkCase("short duration always uses hours",PlaybackLayout.time(980000)=="00:16:20")
    checkCase("long duration retains hours",PlaybackLayout.time(3661000)=="01:01:01")
    checkCase("unknown negative duration never renders negative",PlaybackLayout.time(-1)=="00:00:00")
    checkCase("selected option has no role prefix",PlaybackLayout.value("音轨","原声")=="原声")
    checkCase("disabled subtitles retain function name",PlaybackLayout.value("字幕","关闭")=="字幕")
    checkCase("unknown option retains function name",PlaybackLayout.value("音轨",null)=="音轨")
    checkCase("both players share one compact bottom bar",PlaybackLayout.BAR_Y+PlaybackLayout.BAR_HEIGHT==508f&&PlaybackLayout.BAR_HEIGHT==72f)
    checkCase("progress reaches both bar insets",PlaybackLayout.PROGRESS_X*2+PlaybackLayout.PROGRESS_WIDTH==PlaybackLayout.BAR_WIDTH)
    checkCase("three right action areas do not overlap",(0..1).all {PlaybackLayout.actionX(it)+PlaybackLayout.ACTION_WIDTH+PlaybackLayout.ACTION_GAP==PlaybackLayout.actionX(it+1)}&&PlaybackLayout.actionX(2)+PlaybackLayout.ACTION_WIDTH<=PlaybackLayout.BAR_WIDTH)
    println("$checks episode order and playback design checks passed")
}
