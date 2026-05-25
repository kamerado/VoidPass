package com.kamerado.voidpass.autofill


import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.autofill.Dataset
import android.service.autofill.FillResponse
import android.view.WindowManager
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import android.app.AlertDialog
import android.content.SharedPreferences
import androidx.fragment.app.FragmentActivity
import com.kamerado.voidpass.db.VaultDatabase
import com.kamerado.voidpass.ui.UnlockScreen
import com.kamerado.voidpass.ui.UnlockMode
import com.kamerado.voidpass.ui.UnlockViewModel
import com.kamerado.voidpass.ui.theme.VaultTheme
import com.kamerado.voidpass.crypto.PasswordGenerator
import androidx.lifecycle.lifecycleScope
import com.kamerado.voidpass.R
import com.kamerado.voidpass.db.PasswordEntry
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class UnlockForAutofillActivity : FragmentActivity() {

    private val unlockViewModel: UnlockViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Same security flag as MainActivity — no screenshots of credentials.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )

        // Pull the autofill request data that VaultAutofillService packed
        // into the intent when it launched this activity.
        val usernameId = intent.getParcelableExtra<AutofillId>(EXTRA_USERNAME_ID)
        val passwordId = intent.getParcelableExtra<AutofillId>(EXTRA_PASSWORD_ID)
        val appPackage = intent.getStringExtra(EXTRA_APP_PACKAGE) ?: ""
        val webDomain  = intent.getStringExtra(EXTRA_WEB_DOMAIN) ?: ""
        val saveUsername = intent.getStringExtra(EXTRA_SAVE_USERNAME)
        val savePassword = intent.getStringExtra(EXTRA_SAVE_PASSWORD)



        // Watch for unlock — when it happens, query the DB and return results.
        lifecycleScope.launch {
            unlockViewModel.uiState.collectLatest { state ->
                if (state.mode == UnlockMode.UNLOCKED) {
                    val vaultKey = unlockViewModel.vaultKey
                    // In the unlock success observer:
                    if (saveUsername != null && savePassword != null && vaultKey != null) {
                        // Save mode — just insert and finish, no fill response needed
                        saveCredentials(vaultKey, saveUsername, savePassword, appPackage, webDomain)
                    } else if (vaultKey != null) {
                        // Fill mode — normal deliverCredentials flow
                        deliverCredentials(vaultKey, usernameId, passwordId, appPackage, webDomain)
                    } else {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    }
                }
            }
        }

        setContent {
            VaultTheme {
                UnlockScreen(
                    viewModel  = unlockViewModel,
                    onUnlocked = { /* handled by the flow collector above */ },
                )
            }
        }
    }

    // TODO: email
    private fun deliverCredentials(
        vaultKey:   ByteArray,
        usernameId: AutofillId?,
        passwordId: AutofillId?,
        appPackage: String,
        webDomain:  String,
    ) {
        // Query the now-unlocked vault for matching entries.
        val db      = VaultDatabase.open(this, vaultKey)
        val matches = if (webDomain.isNotBlank())
            db.findByDomainOrPackage(webDomain, appPackage)
        else
            db.findByDomainOrPackage(appPackage, appPackage)

        if (matches.isEmpty()) {
            // Unlocked successfully but no matching entries.
            // Will prompt you if you would like to generate an entry.
            // TODO: email
            showNoMatchDialog(
            vaultKey   = vaultKey,
                usernameId = usernameId,
                passwordId = passwordId,
                appPackage = appPackage,
                webDomain  = webDomain,
            )
            return
        }

        // Build a real FillResponse with the matched credentials.
        val responseBuilder = FillResponse.Builder()

        for (entry in matches) {
            val presentation = RemoteViews(packageName, com.kamerado.voidpass.R.layout.autofill_item).apply {
                setTextViewText(com.kamerado.voidpass.R.id.title, entry.title)
                setTextViewText(com.kamerado.voidpass.R.id.subtitle, entry.username)
            }

            val datasetBuilder = Dataset.Builder()
            usernameId?.let {
                datasetBuilder.setValue(it, AutofillValue.forText(entry.username), presentation)
            }
            passwordId?.let {
                datasetBuilder.setValue(it, AutofillValue.forText(entry.password), presentation)
            }
            responseBuilder.addDataset(datasetBuilder.build())
        }

        // Pack the FillResponse into the result intent.
        // EXTRA_AUTHENTICATION_RESULT is the key the autofill system looks for.
        val replyIntent = Intent().apply {
            putExtra(
                android.view.autofill.AutofillManager.EXTRA_AUTHENTICATION_RESULT,
                responseBuilder.build()
            )
        }
        setResult(Activity.RESULT_OK, replyIntent)
        finish()
    }

    // TODO: email
    private fun saveCredentials(
        vaultKey:   ByteArray,
        username:   String,
        password:   String,
        appPackage: String,
        webDomain:  String,
    ) {
        val db = VaultDatabase.open(this, vaultKey)
        db.insert(PasswordEntry(
            title       = webDomain.ifBlank { appPackage },
            username    = username,
            password    = password,
            url         = webDomain.ifBlank { null },
            packageName = appPackage.ifBlank { null },
        ))
        // No fill response needed — just confirm success and exit
        setResult(Activity.RESULT_OK)
        finish()
    }

    companion object {
        // Keys for the intent extras VaultAutofillService passes to this activity.
        // TODO: add email - also change the db schema, and add checks for this

        const val EXTRA_USERNAME_ID  = "extra_username_id"
        const val EXTRA_PASSWORD_ID  = "extra_password_id"
        const val EXTRA_APP_PACKAGE  = "extra_app_package"
        const val EXTRA_WEB_DOMAIN   = "extra_web_domain"
        const val EXTRA_SAVE_USERNAME = "extra_save_username"
        const val EXTRA_SAVE_PASSWORD = "extra_save_password"
    }

    private fun generatePassword() {

    }

    private fun showNoMatchDialog(
        vaultKey:   ByteArray,
        usernameId: AutofillId?,
        passwordId: AutofillId?,
        appPackage: String,
        webDomain:  String,
    ) {
        AlertDialog.Builder(this)
            .setTitle("No credentials found")
            .setMessage("No saved entry for ${webDomain.ifBlank { appPackage }}.\nGenerate and save one?")
            .setPositiveButton("Generate & Save") { _, _ ->
                generateAndDeliver(vaultKey, usernameId, passwordId, appPackage, webDomain)
            }
            .setNegativeButton("Cancel") { _, _ ->
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
            .setOnCancelListener {
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
            .show()
    }

    // TODO: email
    private fun generateAndDeliver(
        vaultKey:   ByteArray,
        usernameId: AutofillId?,
        passwordId: AutofillId?,
        appPackage: String,
        webDomain:  String,
    ) {
        val prefs    = getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)
        val generator = PasswordGenerator(this)
        val generated = generator.generate()

        val defaultUsername = prefs.getString("default_username", "") ?: ""

        val entry = PasswordEntry(
            title = webDomain.ifBlank { appPackage },
            username = defaultUsername,
            password = generated,
            url = webDomain.ifBlank { null },
            packageName = appPackage.ifBlank { null },
        )

        val db = VaultDatabase.open(this, vaultKey)
        db.insert(entry)

        // Now deliver the newly created credentials back to the system
        val responseBuilder = FillResponse.Builder()
        val presentation = RemoteViews(packageName, R.layout.autofill_item).apply {
            setTextViewText(R.id.title, entry.title)
            setTextViewText(R.id.subtitle, entry.username)
        }

        val dataset = Dataset.Builder()
        usernameId?.let {
            val p = RemoteViews(packageName, R.layout.autofill_item).apply {
                setTextViewText(R.id.title, entry.title)
                setTextViewText(R.id.subtitle, entry.username)
            }
            dataset.setValue(it, AutofillValue.forText(entry.username), p)
        }
        passwordId?.let {
            val p = RemoteViews(packageName, R.layout.autofill_item).apply {
                setTextViewText(R.id.title, entry.title)
                setTextViewText(R.id.subtitle, entry.username)
            }
            dataset.setValue(it, AutofillValue.forText(entry.password), p)
        }

        responseBuilder.addDataset(dataset.build())

        val replyIntent = Intent().apply {
            putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, responseBuilder.build())
        }
        setResult(Activity.RESULT_OK, replyIntent)
        finish()
    }
}