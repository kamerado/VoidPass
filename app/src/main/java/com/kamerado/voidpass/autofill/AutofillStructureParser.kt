package com.kamerado.voidpass.autofill

import android.app.assist.AssistStructure
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId

data class ParsedStructure(
    val usernameId: AutofillId? = null,
    val passwordId: AutofillId? = null,
    val appPackage: String? = null,
    val webDomain: String? = null,
)

object AutofillStructureParser {
    fun parse(structure: AssistStructure): ParsedStructure {
        var username: AutofillId? = null
        var password: AutofillId? = null
        var webDomain: String? = null
        val appPackage = structure.activityComponent?.packageName

        for (i in 0 until structure.windowNodeCount) {
            val root = structure.getWindowNodeAt(i).rootViewNode
            walk(root) { node ->
                if (node.webDomain != null) webDomain = node.webDomain

                val hints = node.autofillHints
                if (hints != null) {
                    for (h in hints) {
                        when (h) {
                            View.AUTOFILL_HINT_USERNAME,
                            View.AUTOFILL_HINT_EMAIL_ADDRESS ->
                                if (username == null) username = node.autofillId
                            View.AUTOFILL_HINT_PASSWORD ->
                                if (password == null) password = node.autofillId
                        }
                    }
                } else {
                    // Heuristic fallbacks for apps that don't set hints.
                    val isPassword = (node.inputType and InputType.TYPE_TEXT_VARIATION_PASSWORD) != 0
                    if (isPassword && password == null) password = node.autofillId
                    else if (looksLikeUsername(node) && username == null) username = node.autofillId
                }
            }
        }
        return ParsedStructure(username, password, appPackage, webDomain)
    }

    private fun walk(node: AssistStructure.ViewNode, visit: (AssistStructure.ViewNode) -> Unit) {
        visit(node)
        for (i in 0 until node.childCount) walk(node.getChildAt(i), visit)
    }

    private fun looksLikeUsername(node: AssistStructure.ViewNode): Boolean {
        val id = node.idEntry?.lowercase() ?: ""
        val hint = node.hint?.lowercase() ?: ""
        return listOf("user", "email", "login", "account").any {
            it in id || it in hint
        }
    }
}