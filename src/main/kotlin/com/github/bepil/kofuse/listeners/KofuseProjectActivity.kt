package com.github.bepil.kofuse.listeners

import com.github.bepil.kofuse.services.FunctionIndexService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * [ProjectActivity] that ensures that the indexing from [FunctionIndexService]
 * is already done at startup.
 */
internal class KofuseProjectActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        // Retrieve the index so that it will be created at startup.
        project.service<FunctionIndexService>().functionIndex
    }
}
