package com.github.bepil.kofuse.services.model

import arrow.core.getOrNone

/**
 * Interface for finding functions by their fully qualified return type.
 */
internal interface KofuseIndex {
    /**
     * Returns a [FileIdToOffsets] representing functions that have [key]
     * as the fully qualified return type.
     */
    fun read(key: String): FileIdToOffsets

    /**
     * Returns a new immutable index based on `this`, but changed according to
     * [block].
     */
    fun withUpdates(block: WriteableKofuseIndex.() -> Unit): KofuseIndex
}

/**
 * Interface for writing functions indexed by their fully qualified return type.
 */
internal interface WriteableKofuseIndex {
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
}


private typealias FileId = Int
private typealias FileOffset = Int

/**
 * Implementation of [WriteableKofuseIndex] that stores the index in memory. The index is therefore not persisted.
 */
internal class MemoryKofuseIndex : KofuseIndex {

    private val index = mutableMapOf<String, Map<FileId, List<FileOffset>>>()
    private val forwardIndex = mutableMapOf<FileId, Set<String>>()

    override fun read(key: String): FileIdToOffsets = index[key] ?: emptyMap()

    override fun withUpdates(block: WriteableKofuseIndex.() -> Unit): KofuseIndex {
        val copy = MemoryKofuseIndex()
        copy.index.putAll(index)
        copy.forwardIndex.putAll(forwardIndex)
        val writeableIndex = object : WriteableKofuseIndex {
            override fun addIndex(fileId: Int, data: Map<String, List<Int>>) {
                data.forEach { (key, offsets) ->
                    val newMap = copy.index.getOrNone(key).fold(
                        ifEmpty = {
                            mapOf(fileId to offsets)
                        },
                        ifSome = {
                            buildMap {
                                putAll(it)
                                put(fileId, offsets)
                            }
                        },
                    )
                    copy.index[key] = newMap
                }
                copy.forwardIndex[fileId] = data.keys + copy.forwardIndex[fileId].orEmpty()
            }

            override fun updateIndex(fileId: Int, data: Map<String, List<Int>>) {
                copy.forwardIndex[fileId]?.let { existingKeys ->
                    val newKeys = data.keys
                    val keysToRemove = existingKeys - newKeys
                    keysToRemove.forEach {
                        copy.index[it] = copy.index[it]!!.filter { it.key != fileId }
                    }
                }

                data.forEach { (key, offsets) ->
                    copy.index[key] = copy.index.getOrNone(key).fold(
                        ifEmpty = {
                            mapOf(fileId to offsets)
                        },
                        ifSome = {
                            buildMap {
                                putAll(it.filter { it.key != fileId })
                                put(fileId, offsets)
                            }
                        },
                    )
                }

                copy.forwardIndex[fileId] = data.keys
            }

        }
        writeableIndex.block()
        return copy
    }
}


/**
 * [Map] where the key represents a file id, and value represents an offset in that file.
 */
internal typealias FileIdToOffsets = Map<Int, List<Int>>
