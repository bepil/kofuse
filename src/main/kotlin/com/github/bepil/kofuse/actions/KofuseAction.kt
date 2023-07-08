package com.github.bepil.kofuse.actions

import com.intellij.ide.actions.SearchEverywhereBaseAction
import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * [com.intellij.openapi.actionSystem.AnAction] that allows the user to search for a function by its type
 * signature.
 */
class KofuseAction : SearchEverywhereBaseAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val tabID = KofuseContributor::class.java.simpleName
        showInSearchEverywherePopup(tabID, event, true, true)
    }
}
