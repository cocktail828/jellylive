package org.jellyfin.androidtv.bestv

import org.jellyfin.sdk.model.api.BaseItemPerson
import org.jellyfin.sdk.model.api.PersonKind
import java.util.UUID

fun main() {
    var checks=0
    fun verify(name: String, value: Boolean){check(value){name};checks++;println("PASS $name")}
    verify("short introduction stays intact",LibraryDetails.ellipsize("短简介"){it.length<=8}=="短简介")
    verify("long introduction ends with three dots",LibraryDetails.ellipsize("这是需要省略的长简介"){it.length<=8}=="这是需要省...")
    verify("ellipsis never splits a supplementary character",LibraryDetails.ellipsize("电影😀电影😀电影"){it.codePointCount(0,it.length)<=7}=="电影😀电...")
    val people=(1..19).map {BaseItemPerson(id=UUID(0,it.toLong()),name="人物$it",type=if(it==9||it==17)PersonKind.DIRECTOR else PersonKind.ACTOR)}
    val ordered=LibraryDetails.people(people)
    verify("directors lead with stable order",ordered.take(2).map {it.id}==listOf(people[8].id,people[16].id))
    verify("full cast survives without truncation",ordered.size==19&&ordered.map {it.id}.toSet()==people.map {it.id}.toSet())
    verify("people remaining order is stable",ordered.drop(2)==people.filter {it.type!=PersonKind.DIRECTOR})
    verify("six people fit one page",(0..5).all {LibraryDetails.person(it).right<=864f&&LibraryDetails.person(it).x>=0})
    verify("seventh person starts next page",LibraryDetails.person(6).x==864f)
    verify("last partial page remains reachable",LibraryDetails.pageOffset(18,6)==2592f&&LibraryDetails.pageCount(19,6)==4)
    verify("empty cast has no pages",LibraryDetails.pageCount(0,6)==0)
    verify("cast fits 4K safe area",LibraryDetails.person(0).height*4+390*4<=2160*.95)
    verify("four episode cards fit one page",(0..3).all {LibraryDetails.episode(it).x>=0f&&LibraryDetails.episode(it).right<=864f})
    verify("episode five starts the second page",LibraryDetails.episode(4).x==866f&&LibraryDetails.pageOffset(4,4)==864f)
    verify("large season remains fully pageable",LibraryDetails.pageCount(101,4)==26&&LibraryDetails.episode(100).right<=26*864f)
    verify("episode images retain dimensions",LibraryDetails.episode(0).width==200f&&LibraryDetails.episode(0).height==113f)
    println("$checks library detail checks passed")
}
