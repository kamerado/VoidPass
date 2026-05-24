package com.kamerado.voidpass.autofill


import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.service.autofill.Dataset
import android.service.autofill.FillResponse
import android.view.WindowManager
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import com.kamerado.voidpass.db.VaultDatabase
import com.kamerado.voidpass.ui.UnlockScreen
import com.kamerado.voidpass.ui.UnlockMode
import com.kamerado.voidpass.ui.UnlockViewModel
import com.kamerado.voidpass.ui.theme.VaultTheme
import androidx.lifecycle.lifecycleScope
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

        // Watch for unlock — when it happens, query the DB and return results.
        lifecycleScope.launch {
            unlockViewModel.uiState.collectLatest { state ->
                if (state.mode == UnlockMode.UNLOCKED) {
                    val vaultKey = unlockViewModel.vaultKey
                    if (vaultKey != null) {
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
            // Unlocked successfully but no matching entries — tell the system
            // we have nothing to fill. It will dismiss the autofill UI.
            // TODO: implement gen random password and save,
            //  Also remember to save domain/appPackage as well.

            setResult(Activity.RESULT_CANCELED)
            finish()
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

    companion object {
        // Keys for the intent extras VaultAutofillService passes to this activity.
        const val EXTRA_USERNAME_ID  = "extra_username_id"
        const val EXTRA_PASSWORD_ID  = "extra_password_id"
        const val EXTRA_APP_PACKAGE  = "extra_app_package"
        const val EXTRA_WEB_DOMAIN   = "extra_web_domain"
    }
}