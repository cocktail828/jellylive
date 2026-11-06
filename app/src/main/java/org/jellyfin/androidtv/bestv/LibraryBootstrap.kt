/* Optional owner-authorized library defaults. GPL-2.0. */
package org.jellyfin.androidtv.bestv

/** No account values belong in source; configured builds provide a private asset. */
internal class LibraryBootstrap(val address: String, val username: String, val password: String) {
    fun matchesAddress(value: String): Boolean = normalize(value) == normalize(address)
    fun matches(value: String, user: String): Boolean = matchesAddress(value) && user.trim() == username
    fun mayConnect(connected: Boolean, savedAddress: String, savedUsername: String, consumed: Boolean = false): Boolean =
        !consumed && !connected && (savedAddress.isBlank() || matchesAddress(savedAddress)) &&
            (savedUsername.isBlank() || savedUsername == username)

    companion object {
        fun normalize(value: String): String {
            val trimmed = value.trim().trimEnd('/')
            return if (trimmed.isBlank() || trimmed.contains("://")) trimmed else "http://$trimmed"
        }
    }
}
