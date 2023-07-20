package com.github.bepil.kofuse

import com.github.bepil.kofuse.services.model.MemoryKofuseIndex
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
        sut.addIndex(fileId, mapOf(dataA))

        // THEN it can be retrieved.
        assertEquals(mapOf(fileId to dataA.second), sut.read("a"))

        // WHEN adding more data to the index...
        sut.addIndex(fileId, mapOf(dataB))

        // THEN all data can be retrieved.
        assertEquals(mapOf(fileId to dataA.second), sut.read(dataA.first))
        assertEquals(mapOf(fileId to dataB.second), sut.read(dataB.first))

        // WHEN updating the index for a fileId...
        sut.updateIndex(fileId, mapOf(dataC))

        // THEN requesting data for that fileId only returns the new data.
        assertEquals(emptyMap<Int, List<Int>>(), sut.read(dataA.first))
        assertEquals(emptyMap<Int, List<Int>>(), sut.read(dataB.first))
        assertEquals(mapOf(fileId to dataC.second), sut.read(dataC.first))
    }
}
