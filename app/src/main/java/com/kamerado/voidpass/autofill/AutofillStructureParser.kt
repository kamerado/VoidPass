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
                // Extract all signals into named variables first
                val className      = node.className
                val idEntry        = node.idEntry
                val hint           = node.hint
                val inputType      = node.inputType
//                val autofillHints  = node.autofillHints
                val autofillId     = node.autofillId
                val htmlTag        = node.htmlInfo?.tag
                val htmlType       = node.htmlInfo?.attributes?.firstOrNull { it.first == "type" }?.second
                val htmlAutocomplete = node.htmlInfo?.attributes?.firstOrNull { it.first == "autocomplete" }?.second
                val webDomain      = node.webDomain
                val childCount     = node.childCount

                val hints = node.autofillHints
//                Log.d("AutoFillStructureParser", "html = " + htmlType.toString())
                if (hints != null) {
                    for (h in hints) {
                        when (h) {
                            // TODO: change this logic to find email fields separately from username
                            //  DONE  ^^
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
                    Log.d("VaultAutoFill", "Parsing htmlType is: " + htmlType)
                    if (username == null && htmlType == "select-one") username = node.autofillId
                    if (email == null && htmlType == "email") {
                        Log.d("VaultAutoFill", "EMAIL HITTTT htmlType is: " + htmlType)
                        email = node.autofillId
                    }
                    if (password == null && htmlType == "password") password = node.autofillId
                } else {
                    // TODO: check email as well.
                    //  This may never be called because hints could be an empty list.
                    //  Test at some point
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
        // Dump everything — we need anything possible to identify fields
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

    // TODO: email version of this maybe
    private fun looksLikeUsername(node: AssistStructure.ViewNode): Boolean {
        val id = node.idEntry?.lowercase() ?: ""
        val hint = node.hint?.lowercase() ?: ""
        return listOf("user", "email", "login", "account").any {
            it in id || it in hint
        }
    }
}