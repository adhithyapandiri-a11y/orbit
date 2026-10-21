package com.orbit.feature.videoinbox.parser

data class ParsedVideoMetadata(
    val videoId: String,
    val canonicalUrl: String,
    val suggestedTitle: String,
    val isShort: Boolean = false,
    val isPlaylist: Boolean = false
)

object YouTubeUrlParser {

    private val YOUTUBE_WATCH_REGEX = "(?:youtube\\.com/watch\\?.*v=|youtu\\.be/|youtube\\.com/embed/)([a-zA-Z0-9_-]{11})".toRegex()
    private val YOUTUBE_SHORTS_REGEX = "youtube\\.com/shorts/([a-zA-Z0-9_-]{11})".toRegex()
    private val YOUTUBE_PLAYLIST_REGEX = "(?:youtube\\.com/playlist\\?.*list=|[?&]list=)([a-zA-Z0-9_-]+)".toRegex()
    private val GENERIC_URL_REGEX = "(https?://[^\\s]+)".toRegex()

    fun extractUrl(text: String): String? {
        val match = GENERIC_URL_REGEX.find(text)
        return match?.value?.trim()
    }

    fun parse(rawUrlOrText: String): ParsedVideoMetadata? {
        val extractedUrl = extractUrl(rawUrlOrText) ?: rawUrlOrText.trim()
        if (extractedUrl.isBlank()) return null

        val textWithoutUrl = rawUrlOrText.replace(extractedUrl, "").trim()
            .trim('-', ':', '|', ' ', '“', '”', '"')
        val candidateTitle = if (textWithoutUrl.isNotBlank() && textWithoutUrl.length > 2) textWithoutUrl else null

        // 1. YouTube Playlist
        val playlistMatch = YOUTUBE_PLAYLIST_REGEX.find(extractedUrl)
        if (playlistMatch != null && !extractedUrl.contains("watch?v=")) {
            val playlistId = playlistMatch.groupValues[1]
            return ParsedVideoMetadata(
                videoId = playlistId,
                canonicalUrl = "https://www.youtube.com/playlist?list=$playlistId",
                suggestedTitle = candidateTitle ?: "YouTube Playlist ($playlistId)",
                isShort = false,
                isPlaylist = true
            )
        }

        // 2. YouTube Shorts
        val shortsMatch = YOUTUBE_SHORTS_REGEX.find(extractedUrl)
        if (shortsMatch != null) {
            val videoId = shortsMatch.groupValues[1]
            return ParsedVideoMetadata(
                videoId = videoId,
                canonicalUrl = "https://www.youtube.com/shorts/$videoId",
                suggestedTitle = candidateTitle ?: "YouTube Short ($videoId)",
                isShort = true,
                isPlaylist = false
            )
        }

        // 3. YouTube Watch / Embed
        val watchMatch = YOUTUBE_WATCH_REGEX.find(extractedUrl)
        if (watchMatch != null) {
            val videoId = watchMatch.groupValues[1]
            return ParsedVideoMetadata(
                videoId = videoId,
                canonicalUrl = "https://www.youtube.com/watch?v=$videoId",
                suggestedTitle = candidateTitle ?: "YouTube Video ($videoId)",
                isShort = false,
                isPlaylist = playlistMatch != null
            )
        }

        // 4. Generic non-youtube web video link
        if (extractedUrl.startsWith("http://") || extractedUrl.startsWith("https://")) {
            val simpleName = extractedUrl
                .substringAfterLast("/")
                .substringBefore("?")
                .replace("-", " ")
                .replace("_", " ")
                .trim()

            val title = candidateTitle ?: if (simpleName.isNotBlank() && simpleName.length > 3) {
                simpleName.replaceFirstChar { it.uppercase() }
            } else {
                "Saved Video Link"
            }

            return ParsedVideoMetadata(
                videoId = extractedUrl.hashCode().toString(),
                canonicalUrl = extractedUrl,
                suggestedTitle = title,
                isShort = false,
                isPlaylist = false
            )
        }

        return null
    }
}
