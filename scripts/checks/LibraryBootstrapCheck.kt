package org.jellyfin.androidtv.bestv

fun main() {
    val config = LibraryBootstrap("http://nas.invalid:8005", "owner", "example-only")
    check(config.mayConnect(false, "", ""))
    check(config.mayConnect(false, "nas.invalid:8005/", "owner"))
    check(!config.mayConnect(true, "", ""))
    check(!config.mayConnect(false, "https://other.invalid", "owner"))
    check(!config.mayConnect(false, "http://nas.invalid:8005", "other"))
    check(!config.mayConnect(false, "", "", true))
    check(!config.mayConnect(false, "http://nas.invalid:8005", "owner", true))
    check(config.matches("nas.invalid:8005/", "owner"))
    check(!config.matches("http://nas.invalid:8005", "other"))
    check(!config.matches("https://nas.invalid:8005", "owner"))
    check(!config.matches("http://nas.invalid:8096", "owner"))
    check(!config.matches("http://nas.invalid:8005.attacker.invalid", "owner"))
    check(!config.toString().contains("example-only"))
    println("13 library bootstrap policy checks passed")
}
