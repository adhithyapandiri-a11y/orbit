package com.orbit.feature.videoinbox.parser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class YouTubeUrlParserTest {

    @Test
    fun parse_standardWatchUrl_extractsVideoIdAndCanonicalUrl() {
        val input = "Check out this talk: https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=10s"
        val parsed = YouTubeUrlParser.parse(input)

        assertThat(parsed).isNotNull()
        assertThat(parsed?.videoId).isEqualTo("dQw4w9WgXcQ")
        assertThat(parsed?.canonicalUrl).isEqualTo("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertThat(parsed?.suggestedTitle).isEqualTo("Check out this talk")
        assertThat(parsed?.isShort).isFalse()
        assertThat(parsed?.isPlaylist).isFalse()
    }

    @Test
    fun parse_shortenedYoutuBeUrl_extractsVideoId() {
        val input = "https://youtu.be/dQw4w9WgXcQ"
        val parsed = YouTubeUrlParser.parse(input)

        assertThat(parsed).isNotNull()
        assertThat(parsed?.videoId).isEqualTo("dQw4w9WgXcQ")
        assertThat(parsed?.canonicalUrl).isEqualTo("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertThat(parsed?.isShort).isFalse()
        assertThat(parsed?.isPlaylist).isFalse()
    }

    @Test
    fun parse_youtubeShortsUrl_marksAsShort() {
        val input = "https://www.youtube.com/shorts/dQw4w9WgXcQ"
        val parsed = YouTubeUrlParser.parse(input)

        assertThat(parsed).isNotNull()
        assertThat(parsed?.videoId).isEqualTo("dQw4w9WgXcQ")
        assertThat(parsed?.canonicalUrl).isEqualTo("https://www.youtube.com/shorts/dQw4w9WgXcQ")
        assertThat(parsed?.isShort).isTrue()
        assertThat(parsed?.isPlaylist).isFalse()
    }

    @Test
    fun parse_youtubePlaylistUrl_marksAsPlaylist() {
        val input = "Kotlin Multiplatform Course https://www.youtube.com/playlist?list=PLbpi6ZahtOH6Blw3RGYpWkSByi_T7Rygb"
        val parsed = YouTubeUrlParser.parse(input)

        assertThat(parsed).isNotNull()
        assertThat(parsed?.videoId).isEqualTo("PLbpi6ZahtOH6Blw3RGYpWkSByi_T7Rygb")
        assertThat(parsed?.canonicalUrl).isEqualTo("https://www.youtube.com/playlist?list=PLbpi6ZahtOH6Blw3RGYpWkSByi_T7Rygb")
        assertThat(parsed?.suggestedTitle).isEqualTo("Kotlin Multiplatform Course")
        assertThat(parsed?.isPlaylist).isTrue()
        assertThat(parsed?.isShort).isFalse()
    }

    @Test
    fun parse_genericHttpUrl_extractsFallback() {
        val input = "https://example.com/videos/system-design-intro"
        val parsed = YouTubeUrlParser.parse(input)

        assertThat(parsed).isNotNull()
        assertThat(parsed?.canonicalUrl).isEqualTo("https://example.com/videos/system-design-intro")
        assertThat(parsed?.suggestedTitle).isEqualTo("System design intro")
        assertThat(parsed?.isPlaylist).isFalse()
    }

    @Test
    fun parse_emptyOrInvalidText_returnsNull() {
        assertThat(YouTubeUrlParser.parse("")).isNull()
        assertThat(YouTubeUrlParser.parse("   ")).isNull()
        assertThat(YouTubeUrlParser.parse("just some random text without urls")).isNull()
    }
}
