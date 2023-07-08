package com.github.bepil.kofuse.services.model

import com.intellij.util.indexing.FileBasedIndex

/**
 * Represents a location of a file with [fileId] at the location [offset]
 * within the file.
 *
 * [fileId] should be based on [FileBasedIndex.getFileId].
 */
data class FileOffsetIndexEntry(val fileId: Int, val offset: Int)
