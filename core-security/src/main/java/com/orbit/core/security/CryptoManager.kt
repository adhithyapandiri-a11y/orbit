package com.orbit.core.security

import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.util.Base64

class CryptoManager(
    private val keyAlias: String = "orbit_master_key",
    private val fallbackKey: SecretKey? = null // For unit testing on JVM where AndroidKeyStore is not present
) {
    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH_BYTES = 12
        private const val TAG_LENGTH_BITS = 128
    }

    private val secureRandom = SecureRandom()

    private val jvmTestFallbackKey: SecretKey by lazy {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256, secureRandom)
        keyGen.generateKey()
    }

    private fun getSecretKey(): SecretKey {
        if (fallbackKey != null) {
            return fallbackKey
        }
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(keyAlias)) {
                val entry = keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry
                if (entry != null) return entry.secretKey
            }

            // Generate new key in AndroidKeyStore
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            val spec = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()

            keyGenerator.init(spec)
            keyGenerator.generateKey()
        } catch (_: Exception) {
            jvmTestFallbackKey
        }
    }

    fun encrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = ByteArray(IV_LENGTH_BYTES).apply { secureRandom.nextBytes(this) }
        val spec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(), spec)

        val cipherText = cipher.doFinal(data)
        // Format: [12-byte IV][Ciphertext + Auth Tag]
        return iv + cipherText
    }

    fun decrypt(encryptedDataWithIv: ByteArray): ByteArray {
        require(encryptedDataWithIv.size > IV_LENGTH_BYTES) { "Data is too short to contain IV and ciphertext" }
        val iv = encryptedDataWithIv.copyOfRange(0, IV_LENGTH_BYTES)
        val cipherText = encryptedDataWithIv.copyOfRange(IV_LENGTH_BYTES, encryptedDataWithIv.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)

        return cipher.doFinal(cipherText)
    }

    fun encryptString(plainText: String): String {
        val encrypted = encrypt(plainText.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(encrypted)
    }

    fun decryptString(cipherTextBase64: String): String {
        val decoded = Base64.getDecoder().decode(cipherTextBase64)
        val decrypted = decrypt(decoded)
        return String(decrypted, Charsets.UTF_8)
    }
}
