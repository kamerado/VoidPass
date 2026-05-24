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
/*      TODO: This is dead code in the current implementation. Decide whether or not to keep the
         current flow, or have the function above not return.
         This might involve changing program flow inside of UnlockForAutoFillActivity
 */
//        android.util.Log.d("VaultAutoFill", "Finding matching entries...")
//
//        // Vault is open — find matching entries and return them directly.
//        val target  = parsed.webDomain ?: parsed.appPackage ?: ""
//        val matches = db.findByDomainOrPackage(target, parsed.appPackage ?: "")
//
//        android.util.Log.d("VaultAutoFill", "Matches: " + matches.toString())
//
//
//        if (matches.isEmpty()) {
//            // TODO: implement gen random password and save, Also remember to save domain/appPackage as well.
//            android.util.Log.d("VaultAutoFill", "No entries found for this domain or appPackage, prompting auto generate and save.")
//            callback.onSuccess(null)
//            return
//        }
//
//        val responseBuilder = FillResponse.Builder()
//
//        for (entry in matches) {
//            val presentation = RemoteViews(packageName, R.layout.autofill_item).apply {
//                setTextViewText(R.id.title, entry.title)
//                setTextViewText(R.id.subtitle, entry.username)
//            }
//            val dataset = Dataset.Builder()
//
//            parsed.usernameId?.let {
//                val presentation = RemoteViews(packageName, R.layout.autofill_item).apply {
//                    setTextViewText(R.id.title, entry.title)
//                    setTextViewText(R.id.subtitle, entry.username)
//                }
//                dataset.setValue(it, AutofillValue.forText(entry.username), presentation)
//            }
//
//            parsed.passwordId?.let {
//                // Fresh RemoteViews instance — not the same object as above
//                val presentation = RemoteViews(packageName, R.layout.autofill_item).apply {
//                    setTextViewText(R.id.title, entry.title)
//                    setTextViewText(R.id.subtitle, entry.username)
//                }
//                dataset.setValue(it, AutofillValue.forText(entry.password), presentation)
//            }
//
//            responseBuilder.addDataset(dataset.build())
//        }
//
//        callback.onSuccess(responseBuilder.build())
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        // TODO: implement save when the add-entry screen is built.
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
}