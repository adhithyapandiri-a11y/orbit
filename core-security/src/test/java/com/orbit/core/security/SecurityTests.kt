package com.orbit.core.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

class SecurityTests {

    private fun createTestCryptoManager(): CryptoManager {
        val keyBytes = ByteArray(32).apply { SecureRandom().nextBytes(this) }
        val secretKey = SecretKeySpec(keyBytes, "AES")
        return CryptoManager(fallbackKey = secretKey)
    }

    @Test
    fun cryptoManager_encryptAndDecrypt_returnsOriginalText() {
        val crypto = createTestCryptoManager()
        val originalText = "Orbit private OS - 100% local-first"

        val encrypted = crypto.encryptString(originalText)
        assertThat(encrypted).isNotEqualTo(originalText)

        val decrypted = crypto.decryptString(encrypted)
        assertThat(decrypted).isEqualTo(originalText)
    }

    @Test
    fun cryptoManager_differentIvEachTime() {
        val crypto = createTestCryptoManager()
        val text = "Identical message"

        val enc1 = crypto.encryptString(text)
        val enc2 = crypto.encryptString(text)
        assertThat(enc1).isNotEqualTo(enc2)

        assertThat(crypto.decryptString(enc1)).isEqualTo(text)
        assertThat(crypto.decryptString(enc2)).isEqualTo(text)
    }

    @Test
    fun cryptoManager_defaultConstructor_inJvm_encryptsAndDecryptsSuccessfully() {
        val crypto = CryptoManager()
        val text = "Private meeting note content"
        val enc = crypto.encryptString(text)
        assertThat(crypto.decryptString(enc)).isEqualTo(text)
    }

    @Test(expected = Exception::class)
    fun cryptoManager_tamperedCiphertext_failsDecryption() {
        val crypto = createTestCryptoManager()
        val encrypted = crypto.encrypt("Sensitive payload".toByteArray(Charsets.UTF_8))
        // Tamper with last byte
        encrypted[encrypted.size - 1] = (encrypted[encrypted.size - 1].toInt() xor 0xFF).toByte()

        crypto.decrypt(encrypted)
    }

    @Test
    fun checksumVerifier_calculatesAndVerifiesCorrectly() {
        val data = "Orbit verified data stream"
        val hash = ChecksumVerifier.calculateSha256(data)

        assertThat(hash).isNotEmpty()
        assertThat(hash.length).isEqualTo(64)
        assertThat(ChecksumVerifier.verifySha256(data, hash)).isTrue()
        assertThat(ChecksumVerifier.verifySha256(data, hash.uppercase())).isTrue()
        assertThat(ChecksumVerifier.verifySha256("Corrupted data", hash)).isFalse()
    }

    @Test
    fun inputSanitizer_validatesUrlsCorrectly() {
        assertThat(InputSanitizer.isValidUrl("https://youtube.com/watch?v=dQw4w9WgXcQ")).isTrue()
        assertThat(InputSanitizer.isValidUrl("http://example.com/video")).isTrue()
        assertThat(InputSanitizer.isValidUrl("https://sub.domain.co.uk/path?q=1&b=2")).isTrue()

        // Malicious or invalid URLs
        assertThat(InputSanitizer.isValidUrl("javascript:alert(1)")).isFalse()
        assertThat(InputSanitizer.isValidUrl("file:///data/data/com.orbit/databases")).isFalse()
        assertThat(InputSanitizer.isValidUrl("content://media/external/images")).isFalse()
        assertThat(InputSanitizer.isValidUrl("intent://host#Intent;")).isFalse()
        assertThat(InputSanitizer.isValidUrl("not a url")).isFalse()
        assertThat(InputSanitizer.isValidUrl(null)).isFalse()
        assertThat(InputSanitizer.isValidUrl("")).isFalse()
    }

    @Test
    fun inputSanitizer_sanitizesTextAndTags() {
        val dirty = "Hello\u0000\u0007World\nKeep newline\tKeep tab"
        val clean = InputSanitizer.sanitizeText(dirty)
        assertThat(clean).doesNotContain("\u0000")
        assertThat(clean).contains("\nKeep newline\tKeep tab")

        val tags = listOf("Tag 1!", "TAG 1", "programming_tips", "a".repeat(100))
        val sanitizedTags = InputSanitizer.sanitizeTags(tags)
        assertThat(sanitizedTags).contains("tag1")
        assertThat(sanitizedTags).contains("programming_tips")
        // Check max tag length constraint
        assertThat(sanitizedTags.all { it.length <= 50 }).isTrue()
    }
}
