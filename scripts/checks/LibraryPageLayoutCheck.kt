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
    val safe=LibraryPageLayout.Box(48f,27f,864f,486f)
    val entries=LibraryPageLayout.Entry.entries
    verify("home exposes exactly original three category entries",entries.map {it.title}==listOf("电影","剧集","收藏"))
    verify("movie entry uses movie scope",entries[0].query().kind==BaseItemKind.MOVIE)
    verify("series entry uses series scope",entries[1].query().kind==BaseItemKind.SERIES)
    verify("favorites entry defaults to name ordering",entries[2].query().let {it.kind==null&&!it.recent&&it.sort==LibraryBrowse.Sort.NAME&&it.ascending})
    verify("home search retains original design location",LibraryPageLayout.HOME_SEARCH==LibraryPageLayout.Box(754f,69f,158f,38f))
    for(hasResume in listOf(false,true)){
        val cards=entries.indices.map {LibraryPageLayout.homeEntry(it,hasResume)}
        verify("three home entry cards stay inside content ($hasResume)",cards.all {inside(it,safe)})
        verify("home entry cards are flat single row ($hasResume)",cards.map {it.y}.distinct().size==1&&cards.zipWithNext().all {(a,b)->!overlaps(a,b)&&b.x>a.x})
        verify("home title stays above entries ($hasResume)",LibraryPageLayout.homeExploreLabel(hasResume).bottom<=cards.first().y)
        verify("home category sizes remain original ($hasResume)",cards.all {near(it.width,276f)&&near(it.height,130f)})
        if(hasResume)verify("resume stays above explore section",LibraryPageLayout.HOME_RESUME_ROW.bottom<LibraryPageLayout.homeExploreLabel(true).y)
        else verify("empty history retains category position",cards.first().y==LibraryPageLayout.homeEntry(0,true).y)
    }
    val homeCard=LibraryPageLayout.Box(0f,0f,276f,130f)
    verify("home captions and progress stay inside card",listOf(LibraryPageLayout.HOME_CARD_TITLE,LibraryPageLayout.HOME_CARD_SUBTITLE,LibraryPageLayout.HOME_CARD_PROGRESS).all {inside(it,homeCard)})
    verify("home progress stays inset within artwork",LibraryPageLayout.HOME_CARD_PROGRESS.let {it.x>0f&&it.right<276f&&it.bottom<LibraryPageLayout.HOME_CARD_ARTWORK_HEIGHT})
    verify("home captions are separated",!overlaps(LibraryPageLayout.HOME_CARD_TITLE,LibraryPageLayout.HOME_CARD_SUBTITLE))
    val tools=listOf(LibraryPageLayout.GRID_SEARCH,LibraryPageLayout.GRID_FILTER,LibraryPageLayout.GRID_SORT)
    verify("category tools stay in original one toolbar row",tools.map {it.y}.distinct().size==1&&tools.all {near(it.y,68f)&&near(it.height,38f)})
    verify("category tools fit without overlap",tools.all {inside(it,safe)}&&tools.zipWithNext().all {(a,b)->!overlaps(a,b)})
    verify("toolbar has three equal functional areas",tools.map {it.width}.distinct()==listOf(148f)&&tools.zipWithNext().all {(a,b)->near(b.x-a.right,12f)})
    run {
        val columns=LibraryPageLayout.columns()
        val cards=(0 until columns).map {LibraryPageLayout.poster(it)}
        val imageHeight=LibraryPageLayout.posterImageHeight()
        val localCard=LibraryPageLayout.Box(0f,0f,cards[0].width,cards[0].height)
        val title=LibraryPageLayout.posterTitle()
        val metadata=LibraryPageLayout.posterMetadata()
        verify("correct column count",columns==6)
        verify("posters fit safe content",cards.all {inside(it,safe)})
        verify("posters retain one row and separate columns",cards.map {it.y}.distinct().size==1&&cards.zipWithNext().all {(a,b)->!overlaps(a,b)&&b.x>a.x})
        verify("toolbar does not cover posters",cards.all {c->tools.map {LibraryPageLayout.toolbar(it)}.none {overlaps(it,c)}})
        verify("poster artwork keeps 2-to-3 ratio",near(imageHeight/cards[0].width,1.5f))
        verify("title and metadata fit poster",inside(title,localCard)&&inside(metadata,localCard))
        verify("caption overlays artwork while metadata stays below",title.y<imageHeight&&title.bottom<=imageHeight&&!overlaps(title,metadata))
    }
    verify("normal grid retains supplied image width and compact caption area",LibraryPageLayout.poster(0)==LibraryPageLayout.Box(50f,135f,130f,222f))
    run {
        val toolsForSize=tools.map {LibraryPageLayout.toolbar(it)}
        verify("scaled toolbar remains in safe area",toolsForSize.all {inside(it,safe)})
        verify("scaled toolbar keeps equal widths",toolsForSize.map {it.width}.distinct().size==1)
        for(screenHeight in listOf(2160f)){
            val scale=screenHeight/540f
            val cards=(0 until LibraryPageLayout.columns()).map {LibraryPageLayout.poster(it)}
            verify("full poster focus border fits 5 percent overscan at $screenHeight",cards.all {it.x*scale>=screenHeight*16/9*.05f&&it.right*scale<=screenHeight*16/9*.95f&&it.y*scale>=screenHeight*.05f&&it.bottom*scale<=screenHeight*.95f})
        }
    }
    run {
        val cols=LibraryPageLayout.columns()
        val viewport=LibraryPageLayout.scrollViewport()
        val first=LibraryPageLayout.scrollPoster(0)
        val second=LibraryPageLayout.scrollPoster(cols)
        verify("scroll viewport fits safe area",inside(viewport,safe))
        verify("scroll grid keeps original card size",first.width==LibraryPageLayout.poster(0).width&&first.height==LibraryPageLayout.poster(0).height)
        verify("next row appends below complete cards",second.y==first.bottom+16f)
        verify("initial batch is three rows",LibraryPageLayout.appendLimit(0,100,0)==cols*3)
        verify("focus near bottom appends two rows",LibraryPageLayout.appendLimit(cols*3,100,cols)==cols*5)
        verify("focus away from bottom retains loaded batch",LibraryPageLayout.appendLimit(cols*5,100,0)==cols*5)
        verify("last partial row clamps to real total",LibraryPageLayout.appendLimit(cols*3,cols*3+1,cols*2)==cols*3+1)
        verify("end does not add phantom items",LibraryPageLayout.appendLimit(2,2,1)==2)
        verify("empty catalog has no appended cards",LibraryPageLayout.appendLimit(0,0,0)==0)
    }
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
                put("normal",buildJsonObject {
                    put("posters",rects((0 until LibraryPageLayout.columns()).map {LibraryPageLayout.poster(it)}))
                    put("imageHeight",LibraryPageLayout.posterImageHeight());put("title",box(LibraryPageLayout.posterTitle()));put("metadata",box(LibraryPageLayout.posterMetadata()))
                })
            })
        }
        File(args[0]).apply {parentFile?.mkdirs();writeText(Json {prettyPrint=true}.encodeToString(JsonObject.serializer(),dump))}
        println("Production layout export: ${args[0]}")
    }
}
