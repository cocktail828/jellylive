package org.jellyfin.androidtv.util.sdk

import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.ConcurrentHashMap

/**
 * Modified 2026-11-03: some Jellyfin-compatible servers require the legacy
 * header. Learn that requirement only from their explicit missing-header error.
 * Preserve Authorization and scope the extra header to the same HTTP origin.
 */
class LegacyAuthorizationCompatibility : Interceptor {
    private val legacyOrigins = ConcurrentHashMap<String, Boolean>()

    private fun origin(request: okhttp3.Request) =
        "${request.url.scheme}://${request.url.host}:${request.url.port}"

    // Run per network hop so redirecting to another host cannot forward a copied token.
    val networkHeaders = Interceptor { chain ->
        val request = chain.request()
        val authorization = request.header("Authorization")
        val builder = request.newBuilder().removeHeader("X-Emby-Authorization")
        if (authorization != null && legacyOrigins.containsKey(origin(request))) {
            builder.header("X-Emby-Authorization", authorization)
        }
        chain.proceed(builder.build())
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        // Inspect the actual network request: the SDK may add Authorization in
        // a downstream interceptor after this application interceptor runs.
        if (response.code != 400 || response.request.header("Authorization") == null
            || response.request.body?.isOneShot() == true
            || legacyOrigins.containsKey(origin(response.request))
            || !response.peekBody(1024).string().contains("X-Emby-Authorization is missing")) {
            return response
        }
        legacyOrigins[origin(response.request)] = true
        response.close()
        return chain.proceed(request)
    }
}
