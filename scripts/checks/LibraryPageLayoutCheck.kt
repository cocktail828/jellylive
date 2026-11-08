package org.jellyfin.androidtv.bestv

import kotlinx.serialization.json.*
import org.jellyfin.sdk.model.api.BaseItemKind
import java.io.File
import kotlin.math.abs

/** Check actual production geometry and category semantics, not a second layout implementation. */
fun main(args: Array<String>) {
    var count=0
    fun verify(name: String,value: Boolean){check(value){name};count++;println("PASS $name")}
    fun near(a: Float,b: Float)=abs(a-b)<0.05f
    fun inside(child: LibraryPageLayout.Box,parent: LibraryPageLayout.Box)=
        child.x>=parent.x&&child.y>=parent.y&&child.right<=parent.right&&child.bottom<=parent.bottom
    fun overlaps(a: LibraryPageLayout.Box,b: LibraryPageLayout.Box)=
        a.x<b.right&&a.right>b.x&&a.y<b.bottom&&a.bottom>b.y
    val safe=LibraryPageLayout.Box(32f,0f,896f,489f)
    val entries=LibraryPageLayout.Entry.entries
    verify("home exposes exactly original three category entries",entries.map {it.title}==listOf("电影","剧集","最近添加"))
    verify("movie entry uses movie scope",entries[0].query().kind==BaseItemKind.MOVIE)
    verify("series entry uses series scope",entries[1].query().kind==BaseItemKind.SERIES)
    verify("recent entry retains the complete media scope and current default ordering",entries[2].query().let {it.kind==null&&!it.recent&&it.sort==LibraryBrowse.Sort.NAME&&it.ascending})
    verify("home search retains original design location",LibraryPageLayout.HOME_SEARCH==LibraryPageLayout.Box(770f,69f,158f,38f))
    for(hasResume in listOf(false,true)){
        val cards=entries.indices.map {LibraryPageLayout.homeEntry(it,hasResume)}
        verify("three home entry cards stay inside content ($hasResume)",cards.all {inside(it,safe)})
        verify("home entry cards are flat single row ($hasResume)",cards.map {it.y}.distinct().size==1&&cards.zipWithNext().all {(a,b)->!overlaps(a,b)&&b.x>a.x})
        verify("home title stays above entries ($hasResume)",LibraryPageLayout.homeExploreLabel(hasResume).bottom<=cards.first().y)
        verify("home category sizes remain original ($hasResume)",cards.all {near(it.width,287f)&&near(it.height,130f)})
        if(hasResume)verify("resume stays above explore section",LibraryPageLayout.HOME_RESUME_ROW.bottom<LibraryPageLayout.homeExploreLabel(true).y)
        else verify("empty history retains category position",cards.first().y==LibraryPageLayout.homeEntry(0,true).y)
    }
    val homeCard=LibraryPageLayout.Box(0f,0f,287f,130f)
    verify("home captions and progress stay inside card",listOf(LibraryPageLayout.HOME_CARD_TITLE,LibraryPageLayout.HOME_CARD_SUBTITLE,LibraryPageLayout.HOME_CARD_PROGRESS).all {inside(it,homeCard)})
    verify("home progress does not cover subtitle",!overlaps(LibraryPageLayout.HOME_CARD_SUBTITLE,LibraryPageLayout.HOME_CARD_PROGRESS))
    verify("home captions are separated",!overlaps(LibraryPageLayout.HOME_CARD_TITLE,LibraryPageLayout.HOME_CARD_SUBTITLE))
    val tools=listOf(LibraryPageLayout.GRID_SEARCH,LibraryPageLayout.GRID_FILTER,LibraryPageLayout.GRID_SORT)
    verify("category tools stay in original one toolbar row",tools.map {it.y}.distinct().size==1&&tools.all {near(it.y,68f)&&near(it.height,38f)})
    verify("category tools fit without overlap",tools.all {inside(it,safe)}&&tools.zipWithNext().all {(a,b)->!overlaps(a,b)})
    verify("toolbar has three equal functional areas",tools.map {it.width}.distinct()==listOf(148f)&&tools.zipWithNext().all {(a,b)->near(b.x-a.right,12f)})
    for(large in listOf(false,true)){
        val columns=LibraryPageLayout.columns(large)
        val cards=(0 until columns).map {LibraryPageLayout.poster(it,large)}
        val pager=listOf(LibraryPageLayout.pagerPrevious(large),LibraryPageLayout.pagerRange(large),LibraryPageLayout.pagerNext(large))
        val imageHeight=LibraryPageLayout.posterImageHeight(large)
        val localCard=LibraryPageLayout.Box(0f,0f,cards[0].width,cards[0].height)
        val title=LibraryPageLayout.posterTitle(large)
        val metadata=LibraryPageLayout.posterMetadata(large)
        verify("correct column count ($large)",columns==if(large)5 else 6)
        verify("posters fit safe content ($large)",cards.all {inside(it,safe)})
        verify("posters retain one row and separate columns ($large)",cards.map {it.y}.distinct().size==1&&cards.zipWithNext().all {(a,b)->!overlaps(a,b)&&b.x>a.x})
        verify("toolbar does not cover posters ($large)",cards.all {c->tools.none {overlaps(it,c)}})
        verify("posters do not cover pagination ($large)",cards.all {c->pager.none {overlaps(it,c)}})
        verify("poster artwork keeps 2-to-3 ratio ($large)",near(imageHeight/cards[0].width,1.5f))
        verify("title and metadata fit poster ($large)",inside(title,localCard)&&inside(metadata,localCard))
        verify("caption overlays artwork while metadata stays below ($large)",title.y<imageHeight&&title.bottom<=imageHeight&&!overlaps(title,metadata))
        verify("pager stays above footer ($large)",pager.all {inside(it,safe)})
        verify("pager controls are separate ($large)",pager.zipWithNext().all {(a,b)->!overlaps(a,b)})
        verify("page range remains screen centered ($large)",near(pager[1].x+pager[1].width/2,480f))
        verify("previous and next are symmetric ($large)",near(pager[0].x+pager[0].width/2+pager[2].x+pager[2].width/2,960f))
    }
    verify("normal grid retains supplied image width and compact caption area",LibraryPageLayout.poster(0)==LibraryPageLayout.Box(32f,135f,137f,232f))
    println("$count library page layout checks passed")
    if(args.isNotEmpty()){
        fun box(b: LibraryPageLayout.Box)=buildJsonObject {put("x",b.x);put("y",b.y);put("width",b.width);put("height",b.height)}
        fun rects(list: List<LibraryPageLayout.Box>)=buildJsonArray {list.forEach {add(box(it))}}
        val dump=buildJsonObject {
            put("source","Production LibraryPageLayout.kt; geometry simulation, not Android screenshot")
            put("checks",count)
            put("width",960);put("height",540)
            put("home",buildJsonObject {
                put("search",box(LibraryPageLayout.HOME_SEARCH));put("resumeLabel",box(LibraryPageLayout.HOME_RESUME_LABEL));put("resumeRow",box(LibraryPageLayout.HOME_RESUME_ROW))
                put("artworkHeight",LibraryPageLayout.HOME_CARD_ARTWORK_HEIGHT);put("infoTop",LibraryPageLayout.HOME_CARD_INFO_TOP)
                put("title",box(LibraryPageLayout.HOME_CARD_TITLE));put("subtitle",box(LibraryPageLayout.HOME_CARD_SUBTITLE));put("progress",box(LibraryPageLayout.HOME_CARD_PROGRESS))
                put("entries",buildJsonArray {entries.forEach {entry->add(buildJsonObject {put("title",entry.title);put("artwork",entry.artwork);put("kind",entry.kind?.serialName)})}})
                for(resume in listOf(false,true))put(if(resume)"withResume" else "withoutResume",buildJsonObject {
                    put("exploreLabel",box(LibraryPageLayout.homeExploreLabel(resume)));put("entries",rects(entries.indices.map {LibraryPageLayout.homeEntry(it,resume)}))
                })
            })
            put("grid",buildJsonObject {
                put("tools",rects(tools))
                for(large in listOf(false,true))put(if(large)"large" else "normal",buildJsonObject {
                    put("posters",rects((0 until LibraryPageLayout.columns(large)).map {LibraryPageLayout.poster(it,large)}))
                    put("imageHeight",LibraryPageLayout.posterImageHeight(large));put("title",box(LibraryPageLayout.posterTitle(large)));put("metadata",box(LibraryPageLayout.posterMetadata(large)))
                    put("pager",rects(listOf(LibraryPageLayout.pagerPrevious(large),LibraryPageLayout.pagerRange(large),LibraryPageLayout.pagerNext(large))))
                })
            })
        }
        File(args[0]).apply {parentFile?.mkdirs();writeText(Json {prettyPrint=true}.encodeToString(JsonObject.serializer(),dump))}
        println("Production layout export: ${args[0]}")
    }
}
