package com.kamerado.voidpass.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object KeystoreWrapper {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "vault_wrapping_key"
    private const val GCM_TAG_BITS = 128
    private const val GCM_IV_BYTES = 12

    /**
     * Creates (or recreates) the wrapping key. Call this exactly once,
     * the first time the user enables biometric unlock.
     *
     * Properties:
     *  - 256-bit AES, GCM, no padding
     *  - User authentication REQUIRED for every use (`-1` validity = per-op)
     *  - Strong biometrics only (no class-2 face unlock etc.)
     *  - Invalidated if biometrics are added/removed (security hygiene)
     *  - StrongBox-backed when available (Pixel 6+)
     */
    fun createWrappingKey() {
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(
                0, // 0 seconds, require auth every operation.
                KeyProperties.AUTH_BIOMETRIC_STRONG
            )
            .setInvalidatedByBiometricEnrollment(true)
            .setRandomizedEncryptionRequired(true)

        val spec = try {
            builder.setIsStrongBoxBacked(true).build()
        } catch (_: Exception) {
            builder.setIsStrongBoxBacked(false).build()
        }

        val keyGen = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        try {
            keyGen.init(spec)
            keyGen.generateKey()
        } catch (e: Exception) {
            // StrongBox can fail on devices that claim support but are flaky.
            // Retry without StrongBox.
            keyGen.init(builder.setIsStrongBoxBacked(false).build())
            keyGen.generateKey()
        }
    }

    fun deleteWrappingKey() {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (ks.containsAlias(KEY_ALIAS)) ks.deleteEntry(KEY_ALIAS)
    }

    fun hasWrappingKey(): Boolean {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return ks.containsAlias(KEY_ALIAS)
    }

    private fun getKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return (ks.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    /** Returns a Cipher in ENCRYPT mode, ready for { BiometricPrompt]. */
    fun encryptCipher(): Cipher {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getKey())
        return cipher
    }

    /** Returns a Cipher in DECRYPT mode, ready for { BiometricPrompt ]. */
    fun decryptCipher(iv: ByteArray): Cipher {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher
    }

    /** After { BiometricPrompt] returns the unlocked cipher, finalize the op. */
    data class Wrapped(val iv: ByteArray, val ciphertext: ByteArray)

    fun wrap(cipher: Cipher, plaintext: ByteArray): Wrapped {
        val ct = cipher.doFinal(plaintext)
        return Wrapped(iv = cipher.iv, ciphertext = ct)
    }

    fun unwrap(cipher: Cipher, ciphertext: ByteArray): ByteArray =
        cipher.doFinal(ciphertext)
}