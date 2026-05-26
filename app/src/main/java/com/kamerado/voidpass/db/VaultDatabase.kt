package com.kamerado.voidpass.db

import android.annotation.SuppressLint
import android.content.Context
import android.database.Cursor
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteOpenHelper
import java.util.UUID

// ── Data model ────────────────────────────────────────────────────────────────
// Plain Kotlin data class — no Room annotations needed.

data class PasswordEntry(
    val id:          String = UUID.randomUUID().toString(),
    val title:       String,
    val username:    String,
    val email:       String,
    val password:    String,
    val url:         String?  = null,
    val packageName: String?  = null,
    val notes:       String?  = null,
    val createdAt:   Long     = System.currentTimeMillis(),
    val updatedAt:   Long     = System.currentTimeMillis(),
)

// ── Database helper ───────────────────────────────────────────────────────────

class VaultDatabase private constructor(
    context: Context,
    key:     ByteArray,
) : SQLiteOpenHelper(
    context,
    DB_NAME,
    key,          // SQLCipher takes the key here — encrypts transparently
    null,
    DB_VERSION,
    0, null, null,
    true,
) {

    companion object {
        private const val DB_NAME    = "vault.db"
        private const val DB_VERSION = 1

        @Volatile var instance: VaultDatabase? = null
            private set


        fun open(context: Context, key: ByteArray): VaultDatabase {
            System.loadLibrary("sqlcipher")
            return instance ?: synchronized(this) {
                instance ?: VaultDatabase(context.applicationContext, key)
                    .also { instance = it }
            }
        }

        fun close() {
            instance?.close()
            instance = null
        }
    }

    // ── Schema ────────────────────────────────────────────────────────────────

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE entries (
                id          TEXT    PRIMARY KEY NOT NULL,
                title       TEXT    NOT NULL,
                username    TEXT    NOT NULL,
                email       TEXT    NOT NULL,
                password    TEXT    NOT NULL,
                url         TEXT,
                packageName TEXT,
                notes       TEXT,
                createdAt   INTEGER NOT NULL,
                updatedAt   INTEGER NOT NULL
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Handle schema migrations here when you bump DB_VERSION.
        // For v1 there's nothing to migrate yet.
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    fun getAll(): List<PasswordEntry> {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM entries ORDER BY title ASC", null)
        return cursor.use { it.toEntryList() }
    }

    fun getById(id: String): PasswordEntry? {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM entries WHERE id = ?",
            arrayOf(id)
        )
        return cursor.use { if (it.moveToFirst()) it.toEntry() else null }
    }

    /** Used by autofill — finds entries matching a web domain or app package. */
    fun findByDomainOrPackage(domain: String, packageName: String): List<PasswordEntry> {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM entries WHERE url LIKE ? OR packageName = ?",
            arrayOf("%$domain%", packageName)
        )
        return cursor.use { it.toEntryList() }
    }

    fun insert(entry: PasswordEntry) {
        val db = writableDatabase
        db.execSQL(
            """INSERT INTO entries
           (id, title, username, email, password, url, packageName, notes, createdAt, updatedAt)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            arrayOf<Any?>(    // ← add <Any?>
                entry.id, entry.title, entry.username, entry.email, entry.password,
                entry.url, entry.packageName, entry.notes,
                entry.createdAt, entry.updatedAt,
            )
        )
    }

    fun update(entry: PasswordEntry) {
        val db = writableDatabase
        db.execSQL(
            """UPDATE entries SET
           title = ?, username = ?, email = ?, password = ?, url = ?,
           packageName = ?, notes = ?, updatedAt = ?
           WHERE id = ?""",
            arrayOf<Any?>(    // ← add <Any?>
                entry.title, entry.username, entry.email, entry.password, entry.url,
                entry.packageName, entry.notes,
                System.currentTimeMillis(),
                entry.id,
            )
        )
    }

    fun deleteById(id: String) {
        writableDatabase.execSQL("DELETE FROM entries WHERE id = ?", arrayOf(id))
    }

    // ── Cursor helpers ────────────────────────────────────────────────────────

    private fun Cursor.toEntry() = PasswordEntry(
        id          = getString(getColumnIndexOrThrow("id")),
        title       = getString(getColumnIndexOrThrow("title")),
        username    = getString(getColumnIndexOrThrow("username")),
        email       = getString(getColumnIndexOrThrow("email")),
        password    = getString(getColumnIndexOrThrow("password")),
        url         = getString(getColumnIndexOrThrow("url")),
        packageName = getString(getColumnIndexOrThrow("packageName")),
        notes       = getString(getColumnIndexOrThrow("notes")),
        createdAt   = getLong(getColumnIndexOrThrow("createdAt")),
        updatedAt   = getLong(getColumnIndexOrThrow("updatedAt")),
    )

    private fun Cursor.toEntryList(): List<PasswordEntry> {
        val list = mutableListOf<PasswordEntry>()
        while (moveToNext()) list.add(toEntry())
        return list
    }
}