package com.kamerado.voidpass.auth

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher

object BiometricAuth {
    /** Returns true if the device has Class 3 biometrics enrolled. */
    fun canAuthenticate(activity: FragmentActivity): Int {
        val mgr = BiometricManager.from(activity)
        return mgr.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
    }

    fun authenticate(
        activity: FragmentActivity,
        cipher: Cipher,
        title: String = "Unlock Vault",
        subtitle: String = "Use your fingerprint to decrypt",
        onSuccess: (Cipher) -> Unit,
        onError: (Int, CharSequence) -> Unit,
        onFail: () -> Unit = {}
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val unlockedCipher = result.cryptoObject?.cipher
                if (unlockedCipher != null) onSuccess(unlockedCipher)
                else onError(-1, "No cipher in result")
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onError(errorCode, errString)
            }

            override fun onAuthenticationFailed() {
                onFail()
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Use master password")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setConfirmationRequired(false)
            .build()

        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }
}