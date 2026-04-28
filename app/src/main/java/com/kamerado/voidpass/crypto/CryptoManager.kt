package com.kamerado.voidpass.crypto

import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoManager {
    private const val VERSION: Byte = 0x01
    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BITS = 128 // 96-bit IV - required by GCM spec
    private const val SALT_BYTES = 16
    private const val KEY_BYTES = 32 // 256-bit AES

    // Argon2id parameters. These are mid-range — on a Pixel 6+ they take
    // roughly 1 second. Tune for your target devices: lower if older hardware
    // must be supported, higher (especially memory) for stronger resistance.
    // OWASP 2024 minimums: m=19 MiB, t=2, p=1 for Argon2id.
    // We're well above that.
    private const val ARGON2_T_COST = 4 // iterations
    private const val ARGON2_M_COST_KIB = 64 * 1024
    private const val ARGON2_PARALLELISM = 2

    private val rng = SecureRandom()

    /** Generates a fresh cryptographically-random salt. Store this with the vault. */
    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also { rng.nextBytes(it) }

    /** Generates a fresh random AES key (the "vault key"). */
    fun newVaultKey(): ByteArray = ByteArray(KEY_BYTES).also { rng.nextBytes(it) }

    /**
     * Derives a 32-byte key from the master password using Argon2id.
     * The result is the *master key* — used only to wrap the vault key.
     * We never reuse the master key directly to encrypt entries because
     * key rotation (e.g. password change) would require re-encrypting
     * everything. Wrapping a separate vault key keeps rotation cheap.
     */
    fun deriveKeyFromPassword(password: CharArray, salt: ByteArray): ByteArray {
        val argon2 = Argon2Kt()
        val passwordBytes = password.toUtf8Bytes()
        try {
            val result = argon2.hash(
                mode = Argon2Mode.ARGON2_ID,
                password = passwordBytes,
                salt = salt,
                tCostInIterations = ARGON2_T_COST,
                mCostInKibibyte = ARGON2_M_COST_KIB,
                parallelism = ARGON2_PARALLELISM,
                hashLengthInBytes = KEY_BYTES
            )
            return result.rawHashAsByteArray()
        } finally {
            passwordBytes.fill(0)  // best-effort wipe
        }
    }

    fun encrypt(plaintext: ByteArray, key: ByteArray): ByteArray {
        require(key.size == KEY_BYTES)
        val iv = ByteArray(GCM_IV_BYTES).also { rng.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey: SecretKey = SecretKeySpec(key, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ct = cipher.doFinal(plaintext)
        return byteArrayOf(VERSION) + iv + ct
    }

    fun decrypt(blob: ByteArray, key: ByteArray): ByteArray {
        require(key.size == KEY_BYTES)
        require(blob.isNotEmpty() && blob[0] == VERSION) { "unknown blob version" }
        val iv = blob.copyOfRange(1, 1 + GCM_IV_BYTES)
        val ct = blob.copyOfRange(1 + GCM_IV_BYTES, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey: SecretKey = SecretKeySpec(key, "AES")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ct)  // throws AEADBadTagException if tampered
    }

    private fun CharArray.toUtf8Bytes(): ByteArray {
        // Avoids creating an intermediate String (which would sit on the heap
        // until GC, hard to wipe).
        val charBuffer = java.nio.CharBuffer.wrap(this)
        val byteBuffer = Charsets.UTF_8.encode(charBuffer)
        val out = ByteArray(byteBuffer.remaining())
        byteBuffer.get(out)
        return out
    }
}