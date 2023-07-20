package apackage

fun freeFunctionNoneToInt() = 1

fun freeFunctionOptionalToInt(optional: String?) = 1

fun freeFunctionNoneToOptionalInt(): Int? = null

fun freeFunctionNoneToList(): List<*> = emptyList<Any>()
fun freeFunctionStringToString(string: String) = string

class SomeClass {

    fun methodNoneToChar() = ' '

    companion object {
        fun companionFunctionNoneToInt() = 1
    }
}

fun SomeClass.extensionFunctionNoneToDouble() = 1.0

fun SomeClass.extentionFunctionNoneToSomeClass() = this
