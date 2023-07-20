package com.github.bepil.kofuse.services

import com.github.bepil.kofuse.KofuseBundle
import com.github.bepil.kofuse.services.model.FileOffsetIndexEntry
import com.github.bepil.kofuse.services.model.FunctionIndexState
import com.github.bepil.kofuse.services.model.KofuseIndex
import com.github.bepil.kofuse.services.model.MemoryKofuseIndex
import com.github.bepil.kofuse.services.model.ProgressResult
import com.github.bepil.kofuse.services.model.SearchResult
import com.github.bepil.kofuse.services.model.WriteableKofuseIndex
import com.github.bepil.kofuse.services.model.mapResult
import com.github.bepil.kofuse.util.NonDispatchingDispatcher
import com.github.bepil.kofuse.util.changes
import com.github.bepil.kofuse.util.disposing
import com.github.bepil.kofuse.util.indexableFiles
import com.github.bepil.kofuse.util.mapEntriesIndexed
import com.github.bepil.kofuse.util.returnTypeFqName
import com.github.bepil.kofuse.util.startWithNull
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.TaskInfo
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.ManagingFS
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.indexing.FileBasedIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.idea.util.isKotlinFileType
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.nj2k.types.typeFqName
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.references.fe10.KtFe10SimpleNameReference

/**
 * A project service that provides functions indexed by their fully qualified return type in
 * [functionIndex].
 */
internal interface FunctionIndexService {
    /**
     * Rebuilds the index anew.
     */
    fun rebuild()

    val functionIndex: Flow<FunctionIndexState>
}

/**
 * Abstract base class for [FunctionIndexService]. Implementations will only have to override [coroutineScope] for a
 * working implementation.
 */
internal abstract class FunctionIndexServiceBase(private val project: Project) : FunctionIndexService, Disposable {
    protected abstract val coroutineScope: CoroutineScope
    private val rebuildFlow = MutableStateFlow(MemoryKofuseIndex())

    override val functionIndex: Flow<FunctionIndexState> by lazy {
        createFunctionIndex(project, rebuildFlow)
            .shareIn(coroutineScope, SharingStarted.Eagerly, 1).also {
                coroutineScope.displayIndexingStateInUi(project, it)
            }
    }

    override fun rebuild() {
        coroutineScope.launch {
            rebuildFlow.emit(MemoryKofuseIndex())
        }
    }

    override fun dispose() {}
}

/**
 * [FunctionIndexService] for production purposes.
 */
internal class ProductionFunctionIndexService(project: Project) : FunctionIndexServiceBase(project) {
    override val coroutineScope = CoroutineScope(Dispatchers.IO).disposing(this)
}

/**
 * [FunctionIndexService] for test purposes.
 */
internal class TestFunctionIndexService(project: Project) : FunctionIndexServiceBase(project) {
    override val coroutineScope = CoroutineScope(NonDispatchingDispatcher()).disposing(this)
}

private fun CoroutineScope.displayIndexingStateInUi(project: Project, flowToDisplay: SharedFlow<FunctionIndexState>) =
    launch {
        flowToDisplay.fold<FunctionIndexState, ProgressWindow?>(null) { acc, value ->
            when (value) {
                is FunctionIndexState.Indexed -> {
                    acc?.processFinish()
                    null
                }

                is FunctionIndexState.Indexing ->
                    (acc ?: BackgroundableProcessIndicator(
                        project,
                        object : TaskInfo {
                            override fun getTitle(): String = KofuseBundle.message("kofuse.progress.title")

                            override fun getCancelText(): String = ""

                            override fun getCancelTooltipText(): String = ""

                            override fun isCancellable(): Boolean = false
                        }
                    ).apply {
                        start()
                    }).apply {
                        text = KofuseBundle.message("kofuse.progress.progressTest", value.fileOrDir)
                    }
            }
        }
    }

@OptIn(ExperimentalCoroutinesApi::class)
private fun createFunctionIndex(project: Project, indexFlow: Flow<WriteableKofuseIndex>): Flow<FunctionIndexState> {
    return combine(
        indexFlow.flatMapLatest {
            buildKotlinIndex(project, it)
        }.buffer(0, onBufferOverflow = BufferOverflow.SUSPEND),
        project.changes.startWithNull()
    ) { functionIndex, changedFile ->
        if (functionIndex is FunctionIndexState.Indexed) {
            changedFile?.virtualFile?.let {
                updateKotlinIndex(project, functionIndex.index, it)
            }
        }
        functionIndex
    }
}

/**
 * Searches for a [KtNamedFunction] based on the fully qualified return type of a function [returnTypeFqn].
 * Functions are only returned if:
 * - their required inputs are in [parameterTypeFqns]. Inputs are the function's
 * parameters and receiver type;
 * - the function is within [scope].
 */
