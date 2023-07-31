package com.github.bepil.kofuse.actions

import com.github.bepil.kofuse.KofuseBundle
import com.github.bepil.kofuse.services.FunctionIndexService
import com.github.bepil.kofuse.services.findFunctionsMatching
import com.github.bepil.kofuse.services.model.FunctionIndexState
import com.github.bepil.kofuse.util.awaitFirst
import com.intellij.codeInsight.navigation.NavigationUtil
import com.intellij.ide.actions.QualifiedNameProviderUtil
import com.intellij.ide.actions.SearchEverywhereClassifier
import com.intellij.ide.actions.SearchEverywherePsiRenderer
import com.intellij.ide.actions.searcheverywhere.AbstractGotoSEContributor
import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.ide.actions.searcheverywhere.PossibleSlowContributor
import com.intellij.ide.actions.searcheverywhere.ScopeChooserAction
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributorFactory
import com.intellij.ide.util.gotoByName.FilteringGotoByModel
import com.intellij.ide.util.gotoByName.GotoSymbolModel2
import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.ProjectAndLibrariesScope
import com.intellij.ui.ContextHelpLabel
import com.intellij.util.Processor
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.JButtonAction
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.VisibleForTesting
import javax.swing.JComponent
import javax.swing.ListCellRenderer

/**
 * [AbstractGotoSEContributor] that plugs into the Goto search popup, adding a tab
 * to search for functions by their type signature.
 */
class KofuseContributor(event: AnActionEvent) :
    AbstractGotoSEContributor(event),
    PossibleSlowContributor {
    private val project = event.getRequiredData(CommonDataKeys.PROJECT)
    private val everythingScope = GlobalSearchScope.everythingScope(project)
    private val projectScope = SearchEverywhereClassifier.EP_Manager.getProjectScope(project)
    private var scopeDescriptor: ScopeDescriptor = ScopeDescriptor(ProjectAndLibrariesScope(project))

    override fun getGroupName(): String = KofuseBundle.message("name")

    override fun getSortWeight(): Int = 1

    // Be almost on top, but just after the calculator search results.
    // Because a colon is required for search results, we assume that results besides calculator results are less
    // important.
    override fun getAdvertisement(): String = KofuseBundle.message("kofuse.advertisement")

    override fun getActions(onChanged: Runnable): List<AnAction> {
        return listOf(object : ScopeChooserAction() {
            val canToggleEverywhere = everythingScope != scopeDescriptor
            override fun onScopeSelected(o: ScopeDescriptor) {
                scopeDescriptor = o
                onChanged.run()
            }

            override fun getSelectedScope(): ScopeDescriptor {
                return scopeDescriptor
            }

            override fun onProjectScopeToggled() {
                isEverywhere = !scopeDescriptor.scopeEquals(everythingScope)
            }

            override fun processScopes(processor: Processor<in ScopeDescriptor>): Boolean {
                return ContainerUtil.process(createScopes(), processor)
            }

            override fun isEverywhere(): Boolean {
                return scopeDescriptor.scopeEquals(everythingScope)
            }

            override fun setEverywhere(everywhere: Boolean) {
                scopeDescriptor = ScopeDescriptor(if (everywhere) everythingScope else projectScope)
                onChanged.run()
            }

            override fun canToggleEverywhere(): Boolean {
                return if (!canToggleEverywhere) false else scopeDescriptor.scopeEquals(everythingScope) ||
                        scopeDescriptor.scopeEquals(projectScope)
            }
        }, ReindexAction(::reindex), HelpAction())
    }

    override fun createModel(project: Project): FilteringGotoByModel<*> = GotoSymbolModel2(project, this)

    override fun processSelectedItem(selected: Any, modifiers: Int, searchText: String): Boolean =
        (selected as? PsiElement?)?.let { NavigationUtil.activateFileWithPsiElement(it, true) } ?: false

    override fun fetchWeightedElements(
        rawPattern: String,
        progressIndicator: ProgressIndicator,
        consumer: Processor<in FoundItemDescriptor<Any>>
    ) {
        val service = project.getService(FunctionIndexService::class.java)
        parse(rawPattern)?.let { parsed ->
            // Since this is not called from the main thread, and we want to show the results after the index has
            // been build without the user having to retype their query, we use runBlocking. Inside we do regularly
            // call `checkCanceled` so we don't do unnecessary blocking processing for too long.
            runBlocking {
                service.functionIndex
                    .onEach {
                        progressIndicator.checkCanceled()
                    }
                    .filterIsInstance<FunctionIndexState.Indexed>()
                    .awaitFirst().let { functionIndex ->
                        functionIndex.index.findFunctionsMatching(
                            parsed.returnType,
                            parsed.parameterTypes,
                            scopeDescriptor.scope,
                            project
                        ).collect {
                            ReadAction.run<Throwable> {
                                consumer.process(FoundItemDescriptor(it.result.function, it.result.score.toInt()))
                            }
                            progressIndicator.fraction = it.progress
                            progressIndicator.checkCanceled()
                        }
                    }
            }
        }
    }

    override fun getElementsRenderer(): ListCellRenderer<Any> {
        return SearchEverywherePsiRenderer(this)
    }

    override fun getItemDescription(element: Any): String? =
        (element as? PsiElement?)?.let { QualifiedNameProviderUtil.getQualifiedName(it) }

    private class HelpAction : CustomComponentAction, AnAction() {
        override fun actionPerformed(e: AnActionEvent) {}

        override fun createCustomComponent(presentation: Presentation, place: String): JComponent =
            ContextHelpLabel.create(KofuseBundle.message("kofuse.help"))
    }

    @VisibleForTesting
    fun reindex() {
        project.getService(FunctionIndexService::class.java).rebuild()
    }

    private class ReindexAction(private val onActionPerformed: () -> Unit) :
        JButtonAction(KofuseBundle.message("kofuse.reindex")) {
        override fun actionPerformed(e: AnActionEvent) {
            onActionPerformed()
        }
    }
}

/**
 * [SearchEverywhereContributorFactory] that uses [KofuseContributor] as the contributor.
 */
class KofuseSecFactory : SearchEverywhereContributorFactory<Any> {
    override fun createContributor(initEvent: AnActionEvent): SearchEverywhereContributor<Any> =
        PSIPresentationBgRendererWrapper(KofuseContributor(initEvent))
}

private data class SearchQuery(val returnType: String, val parameterTypes: List<String>)

private fun parse(input: String): SearchQuery? {
    val splitted = input.replace(Regex("[^\\w,:.]"), "").split(":")
    if (splitted.size == 2) {
        return SearchQuery(splitted[1], splitted[0].split(","))
    }
    return null
}

