package org.jellyfin.androidtv.util.sdk

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Modified 2026-11-03: narrow compatibility adapter for identified fnmedia servers. */
class FnMediaCompatibility : Interceptor {
    companion object {
        private val origins = ConcurrentHashMap<String, Boolean>()
        private const val MAX_JSON = 8L * 1024 * 1024
        private val dashedUuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
        private val mediaIdQueries = setOf(
            "parentid", "userid", "itemid", "ids", "ancestorids", "albumids", "artistids",
            "personids", "genreids", "studioids", "mediasourceid", "playlistid", "startitemid", "excludeitemids"
        )
        private val canonicalQueries = (listOf(
            "ParentId", "UserId", "ItemId", "Ids", "AncestorIds", "AlbumIds", "ArtistIds",
            "PersonIds", "GenreIds", "StudioIds", "MediaSourceId", "PlaylistId", "StartItemId", "ExcludeItemIds",
            "Recursive", "Limit", "StartIndex", "Fields", "SortBy", "SortOrder", "IncludeItemTypes", "ExcludeItemTypes",
            "EnableUserData", "EnableImages", "EnableTotalRecordCount", "ImageTypeLimit", "EnableImageTypes", "SearchTerm",
            "Static", "Container", "StreamOptions", "EnableAudioVbrEncoding"
        )).associateBy { it.lowercase() }
        private fun origin(url: okhttp3.HttpUrl) = "${url.scheme}://${url.host}:${url.port}"

        fun rememberServer(address: String, name: String) {
            if (!name.contains("fnmedia", true) && !name.contains("飞牛影视")) return
            address.toHttpUrlOrNull()?.let { origins[origin(it)] = true }
        }

        private fun validItemId(value: String): Boolean = try {
            val canonical = if (value.matches(Regex("[0-9a-fA-F]{32}"))) {
                "${value.substring(0, 8)}-${value.substring(8, 12)}-${value.substring(12, 16)}-${value.substring(16, 20)}-${value.substring(20)}"
            } else value
            UUID.fromString(canonical)
            true
        } catch (_: IllegalArgumentException) { false }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val initial = chain.request()
        val known = origins.containsKey(origin(initial.url))
        var request = initial
        if (known) {
            // The SDK uses canonical dashed UUIDs, while fnmedia's parent/item
            // filters use compact UUIDs. Change only complete UUID values.
            val url = initial.url.newBuilder()
            initial.url.pathSegments.forEachIndexed { index, segment ->
                if (dashedUuid.matches(segment)) url.setPathSegment(index, segment.replace("-", ""))
            }
            initial.url.queryParameterNames.filter { it.lowercase() in canonicalQueries }.forEach { name ->
                val values = initial.url.queryParameterValues(name)
                val canonical = canonicalQueries.getValue(name.lowercase())
                val compact = if (name.lowercase() !in mediaIdQueries) values else values.map { value -> value?.split(',')?.joinToString(",") { part ->
                    if (dashedUuid.matches(part)) part.replace("-", "") else part
                } }
                if (compact != values || canonical != name) {
                    url.removeAllQueryParameters(name)
                    compact.forEach { url.addQueryParameter(canonical, it) }
                }
            }
            request = initial.newBuilder().url(url.build()).build()
        }
        // fnmedia exposes a static byte-range route without a container suffix.
        // Keep the SDK's requested static mode; never turn a transcoding request into direct play.
        val static = request.url.queryParameterNames.any { it.equals("Static", true) && request.url.queryParameter(it).equals("true", true) }
        if (known && request.method == "GET" && static && request.url.encodedPath.matches(Regex(".*/Videos/[^/]+/stream\\.[A-Za-z0-9]+", RegexOption.IGNORE_CASE))) {
            request = request.newBuilder().url(request.url.newBuilder().encodedPath(request.url.encodedPath.substringBeforeLast('.')).build()).build()
        }
        var response = chain.proceed(request)
        if (origin(response.request.url) != origin(request.url)) return response
        if (!response.isSuccessful) return response // Preserve authorization and other HTTP failures.
        val path = request.url.encodedPath
        if (path.endsWith("/System/Info/Public", true) && isJson(response)) {
            try {
                val info = JSONObject(response.peekBody(64 * 1024).string())
                rememberServer(request.url.toString(), info.optString("ServerName"))
            } catch (_: Exception) { }
        }
        if (!origins.containsKey(origin(request.url))) return response
        val profileRequest = requestBody(initial)
        if (initial.method == "POST" && path.endsWith("/PlaybackInfo", true) && !isJson(response)) {
            response.close()
            response = chain.proceed(request.newBuilder().get().removeHeader("Content-Type").removeHeader("Content-Length").build())
            if (origin(response.request.url) != origin(request.url)) return response
            if (!response.isSuccessful) return response
        }
        if (!isJson(response) || response.peekBody(MAX_JSON + 1).bytes().size > MAX_JSON) return response
        val body = response.body ?: return response
        val contentType = body.contentType()
        val text = body.string()
        val transformed = try {
            when (text.trimStart().firstOrNull()) {
                '{' -> JSONObject(text).also { normalize(it, profileRequest) }.toString()
                '[' -> JSONArray(text).also { normalize(it, profileRequest) }.toString()
                else -> text
            }
        } catch (_: Exception) { text }
        return response.newBuilder().removeHeader("Content-Length")
            .body(transformed.toResponseBody(contentType)).build()
    }

