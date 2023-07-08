package com.github.bepil.kofuse.services.model

import com.intellij.openapi.vfs.VirtualFile

/**
 * Indicates the state of the index, it is either:
 * - [Indexing], in which case progress is available in [Indexing.fileOrDir], or
 * - [Indexed], in which case the index is available in [Indexed.index].
 */
internal sealed class FunctionIndexState {
    class Indexing(val fileOrDir: VirtualFile) : FunctionIndexState()

    class Indexed(val index: WriteableKofuseIndex) : FunctionIndexState()
}
