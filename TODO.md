# TODO

// TODO: docs.
// TODO: tests.
// TODO: generics.
// TODO: incremental indexing after restart.
// TODO: incremental indexing for changed inferred return type alias/hierarchy change is not seen.
// TODO: search options: all input parameters exact match, partial (text) match, including/exclude subtypes, generics.


// DONE: faster full index. Already better. But: use proper index?
// When using normal architecture: return values can be indexed either on return type and all parents
// (parents is difficult since indexing cannot be done based on external files: "The data returned by DataIndexer.map() must depend only on input data passed to the method, and must not depend on any external files. Otherwise, your index will not be correctly updated when the external data changes, and you will have stale data in your index." https://plugins.jetbrains.com/docs/intellij/file-based-indexes.html#accessing-a-file-based-index)
// , or some hash such that hash(me) == hash(me.parent), while for performance, hash(me.parent.meChild) == hash(me) != hash(me.parent.otherChild), which seems unachievable (not symmetrical).
// Other option is some embedding/partitioning/clustering...
// Indexing explanation: https://plugins.jetbrains.com/docs/intellij/stub-indexes.html#implementation.
// Storing to index for Java methods happens in com.intellij.psi.impl.java.stubs.JavaMethodElementType.indexStub.
// ObjectStubSerializer#indexStub extends IElementType is 1:1 with LighterASTNode, and therefore indexing does not
// seem to be extendable with a stub index plugin for a given language.
// Maybe build on top somehow? Using another's index events or the index events?

// A FileBasedIndex does not work since the derived return types require previous indexing to have succeeded, and we cannot depend on other indices.
// https://intellij-support.jetbrains.com/hc/en-us/community/posts/360000506770-How-do-I-build-index-dependent-on-another-index-
// https://intellij-support.jetbrains.com/hc/en-us/community/posts/115000039604-Use-indexes-while-indexing
