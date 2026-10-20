package com.orbit.core.security

import java.security.MessageDigest

object ChecksumVerifier {

    fun calculateSha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return hash.joinToString("") { "%02x".format(it) }
    }

    fun calculateSha256(text: String): String {
        return calculateSha256(text.toByteArray(Charsets.UTF_8))
    }

    fun verifySha256(data: ByteArray, expectedHexChecksum: String): Boolean {
        val actualChecksum = calculateSha256(data)
        return MessageDigest.isEqual(
            actualChecksum.toByteArray(Charsets.UTF_8),
            expectedHexChecksum.lowercase().toByteArray(Charsets.UTF_8)
        )
    }

    fun verifySha256(text: String, expectedHexChecksum: String): Boolean {
        return verifySha256(text.toByteArray(Charsets.UTF_8), expectedHexChecksum)
    }
}
