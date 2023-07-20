package com.github.bepil.kofuse.services.model

/**
 * Interface for finding functions by their fully qualified return type.
 */
internal interface KofuseIndex {
    /**
     * Returns a [FileIdToOffsets] representing functions that have [key]
     * as the fully qualified return type.
     */
    fun read(key: String): FileIdToOffsets
}

/**
 * Interface for writing functions indexed by their fully qualified return type.
 */
internal interface WriteableKofuseIndex : KofuseIndex {

    /**
     * For [fileId], adds new entries to index based on [data]. The value of [data]
     * is a list of file offsets to function which have [data]'s key as their
     * fully qualified return type.
     */
    fun addIndex(fileId: Int, data: Map<String, List<Int>>)

    /**
     * As [addIndex], but first clears entries that match [fileId] with the keys
     * from [data].
     */
    fun updateIndex(fileId: Int, data: Map<String, List<Int>>)


    /**
     * Clears the index, after this call, a direct subsequent call to [read] will
     * return empty data.
     */
    fun clear()
}


private typealias FileId = Int
private typealias FileOffset = Int

/**
 * Implementation of [WriteableKofuseIndex] that stores the index in memory. The index is therefore not persisted.
 */
internal class MemoryKofuseIndex : WriteableKofuseIndex {

    private val index = mutableMapOf<String, MutableMap<FileId, MutableList<FileOffset>>>()
    private val forwardIndex = mutableMapOf<FileId, Set<String>>()

    override fun read(key: String): FileIdToOffsets = index[key] ?: emptyMap()
    override fun clear() {
        index.clear()
        forwardIndex.clear()
    }

    override fun addIndex(fileId: Int, data: Map<String, List<Int>>) {
        data.forEach { (key, value) ->
            if (!index.contains(key)) {
                index[key] = mutableMapOf()
            }
            val entry = index[key]!!
            if (!entry.contains(fileId)) {
                entry[fileId] = mutableListOf()
            }
            index[key]!![fileId]!!.addAll(value)
        }
        forwardIndex[fileId] = data.keys + forwardIndex[fileId].orEmpty()
    }

    override fun updateIndex(fileId: Int, data: Map<String, List<Int>>) {
        forwardIndex[fileId]?.forEach { key ->
            index[key]?.remove(fileId)
        }
        forwardIndex[fileId] = data.keys
        data.forEach { (key, value) ->
            if (!index.contains(key)) {
                index[key] = mutableMapOf()
            }
            index[key]!![fileId] = value.toMutableList()
        }
    }
}


/**
 * [Map] where the key represents a file id, and value represents an offset in that file.
 */
internal typealias FileIdToOffsets = Map<Int, List<Int>>