internal fun KofuseIndex.findFunctionsMatching(
    returnTypeFqn: String,
    parameterTypeFqns: List<String>,
    scope: SearchScope?,
    project: Project
): Flow<ProgressResult<SearchResult>> {
    val managingFSInstance = ManagingFS.getInstance()
    val fileIndexEntries = read(returnTypeFqn)
    return fileIndexEntries
        .mapEntriesIndexed { index, entry ->
            entry.value.map {
                ProgressResult(
                    FileOffsetIndexEntry(entry.key, it),
                    index.toDouble() / fileIndexEntries.size
                )
            }
        }.values.flatten().asFlow()
        .map { progressResult ->
            progressResult.mapResult { mapFileIndexEntriesToNamedFunctions(it, project, managingFSInstance) }
        }
        .mapNotNull { progressResult ->
            progressResult.mapToSearchResultIfMatches(scope, parameterTypeFqns)
        }
}

private fun mapFileIndexEntriesToNamedFunctions(
    fileIndexEntry: FileOffsetIndexEntry,
    project: Project,
    managingFSInstance: ManagingFS
): KtNamedFunction? = ReadAction.compute<KtNamedFunction?, Throwable> {
    managingFSInstance.findFileById(fileIndexEntry.fileId)?.toPsiFile(project)?.let {
        PsiTreeUtil.findElementOfClassAtOffset(
            it,
            fileIndexEntry.offset,
            KtNamedFunction::class.java,
            true
        )
    }
}

private inline fun ProgressResult<KtNamedFunction?>.mapToSearchResultIfMatches(
    scope: SearchScope?,
    parameterTypeFqns: List<String>
): ProgressResult<SearchResult>? =
    ReadAction.compute<ProgressResult<SearchResult>, Throwable> {
        if (result != null && scope?.contains(result.containingFile.virtualFile) != false &&
            result.inputs.all { parameterTypeFqns.contains(it) }
        ) {
            ProgressResult(SearchResult(result, 1.0), progress)
        } else {
            null
        }
    }

private inline val KtNamedFunction.inputs: Sequence<String>
    get() = sequence {
        val receiverMethodClass = containingClassOrObject.takeIf {
            it is KtClass
        }?.fqName?.asString()
        receiverMethodClass?.let { yield(it) }
        valueParameterList?.parameters?.forEach { parameter ->
            if (parameter.descriptor?.type?.isMarkedNullable == false) {
                parameter.typeFqName()?.asString()?.let {
                    yield(it)
                }
            }
        }
        val extensionReceiverType =
            ((receiverTypeReference?.typeElement as? KtUserType)?.referenceExpression)?.let { expr ->
                KtFe10SimpleNameReference(expr).resolve()?.kotlinFqName?.asString()
            }
        extensionReceiverType?.let { yield(it) }
    }

private suspend fun buildKotlinIndex(
    project: Project,
    index: WriteableKofuseIndex,
): Flow<FunctionIndexState> = callbackFlow {
    val psiManager = DumbService.getInstance(project).runReadActionInSmartMode<PsiManager> {
        PsiManager.getInstance(project)
    }
    project.indexableFiles().collect { fileOrDir ->
        trySend(FunctionIndexState.Indexing(fileOrDir))
        yield()
        DumbService.getInstance(project).runReadActionInSmartMode {
            if (fileOrDir.isKotlinFileType()) {
                psiManager.findFile(fileOrDir)?.let { psiFile ->
                    val map = mapFile(psiFile)
                    val fileId = FileBasedIndex.getFileId(fileOrDir)
                    index.addIndex(fileId, map)
                }
            }
        }
    }
    trySendBlocking(FunctionIndexState.Indexed(index))
    close() // Close, since we are done. This prevents the caller waiting for more unnecessarily.
    awaitClose { }
}

/**
 * Updates the index.
 *
 * Note: this usually works, but can result in an index that is wrong if the inferred return type for a
 * function changed indirectly. For example:
 * ````
 * fun a() = b()
 *
 * // Some other file:
 * fun b = ""
 *
 * ````
 *
 * If `fun b` is changed to return, for example, an `Int`, then `a`'s return type isn't updated in the index.
 */
private fun updateKotlinIndex(
    project: Project,
    index: WriteableKofuseIndex,
    fileOrDir: VirtualFile
) {
    if (fileOrDir.isKotlinFileType()) {
        DumbService.getInstance(project).runReadActionInSmartMode {
            PsiManager.getInstance(project).findFile(fileOrDir)?.let { psiFile ->
                val fileId = FileBasedIndex.getFileId(fileOrDir)
                val newData = mapFile(psiFile)
                index.updateIndex(fileId, newData)
            }
        }
    }
}

private fun mapFile(psiFile: PsiFile) = buildMap<String, List<Int>> {
    PsiTreeUtil.processElements(psiFile, KtNamedFunction::class.java) { function ->
        if (!function.hasModifier(KtTokens.OVERRIDE_KEYWORD)) { // Only use base function definitions.
            function.returnTypeFqName?.let { returnTypeFqName ->
                val currentValue = get(returnTypeFqName)
                if (currentValue != null) {
                    put(
                        returnTypeFqName,
                        currentValue + listOf(function.startOffset)
                    )
                } else {
                    put(returnTypeFqName, listOf(function.startOffset))
                }
            }
        }
        true
    }
}
