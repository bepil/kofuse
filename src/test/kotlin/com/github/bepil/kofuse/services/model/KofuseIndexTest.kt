package com.github.bepil.kofuse.services.model

import org.junit.Assert.assertEquals
import org.junit.Test

class KofuseIndexTest {
    @Test
    fun testKofuseIndex() {
        // GIVEN
        val sut = MemoryKofuseIndex()

        val fileId = 0
        val dataA = "a" to listOf(0, 1)
        val dataB = "b" to listOf(2, 3)
        val dataC = "c" to listOf(4, 5)

        // WHEN adding data to the index...
        val newIndex = sut.withUpdates { addIndex(fileId, mapOf(dataA)) }

        // THEN it can be retrieved.
        assertEquals(mapOf(fileId to dataA.second), newIndex.read("a"))

        // WHEN adding more data to the index...
        val newIndex2 = newIndex.withUpdates {
            addIndex(fileId, mapOf(dataB))
        }

        // THEN all data can be retrieved.
        assertEquals(mapOf(fileId to dataA.second), newIndex2.read(dataA.first))
        assertEquals(mapOf(fileId to dataB.second), newIndex2.read(dataB.first))

        // WHEN updating the index for a fileId...
        val newIndex3 = newIndex2.withUpdates {
            updateIndex(fileId, mapOf(dataC))
        }

        // THEN requesting data for that fileId only returns the new data.
        assertEquals(emptyMap<Int, List<Int>>(), newIndex3.read(dataA.first))
        assertEquals(emptyMap<Int, List<Int>>(), newIndex3.read(dataB.first))
        assertEquals(mapOf(fileId to dataC.second), newIndex3.read(dataC.first))
    }

    @Test
    fun testAddingToIndexWithSameKey() {
        // GIVEN
        val sut = MemoryKofuseIndex()

        val fileIdFirst = 0
        val fileIdSecond = 1
        val dataAFirst = "a" to listOf(0, 1)
        val dataASecond = "a" to listOf(2, 3)

        // WHEN adding data from different files with the same key...
        val newIndex = sut.withUpdates {
            addIndex(fileIdFirst, mapOf(dataAFirst))
            addIndex(fileIdSecond, mapOf(dataASecond))
        }

        // THEN results form both files can be retrieved.
        assertEquals(
            mapOf(fileIdFirst to dataAFirst.second, fileIdSecond to dataASecond.second),
            newIndex.read("a")
        )
    }
}
