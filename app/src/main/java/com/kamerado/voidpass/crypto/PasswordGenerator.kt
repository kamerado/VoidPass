package com.kamerado.voidpass.crypto

import android.app.Application
import android.content.Context
import com.kamerado.voidpass.autofill.UnlockForAutofillActivity
import java.security.SecureRandom

// TODO: probably change prefs to -> app: Application
class PasswordGenerator(private val context: UnlockForAutofillActivity) {
    private val prefs = context.application
        .getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)

    private val rng = SecureRandom()

    // Character sets
    private val lowercase = "abcdefghijklmnopqrstuvwxyz"
    private val uppercase = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private val digits    = "0123456789"
    private val symbols   = "!@#\\$%^&*()-_=+[]{}|;:,.<>?"

    // Ambiguous characters people commonly misread — excluded when
    // readability matters (e.g. a password the user might type manually)
    private val ambiguous = "0OIl1"

    data class Options(
        val length:          Int     = 12,
        val useLowercase:    Boolean = true,
        val useUppercase:    Boolean = true,
        val useDigits:       Boolean = true,
        val useSymbols:      Boolean = true,
        val excludeAmbiguous: Boolean = false,
    )

    fun defaultOptions(): Options = Options(
        length = prefs.getInt("default_password_length", 12),
    )

    /**
     * Generates a cryptographically random password.
     *
     * Uses SecureRandom (OS-seeded CSPRNG) — not kotlin.random.Random
     * which is a PRNG and not suitable for security-sensitive generation.
     *
     * Guarantees at least one character from each enabled set,
     * so you never get a password that technically passes length
     * requirements but has no digits or no symbols.
     */
    fun generate(options: Options = defaultOptions()): String {
        // Build the pool of allowed characters
        var pool = ""
        if (options.useLowercase) pool += lowercase
        if (options.useUppercase) pool += uppercase
        if (options.useDigits)    pool += digits
        if (options.useSymbols)   pool += symbols

        if (options.excludeAmbiguous) {
            pool = pool.filter { it.toString() !in ambiguous }
        }

        require(pool.isNotEmpty()) { "At least one character set must be enabled" }
        require(options.length >= 4) { "Length must be at least 4 to guarantee one of each set" }

        // Build a list of characters, guaranteeing at least one from each
        // enabled set. This avoids the (unlikely but possible) case where
        // pure random selection produces a password with no digits, etc.
        val guaranteed = mutableListOf<Char>()
        if (options.useLowercase) guaranteed.add(randomChar(lowercase))
        if (options.useUppercase) guaranteed.add(randomChar(uppercase))
        if (options.useDigits)    guaranteed.add(randomChar(digits))
        if (options.useSymbols)   guaranteed.add(randomChar(symbols))

        // Fill the rest randomly from the full pool
        val remaining = (guaranteed.size until options.length).map { randomChar(pool) }

        // Shuffle so the guaranteed characters aren't always at the start.
        // If you didn't shuffle, the password would always start with
        // lowercase, uppercase, digit, symbol — a predictable pattern
        // that slightly reduces entropy.
        val all = (guaranteed + remaining).toMutableList()
        shuffle(all)

        return all.joinToString("")
    }

    private fun randomChar(pool: String): Char =
        pool[rng.nextInt(pool.length)]

    /**
     * Fisher-Yates shuffle using SecureRandom.
     * Collections.shuffle() accepts a Random parameter, but we want
     * SecureRandom specifically.
     */
    private fun shuffle(list: MutableList<Char>) {
        for (i in list.size - 1 downTo 1) {
            val j = rng.nextInt(i + 1)
            val tmp = list[i]
            list[i] = list[j]
            list[j] = tmp
        }
    }
}