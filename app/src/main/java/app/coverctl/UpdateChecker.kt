package app.coverctl

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {
    data class Result(
        val tagName: String,
        val htmlUrl: String,
        val newer: Boolean
    )

    fun check(currentVersionName: String, repo: String = BuildConfig.GITHUB_REPO): Result {
        val url = URL("https://api.github.com/repos/$repo/releases/latest")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "CoverCtl-Android")
        }
        try {
            val code = conn.responseCode
            if (code == 404) {
                return Result(currentVersionName, "https://github.com/$repo/releases", newer = false)
            }
            if (code !in 200..299) {
                error("HTTP $code")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val tag = json.optString("tag_name", "").removePrefix("v").trim()
            val html = json.optString("html_url", "https://github.com/$repo/releases")
            val newer = compareVersions(tag, currentVersionName) > 0
            return Result(tag.ifBlank { currentVersionName }, html, newer)
        } finally {
            conn.disconnect()
        }
    }

    /** Positive if a > b. Supports dotted numeric versions like 1.7.0. */
    fun compareVersions(a: String, b: String): Int {
        val pa = a.trim().removePrefix("v").split('.', '-', '_').mapNotNull { it.toIntOrNull() }
        val pb = b.trim().removePrefix("v").split('.', '-', '_').mapNotNull { it.toIntOrNull() }
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }
}
