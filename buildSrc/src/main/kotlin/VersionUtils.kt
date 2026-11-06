import org.gradle.api.Project

/**
 * Get the version name from the current environment or use the fallback.
 * It will look for a environment variable called JELLYFIN_VERSION first.
 * Next it will look for a property called "jellyfin.version" and lastly it will use the fallback.
 * If the version in the environment starts with a "v" prefix it will be removed.
 *
 * Sample output:
 * v2.0.0 -> 2.0.0
 * null -> 0.0.1 (unless different fallback set)
 */
fun Project.getVersionName(fallback: String = "0.0.1") =
	getProperty("jellyfin.version")
		?.removePrefix("v")
		?: fallback

/**
 * Numeric release names have three or four components, optionally prefixed by v.
 * The optional fourth component is an incremental revision, defaulting to zero.
 * Examples: v0.1.1 -> 10100; v0.1.1.1 -> 10101; v0.1.2 -> 10200.
 * Each component is bounded to two digits so adjacent revisions stay ordered.
 */
fun getVersionCode(versionName: String): Int {
    val value = versionName.removePrefix("v")
    require(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:\\.[0-9]+)?").matches(value)) {
        "Version must use three or four numeric components, for example v0.1.1 or v0.1.1.1"
    }
    val parts = value.split('.').map(String::toInt)
    require(parts.all { it in 0..99 }) { "Version components must be between 0 and 99" }
    return parts[0] * 1000000 + parts[1] * 10000 + parts[2] * 100 + parts.getOrElse(3) { 0 }
}