    private fun isJson(response: Response): Boolean {
        val type = response.body?.contentType()?.subtype.orEmpty()
        if (type.contains("json", true)) return true
        val first = response.peekBody(128).string().trimStart().firstOrNull()
        return first == '{' || first == '['
    }

    private fun requestBody(request: Request): JSONObject? = try {
        val body = request.body
        if (body == null || body.isOneShot() || body.isDuplex() || body.contentLength() > 1024 * 1024) null
        else Buffer().use { buffer -> body.writeTo(buffer); JSONObject(buffer.readUtf8()) }
    } catch (_: Exception) { null }

    private fun normalize(value: Any?, request: JSONObject?) {
        when (value) {
            is JSONArray -> for (i in 0 until value.length()) normalize(value.opt(i), request)
            is JSONObject -> {
                // Observed full movie details use empty strings for absent nullable references.
                // SDK UUID serializers accept null, not an empty UUID; never fabricate an identity.
                for (field in arrayOf("ParentId", "ServerId")) {
                    val reference=value.opt(field)
                    if(reference is String && reference.isBlank())value.put(field,JSONObject.NULL)
                }
                val itemId = value.optString("Id")
                val userData = value.optJSONObject("UserData")
                if (userData != null && validItemId(itemId) && !validItemId(userData.optString("ItemId"))) {
                    userData.put("ItemId", itemId)
                }
                value.optJSONObject("ImageBlurHashes")?.let { hashes ->
                    val tags = value.optJSONObject("ImageTags")
                    for (kind in hashes.keys().asSequence().toList()) {
                        val hash = hashes.opt(kind)
                        if (hash is String) {
                            val tag = tags?.opt(kind) as? String
                            if (!tag.isNullOrBlank()) hashes.put(kind, JSONObject().put(tag, hash))
                            else hashes.remove(kind) // Optional hint without an image identity.
                        } else if (hash !is JSONObject) hashes.remove(kind)
                    }
                }
                // These are optional UUID references. fnmedia sometimes sends
                // numeric genre IDs; keep the descriptive Genres text, never
                // invent UUIDs that would point at unrelated server entities.
                for (field in arrayOf("GenreItems", "Studios", "ArtistItems", "AlbumArtists")) {
                    value.optJSONArray(field)?.let { references ->
                        val valid = JSONArray()
                        for (i in 0 until references.length()) {
                            val reference = references.optJSONObject(i)
                            if (reference != null && validItemId(reference.optString("Id"))) valid.put(reference)
                        }
                        if (valid.length() != references.length()) value.put(field, valid)
                    }
                }
                value.optJSONArray("MediaSources")?.let { sources ->
                    for (i in 0 until sources.length()) sources.optJSONObject(i)?.let { normalizeSource(it, request) }
                }
                val keys = value.keys().asSequence().toList()
                for (key in keys) normalize(value.opt(key), request)
            }
        }
    }

