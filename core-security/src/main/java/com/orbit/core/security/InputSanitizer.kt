package com.orbit.core.security

import java.net.URI

object InputSanitizer {

    private val ALLOWED_SCHEMES = setOf("http", "https")
    private const val MAX_TITLE_LENGTH = 500
    private const val MAX_NOTES_LENGTH = 10000
    private const val MAX_TAG_LENGTH = 50
    private const val MAX_TAGS_COUNT = 30
    private const val MAX_IMPORT_SIZE_BYTES = 50 * 1024 * 1024 // 50MB safety limit

    fun isValidUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return try {
            val trimmed = url.trim()
            val uri = URI(trimmed)
            val scheme = uri.scheme?.lowercase() ?: return false
            if (scheme !in ALLOWED_SCHEMES) return false
            val host = uri.host
            !host.isNullOrBlank() && host.contains(".")
        } catch (_: Exception) {
            false
        }
    }

    fun sanitizeText(input: String?, maxLength: Int = MAX_NOTES_LENGTH): String {
        if (input == null) return ""
        // Strip null bytes and control chars except newline and tab
        val cleaned = input.filter { c ->
            c == '\n' || c == '\r' || c == '\t' || !c.isISOControl()
        }
        return if (cleaned.length > maxLength) cleaned.substring(0, maxLength) else cleaned
    }

    fun sanitizeTitle(input: String?): String {
        return sanitizeText(input, MAX_TITLE_LENGTH).trim()
    }

    fun sanitizeTag(tag: String?): String {
        if (tag == null) return ""
        val cleaned = tag.trim().lowercase().filter { it.isLetterOrDigit() || it == '-' || it == '_' }
        return if (cleaned.length > MAX_TAG_LENGTH) cleaned.substring(0, MAX_TAG_LENGTH) else cleaned
    }

    fun sanitizeTags(tags: List<String>?): List<String> {
        if (tags == null) return emptyList()
        return tags.asSequence()
            .map { sanitizeTag(it) }
            .filter { it.isNotBlank() }
            .distinct()
            .take(MAX_TAGS_COUNT)
            .toList()
    }

    fun isSafeImportSize(sizeBytes: Long): Boolean {
        return sizeBytes in 1..MAX_IMPORT_SIZE_BYTES
    }
}
