package com.github.bepil.kofuse.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiTreeAnyChangeAbstractAdapter
import com.intellij.util.indexing.FileBasedIndex
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive

/**
 * Returns a [Flow], emitting a [PsiFile] everytime that file is modified.
 */
val Project.changes: Flow<PsiFile>
    get() = callbackFlow {
        val disposable = Disposable {}
        val listener = object : PsiTreeAnyChangeAbstractAdapter() {
            override fun onChange(file: PsiFile?) {
                file?.let { trySend(it) }
            }
        }
        PsiManager.getInstance(this@changes).addPsiTreeChangeListener(listener, disposable)
        awaitClose {
            Disposer.dispose(disposable)
        }
    }.buffer(128)

/**
 * Emits a [Flow] of [VirtualFile]s that can be indexed for this [Project].
 * The [Flow] is closed once all [VirtualFile]s have been emitted.
 */
internal fun Project.indexableFiles(): Flow<VirtualFile> = callbackFlow {
    FileBasedIndex.getInstance().iterateIndexableFiles({ fileOrDir ->
        trySendBlocking(fileOrDir)
        isActive
    }, this@indexableFiles, null)
    close()
    awaitClose { }
}
