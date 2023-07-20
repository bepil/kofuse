package com.github.bepil.kofuse.actions

import app.cash.turbine.test
import com.intellij.ide.actions.searcheverywhere.WeightedSearchEverywhereContributor
import com.intellij.mock.MockProgressIndicator
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@TestDataPath("\$CONTENT_ROOT/src/test/testData")
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Parameterized::class)
class KofuseContributorTest : LightJavaCodeInsightFixtureTestCase() {

    private lateinit var sut: KofuseContributor
    override fun setUp() {
        super.setUp()

        // First create a dummy file,
        // so that com.intellij.psi.impl.file.impl.PsiVFSListener.fileCreated gives a proper event.
        // For this, the parent needs to exist already.
        myFixture.addFileToProject("src/dummy.txt", "")
        myFixture.copyFileToProject("testData.kt", "src/testData.kt")

        sut = createSut()
    }

    @Parameterized.Parameter
    lateinit var testData: TestData

    @Test
    fun test() = kotlinx.coroutines.test.runTest {
        // WHEN a search query is made, THEN the expected result are found.
        sut.assertResultsForSearch(testData.searchQuery, testData.results)

        // WHEN a reindex is done...
        sut.reindex()

        // THEN the expected results are also found.
        sut.assertResultsForSearch(testData.searchQuery, testData.results)
    }

    private fun createSut(): KofuseContributor {
        val dataContext = SimpleDataContext.getProjectContext(project)
        val event = AnActionEvent.createFromDataContext(ActionPlaces.UNKNOWN, null, dataContext)
        return KofuseContributor(event).also {
            Disposer.register(testRootDisposable, it)
        }
    }

    override fun getTestDataPath() = "src/test/testData"

    companion object {
        class TestData(val searchQuery: String, val results: List<String>)

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun provideTestParameters(): Iterable<TestData> = listOf(
            TestData(
                ":kotlin.Int",
                listOf(
                    "apackage.freeFunctionNoneToInt",
                    "apackage.SomeClass.Companion.companionFunctionNoneToInt",
                    "apackage.freeFunctionNoneToOptionalInt",
                    "apackage.freeFunctionOptionalToInt"
                )
            ),
            TestData(
                ":kotlin.collections.List",
                listOf(
                    "apackage.freeFunctionNoneToList",
                )
            ),
            TestData(":kotlin.Char", listOf()),
            TestData("apackage.SomeClass:kotlin.Char", listOf("apackage.SomeClass.methodNoneToChar")),
            TestData(":kotlin.String", listOf()),
            TestData("kotlin.String:kotlin.String", listOf("apackage.freeFunctionStringToString")),
            TestData("apackage.SomeClass:kotlin.Double", listOf("apackage.extensionFunctionNoneToDouble")),
            TestData("apackage.SomeClass:apackage.SomeClass", listOf("apackage.extentionFunctionNoneToSomeClass")),
            TestData("not.matching.search.query.pattern", listOf()),
        )

        private fun <T> WeightedSearchEverywhereContributor<T>.fetchWeightedElements(rawPattern: String) =
            callbackFlow {
                var stopped = false
                fetchWeightedElements(rawPattern, MockProgressIndicator()) {
                    trySend(it)
                    stopped
                }
                awaitClose {
                    stopped = true
                }
            }

        private suspend fun KofuseContributor.assertResultsForSearch(
            searchQuery: String,
            expectedResults: Iterable<String>
        ) {
            val expectedResultsToAwait = expectedResults.toMutableSet()
            fetchWeightedElements(searchQuery).test {
                while (expectedResultsToAwait.isNotEmpty()) {
                    val actualResult = (awaitItem().item as KtNamedFunction).fqName?.asString()
                    assertTrue(
                        "$expectedResultsToAwait does not contain $actualResult for search query $searchQuery",
                        expectedResultsToAwait.contains(actualResult)
                    )
                    expectedResultsToAwait.remove(actualResult)
                }
            }
        }
    }
}
