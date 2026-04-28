package com.kamerado.voidpass.db

import android.content.Context
import com.kamerado.voidpass.crypto.CryptoManager
import java.io.File

/**
 * Handles persistence of the vault's metadata files (salt, biometric wrapped key).
 *
 * These files live in the app's private internal storage:
 *   /data/data/com.kamerado.voidpass/files/
 *
 * That directory:
 *   - is owned by this app's UID; other apps cannot read it
 *   - is excluded from backup (allowBackup="false" in manifest)
 *   - is wiped when the user uninstalls or clears app data
 *
 * The salt itself is NOT secret — it can be public without compromising
 * security (its job is just to make the same password derive different
 * keys on different devices). We store it in plaintext.
 *
 * The vault database file (vault.db) is managed separately by SQLCipher
 * and lives in /data/data/<package>/databases/.
 */
class VaultStorage(private val context: Context) {

    private val saltFile: File
        get() = File(context.filesDir, SALT_FILENAME)

    private val wrappedKeyFile: File
        get() = File(context.filesDir, WRAPPED_KEY_FILENAME)

    // ── Salt ──────────────────────────────────────────────────────────────────

    /** True if a salt has been previously generated and saved. */
    fun saltExists(): Boolean = saltFile.exists()

    /** Loads the existing salt. Throws if it doesn't exist — check first. */
    fun loadSalt(): ByteArray {
        check(saltFile.exists()) { "Salt file does not exist; call generateAndSaveSalt() first" }
        return saltFile.readBytes()
    }

    /** Generates a fresh random salt and persists it. Returns the salt. */
    fun generateAndSaveSalt(): ByteArray {
        val salt = CryptoManager.newSalt()
        saltFile.writeBytes(salt)
        return salt
    }

    // ── Wrapped key (biometric unlock) ────────────────────────────────────────
    //
    // After biometric setup, we encrypt the vault key with a Keystore-backed
    // wrapping key. The result (IV + ciphertext) is stored here. On unlock,
    // we re-derive the wrapping key (gated by biometric) and decrypt this
    // blob to recover the vault key without re-running Argon2id.
    //
    // File layout: 4 bytes IV length (big-endian int) || IV bytes || ciphertext

    fun wrappedKeyExists(): Boolean = wrappedKeyFile.exists()

    fun saveWrappedKey(iv: ByteArray, ciphertext: ByteArray) {
        wrappedKeyFile.outputStream().use { out ->
            // Write IV length as 4-byte big-endian int
            out.write(byteArrayOf(
                (iv.size shr 24).toByte(),
                (iv.size shr 16).toByte(),
                (iv.size shr 8).toByte(),
                iv.size.toByte(),
            ))
            out.write(iv)
            out.write(ciphertext)
        }
    }

    fun loadWrappedKey(): Pair<ByteArray, ByteArray>? {
        if (!wrappedKeyFile.exists()) return null
        val bytes = wrappedKeyFile.readBytes()
        if (bytes.size < 4) return null
        val ivLen = ((bytes[0].toInt() and 0xFF) shl 24) or
                ((bytes[1].toInt() and 0xFF) shl 16) or
                ((bytes[2].toInt() and 0xFF) shl 8) or
                (bytes[3].toInt() and 0xFF)
        if (bytes.size < 4 + ivLen) return null
        val iv = bytes.copyOfRange(4, 4 + ivLen)
        val ct = bytes.copyOfRange(4 + ivLen, bytes.size)
        return iv to ct
    }

    fun deleteWrappedKey() {
        wrappedKeyFile.delete()
    }

    // ── Full reset ────────────────────────────────────────────────────────────

    /** Nuke everything. Used by a "reset vault" feature (not yet built). */
    fun deleteAll() {
        saltFile.delete()
        wrappedKeyFile.delete()
        File(context.filesDir.parent, "databases/$DB_FILENAME").delete()
    }

    fun vaultDbExists(): Boolean {
        return File(context.filesDir.parent, "databases/$DB_FILENAME").exists()
    }

    companion object {
        private const val SALT_FILENAME        = "vault.salt"
        private const val WRAPPED_KEY_FILENAME = "vault.wkey"
        private const val DB_FILENAME          = "vault.db"
    }
}