    private fun normalizeSource(source: JSONObject, request: JSONObject?) {
        // MediaSource.Type is a source relationship, not the movie/episode kind fnmedia puts here.
        val type = source.optString("Type")
        source.put("Type", when {
            type.equals("Grouping", true) -> "Grouping"
            type.equals("Placeholder", true) -> "Placeholder"
            else -> "Default"
        })
        val file = source.optString("Protocol").equals("File", true) && source.optString("Path").isNotBlank()
        val infinite = source.optBoolean("IsInfiniteStream", false)
        val enabled = request?.optBoolean("EnableDirectPlay", true) ?: true
        val direct = file && !infinite && enabled && supportsSource(source, request?.optJSONObject("DeviceProfile"))
        for (key in arrayOf("IsRemote", "ReadAtNativeFramerate", "IgnoreDts", "IgnoreIndex", "GenPtsInput", "RequiresOpening", "RequiresClosing", "RequiresLooping", "HasSegments")) {
            if (!source.has(key) || source.isNull(key)) source.put(key, false)
        }
        source.put("IsInfiniteStream", infinite)
        source.put("SupportsDirectPlay", direct)
        // No advertised transcoding/remux route has been verified for this service.
        source.put("SupportsDirectStream", false)
        source.put("SupportsTranscoding", false)
        if (!source.has("TranscodingSubProtocol") || source.isNull("TranscodingSubProtocol")) source.put("TranscodingSubProtocol", "http")
    }

    private fun entries(array: JSONArray?): List<JSONObject> = if (array == null) emptyList()
        else (0 until array.length()).mapNotNull { array.optJSONObject(it) }

    private fun includes(csv: String, value: String): Boolean = csv.isBlank() || csv.split(',', '|').any { it.trim().equals(value, true) }

    private fun supportsSource(source: JSONObject, profile: JSONObject?): Boolean {
        if (profile == null) return true // Browsing DTO: the subsequent playback request supplies the actual profile.
        val streams = entries(source.optJSONArray("MediaStreams"))
        val video = streams.firstOrNull { it.optString("Type").equals("Video", true) }
        val audio = streams.firstOrNull { it.optString("Type").equals("Audio", true) }
        val container = source.optString("Container")
        if (container.isBlank() || video == null) return false
        val bitrate = source.optDouble("Bitrate", source.optDouble("BitRate", 0.0))
        val maxBitrate = profile.optDouble("MaxStreamingBitrate", 0.0)
        if (maxBitrate > 0 && bitrate > maxBitrate) return false
        val direct = entries(profile.optJSONArray("DirectPlayProfiles")).any {
            it.optString("Type").equals("Video", true) && includes(it.optString("Container"), container) &&
                includes(it.optString("VideoCodec"), video.optString("Codec")) &&
                (audio == null || includes(it.optString("AudioCodec"), audio.optString("Codec")))
        }
        if (!direct) return false
        return entries(profile.optJSONArray("CodecProfiles")).all { codec ->
            val stream = when {
                codec.optString("Type").equals("Video", true) -> video
                codec.optString("Type").equals("Audio", true) -> audio
                else -> null
            }
            if (stream == null || !includes(codec.optString("Codec"), stream.optString("Codec"))) true
            else if (!conditions(codec.optJSONArray("ApplyConditions"), stream)) true
            else conditions(codec.optJSONArray("Conditions"), stream)
        }
    }

    private fun conditions(array: JSONArray?, stream: JSONObject): Boolean = entries(array).all { condition ->
        val property = condition.optString("Property")
        val key = when (property) {
            "VideoProfile", "AudioProfile" -> "Profile"
            "VideoLevel" -> "Level"
            "VideoBitDepth" -> "BitDepth"
            "VideoRefFrames" -> "RefFrames"
            "VideoBitrate", "AudioBitrate" -> "BitRate"
            "AudioChannels" -> "Channels"
            else -> property
        }
        if (!stream.has(key) || stream.isNull(key)) !condition.optBoolean("IsRequired", false)
        else {
            val actual = stream.optString(key)
            val expected = condition.optString("Value")
            when (condition.optString("Condition")) {
                "Equals" -> actual.equals(expected, true)
                "NotEquals" -> !actual.equals(expected, true)
                "EqualsAny" -> includes(expected, actual)
                "LessThanEqual" -> actual.toDoubleOrNull()?.let { a -> expected.toDoubleOrNull()?.let { a <= it } } ?: false
                "GreaterThanEqual" -> actual.toDoubleOrNull()?.let { a -> expected.toDoubleOrNull()?.let { a >= it } } ?: false
                else -> !condition.optBoolean("IsRequired", false)
            }
        }
    }
}
