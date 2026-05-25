package com.kamerado.voidpass.autofill

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.CancellationSignal
import android.service.autofill.*
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import com.kamerado.voidpass.R
import com.kamerado.voidpass.db.VaultDatabase

class VaultAutofillService : AutofillService() {

    override fun onFillRequest(
        request:            FillRequest,
        cancellationSignal: CancellationSignal,
        callback:           FillCallback,
    ) {
        android.util.Log.d("VaultAutofill", "onFillRequest called")
        val structure = request.fillContexts.last().structure
        val parsed    = AutofillStructureParser.parse(structure)

        // Nothing autofillable on this screen — bail early.
        if (parsed.usernameId == null && parsed.passwordId == null) {

            callback.onSuccess(null)
            return
        }

        val db = VaultDatabase.instance  // null if vault is locked

        if (db == null) {
            // Vault is locked — return the "tap to unlock" placeholder.
            android.util.Log.d("VaultAutoFill", "Asking user to unlock vault.")
            callback.onSuccess(buildAuthRequiredResponse(parsed))
            return
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val structure = request.fillContexts.last().structure
        val parsed    = AutofillStructureParser.parse(structure)

        val username = findValueForId(structure, parsed.usernameId)
        val password = findValueForId(structure, parsed.passwordId)

        if (username != null && password != null) {
            val intent = Intent(this, UnlockForAutofillActivity::class.java).apply {
                putExtra(UnlockForAutofillActivity.EXTRA_USERNAME_ID,  parsed.usernameId)
                putExtra(UnlockForAutofillActivity.EXTRA_PASSWORD_ID,  parsed.passwordId)
                putExtra(UnlockForAutofillActivity.EXTRA_APP_PACKAGE,  parsed.appPackage)
                putExtra(UnlockForAutofillActivity.EXTRA_WEB_DOMAIN,   parsed.webDomain)
                putExtra(UnlockForAutofillActivity.EXTRA_SAVE_USERNAME, username)
                putExtra(UnlockForAutofillActivity.EXTRA_SAVE_PASSWORD, password)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }
        callback.onSuccess()
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private fun buildAuthRequiredResponse(parsed: ParsedStructure): FillResponse {
        // Build the Intent that launches the unlock activity.
        val authIntent = Intent(this, UnlockForAutofillActivity::class.java).apply {
            putExtra(UnlockForAutofillActivity.EXTRA_USERNAME_ID, parsed.usernameId)
            putExtra(UnlockForAutofillActivity.EXTRA_PASSWORD_ID, parsed.passwordId)
            putExtra(UnlockForAutofillActivity.EXTRA_APP_PACKAGE, parsed.appPackage)
            putExtra(UnlockForAutofillActivity.EXTRA_WEB_DOMAIN,  parsed.webDomain)
        }

        val pi = PendingIntent.getActivity(
            this,
            0,
            authIntent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE,
        )

        val presentation = RemoteViews(packageName, R.layout.autofill_locked).apply {
            setTextViewText(R.id.title, "Vault locked — tap to unlock")
        }

        return FillResponse.Builder()
            .setAuthentication(
                listOfNotNull(parsed.usernameId, parsed.passwordId).toTypedArray(),
                pi.intentSender,
                presentation,
            )
            .build()
    }

    private fun findValueForId(
        structure: android.app.assist.AssistStructure,
        id: AutofillId?
    ): String? {
        if (id == null) return null
        var result: String? = null
        for (i in 0 until structure.windowNodeCount) {
            findValueInNode(structure.getWindowNodeAt(i).rootViewNode, id) { result = it }
        }
        return result
    }

    private fun findValueInNode(
        node:   android.app.assist.AssistStructure.ViewNode,
        target: AutofillId,
        found:  (String) -> Unit,
    ) {
        if (node.autofillId == target) {
            node.autofillValue?.textValue?.toString()?.let { found(it) }
            return
        }
        for (i in 0 until node.childCount) {
            findValueInNode(node.getChildAt(i), target, found)
        }
    }
}