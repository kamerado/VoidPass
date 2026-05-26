package com.kamerado.voidpass.autofill

import android.app.assist.AssistStructure
import android.text.InputType
import android.util.Log
import android.view.View
import android.view.autofill.AutofillId

data class ParsedStructure(
    val usernameId: AutofillId? = null,
    val emailId: AutofillId? = null,
    val passwordId: AutofillId? = null,
    val appPackage: String? = null,
    val webDomain: String? = null,
)

object AutofillStructureParser {
    fun parse(structure: AssistStructure): ParsedStructure {
        var username: AutofillId? = null
        var email:    AutofillId? = null
        var password: AutofillId? = null
        var webDomain: String? = null
        val appPackage = structure.activityComponent?.packageName

        for (i in 0 until structure.windowNodeCount) {
            val root = structure.getWindowNodeAt(i).rootViewNode
            walk(root) { node ->
                if (node.webDomain != null) webDomain = node.webDomain

                val hints = node.autofillHints
                val htmlType = node.htmlInfo
                Log.d("AutoFillStructureParser", "html = " + htmlType.toString())
                if (hints != null) {
                    for (h in hints) {
                        when (h) {
                            // TODO: change this logic to find email fields separately from username
                            //  TEST THIS NOW!
                            View.AUTOFILL_HINT_USERNAME ->
                                if (username == null) username = node.autofillId
                            View.AUTOFILL_HINT_EMAIL_ADDRESS ->
                                if (email == null) email = node.autofillId
                            "current-password",
                            "new-password",
                            View.AUTOFILL_HINT_PASSWORD ->
                                if (password == null) password = node.autofillId
                        }
                    }
                } else {
                    // TODO: check email as well.
                    // Heuristic fallbacks for apps that don't set hints.
                    val isPassword = (node.inputType and InputType.TYPE_TEXT_VARIATION_PASSWORD) != 0
                    if (isPassword && password == null) password = node.autofillId
                    else if (looksLikeUsername(node) && username == null) username = node.autofillId
                }
            }
        }
        return ParsedStructure(username, email, password, appPackage, webDomain)
    }

    private fun walk(node: AssistStructure.ViewNode, visit: (AssistStructure.ViewNode) -> Unit) {
        visit(node)
        for (i in 0 until node.childCount) walk(node.getChildAt(i), visit)
        // Dump everything — no filter
        Log.d("VaultAutofill",
            "Node: class=${node.className} " +
                    "id=${node.idEntry} " +
                    "hint='${node.hint}' " +
                    "inputType=0x${node.inputType.toString(16)} " +
                    "autofillHints=${node.autofillHints?.toList()} " +
                    "autofillId=${node.autofillId} " +
                    "htmlTag=${node.htmlInfo?.tag} " +
                    "htmlType=${node.htmlInfo?.attributes
                        ?.firstOrNull { it.first == "type" }?.second} " +
                    "htmlAutocomplete=${node.htmlInfo?.attributes
                        ?.firstOrNull { it.first == "autocomplete" }?.second} " +
                    "webDomain=${node.webDomain} " +
                    "childCount=${node.childCount}"
        )


    }

    // TODO: email version of this
    private fun looksLikeUsername(node: AssistStructure.ViewNode): Boolean {
        val id = node.idEntry?.lowercase() ?: ""
        val hint = node.hint?.lowercase() ?: ""
        return listOf("user", "email", "login", "account").any {
            it in id || it in hint
        }
    }
}